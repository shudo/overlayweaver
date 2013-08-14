/*
 * Copyright 2006-2007,2009-2010 National Institute of Advanced Industrial Science
 * and Technology (AIST), and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ow.tool.mrouted;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.ipmulticast.Group;
import ow.ipmulticast.IPMulticast;
import ow.util.Timer;

final class OverlayTrafficForwarder implements Runnable {
	private final static Logger logger = Logger.getLogger("mrouted");

	private final ApplicationLevelMulticastRouterConfiguration config;
	private Inet4Address group = null;
	private GroupTable groupTable;
	private DatagramSocket sockForOverlay;
	private IPMulticast mcast;

	private Thread receivingThread = null;

	// topology-related
	private ForwarderAddress parent;
	private ForwarderAddress[] children;

	// time on which a message sent from this node itself received
	private long loopbackMessageReceivedTime;

	OverlayTrafficForwarder(ApplicationLevelMulticastRouterConfiguration config,
			GroupTable groupTable, DatagramSocket sockForOverlay, IPMulticast mcast) {
		this.config = config;
		this.groupTable = groupTable;
		this.sockForOverlay = sockForOverlay;
		this.mcast = mcast;

		this.reset();
	}

	protected void reset() {
		this.loopbackMessageReceivedTime = Timer.currentTimeMillis();
	}

	protected synchronized void start() {
		if (this.receivingThread == null) {
			this.receivingThread = new Thread(this);
			this.receivingThread.setName("Overlay Traffic Forwarder");
			this.receivingThread.setDaemon(true);
			this.receivingThread.start();
		}
	}

	protected synchronized void stop() {
		if (this.receivingThread != null) {
			this.receivingThread.interrupt();
			this.receivingThread = null;
		}
	}

	/**
	 * Returns the multicast group address which this Forwarder instance is observing.
	 */
	protected Inet4Address getGroupAddress() { return this.group; }

	protected Inet4Address setGroupAddress(Inet4Address group) {
		Inet4Address old = this.group;
		this.group = group;
		return old;
	}

	protected ForwarderAddress getParent() { return this.parent; }

	protected synchronized ForwarderAddress setParent(ForwarderAddress parent) {
		ForwarderAddress old = this.parent;
		this.parent = parent;
		return old;
	}

	protected ForwarderAddress[] getChildren() { return this.children; }

	protected synchronized ForwarderAddress[] setChildren(ForwarderAddress[] children) {
		ForwarderAddress[] old = this.children;
		this.children = children;
		return old;
	}

	protected long getLoopbackMessageReceivedTime() { return this.loopbackMessageReceivedTime; }

	protected long setLoopbackMessageReceivedTime(long time) {
		long old = this.loopbackMessageReceivedTime;
		this.loopbackMessageReceivedTime = time;
		return old;
	}

	/**
	 * Receive packets from a neighbor on an overlay
	 * and forward them to other neighbors and local multicast network.
	 */
	public void run() {
		int datagramBufSize = this.config.getDatagramBufferSize();
		byte[] datagramBuf = new byte[datagramBufSize];
		DatagramPacket packet = new DatagramPacket(datagramBuf, datagramBufSize);

		int decodedDataSize = this.config.getDatagramBufferSize() - ProtocolOnOverlay.HEADER_LEN;
		byte[] decodedData = new byte[decodedDataSize];
		DecodedMessageOnOverlay decodedMessage = new DecodedMessageOnOverlay(decodedData);

		while (true) {
			if (Thread.interrupted())
				break;

			// initialize a packet to receive any packet
			try {
				this.sockForOverlay.receive(packet);
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "An Exception thrown during receiving a datagram.", e);
				continue;
			}

			InetAddress fromInetAddress = (Inet4Address)packet.getAddress();
			if (!(fromInetAddress instanceof Inet4Address)) {
				logger.log(Level.WARNING, "A received datagram has an IPv6 address: " + packet.getAddress());
				continue;
			}
			ForwarderAddress from = new ForwarderAddress((Inet4Address)fromInetAddress, packet.getPort());
			// interpret TTL
			byte[] encoded = packet.getData();
/*
int destPort = ProtocolOnOverlay.getDestPort(encoded);
if (!(destPort == 10002 || destPort == 10003)) {
	System.out.print("rcvd from an overlay: from " + fromInet4Address + ":" + packet.getPort());
}
*/
			int overlayTTL = ProtocolOnOverlay.getOverlayTTL(encoded);
			overlayTTL--;
			if (overlayTTL <= 0) {
				logger.log(Level.WARNING, "TTL expired when forwarding. from: " + from);
				return;
			}
			ProtocolOnOverlay.setOverlayTTL(encoded, overlayTTL);

			// interpret destination (group address)
			Inet4Address dest = ProtocolOnOverlay.getDestAddress(encoded);
/*
if (!(destPort == 10002 || destPort == 10003)) {
	System.out.println(" dest: " + dest);
	System.out.flush();
}
*/

			// forward to other neighbors
			synchronized (this) {
				if (this.parent != null) {
					if (!from.equals(this.parent)) {
						packet.setAddress(this.parent.getAddress());
						packet.setPort(this.parent.getPort());

						try {
							this.sockForOverlay.send(packet);
						}
						catch (IOException e) {
							logger.log(Level.WARNING,
									"Failed to forward to " + this.parent.getAddress() + ":" + this.parent.getPort() + ".", e);
						}
					}
				}

				if (this.children != null) {
					for (ForwarderAddress addr: this.children) {
						if (!from.equals(addr)) {
							packet.setAddress(addr.getAddress());
							packet.setPort(addr.getPort());

							try {
								this.sockForOverlay.send(packet);
							}
							catch (IOException e) {
								logger.log(Level.WARNING,
										"Failed to forward to " + addr.getAddress() + ":" + addr.getPort() + ".", e);
							}
						}
					}
				}
			}	// synchronized (this)

			// forward to local multicast network
			Group group = this.groupTable.getJoinedGroup(dest);
			if (group != null) {
				ProtocolOnOverlay.decode(decodedMessage, encoded);

				this.mcast.send(decodedMessage.getSrcAddress(), decodedMessage.getSrcPort(),
						dest, decodedMessage.getDestPort(), decodedMessage.getID(), decodedMessage.getIPTTL(),
						decodedMessage.getDataLength(), decodedMessage.getData());
			}
		}
	}
}

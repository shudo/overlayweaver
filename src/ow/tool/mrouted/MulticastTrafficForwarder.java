/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.ipmulticast.IPMulticastHandler;
import ow.ipmulticast.VirtualInterface;
import ow.util.Timer;

final class MulticastTrafficForwarder implements IPMulticastHandler {
	private static Logger logger = Logger.getLogger("mrouted");

	private DatagramSocket sockForOverlay;
	private int datagramBufSize;
	private int ttlOnOverlay;
	private GroupTable groupTable;
	private int idSizeInByte;

	private byte[] encoded;
	private DatagramPacket packet;

	MulticastTrafficForwarder(DatagramSocket sock, int datagramBufSize, int ttlOnOverlay, GroupTable groupTable, int idSizeInByte) {
		this.sockForOverlay = sock;
		this.datagramBufSize = datagramBufSize;
		this.ttlOnOverlay = ttlOnOverlay;
		this.groupTable = groupTable;
		this.idSizeInByte = idSizeInByte;

		int encodedLen = ProtocolOnOverlay.getHeaderLen() + this.datagramBufSize;
		this.encoded = new byte[encodedLen];
		this.packet = new DatagramPacket(this.encoded, this.encoded.length);
	}

	/**
	 * Implements
	 * {@link MulticastHandler#process(Inet4Address, int, Inet4Address, int int, int, byte[])
	 * MulticastHandler#process()}.
	 */
	public synchronized void process(Inet4Address srcAddr, int srcPort,
			Inet4Address destAddr, int destPort,
			int id, int ttl, byte[] payload) {
		ID groupID;
		OverlayTrafficForwarder forwarder;

		ttl--;
		if (ttl <= 0) {
			// does not forward to an overlay
			return;
		}
//System.out.println("src: " + srcAddr);

		// identify the network interface
		VirtualInterface vif = VirtualInterface.findVirtualInterface(srcAddr);
		if (vif == null) {
			// not from local network
			return;
		}

		// obtain a forwarder
		groupID = ID.getHashcodeBasedID(destAddr, this.idSizeInByte);
		forwarder = this.groupTable.getOverlayTrafficForwarder(groupID);

		// record the time if a message is from this node itself
		if (srcAddr.equals(vif.getLocalAddress())) {
			forwarder.setLoopbackMessageReceivedTime(Timer.currentTimeMillis());
		}

		if (forwarder != null) {
			// encode for overlay
			int encodedLen =
				ProtocolOnOverlay.encode(this.encoded,
						srcAddr, srcPort, destAddr, destPort,
						id, ttl, this.ttlOnOverlay, payload);

			// forward to an overlay
			synchronized (forwarder) {
				ForwarderAddress parent = forwarder.getParent();
				ForwarderAddress[] children = forwarder.getChildren();
//System.out.println("  #children: " + (children == null ? "0" : children.length));
//System.out.flush();

				this.packet.setData(this.encoded, 0, encodedLen);

				if (parent != null) {
					this.packet.setAddress(parent.getAddress());
					this.packet.setPort(parent.getPort());

					try {
						this.sockForOverlay.send(this.packet);
					}
					catch (IOException e) {
						logger.log(Level.WARNING,
								"Failed to forward to " + parent.getAddress() + ":" + parent.getPort() + ".", e);
					}
/*
if (!(destPort == 10002 || destPort == 10003)) {
	int sum = 0;
	for (int i = 0; i < payload.length; i++) {
		sum += (payload[i] & 0xff);
	}
	sum %= 256;

	System.out.println("  sent to parent: len " + payload.length + " cksum " + sum);
	System.out.flush();
}
*/
				}

				if (children != null) {
					for (ForwarderAddress addr: children) {
						this.packet.setAddress(addr.getAddress());
						this.packet.setPort(addr.getPort());

						try {
							this.sockForOverlay.send(this.packet);
						}
						catch (IOException e) {
							logger.log(Level.WARNING,
									"Failed to forward to " + addr.getAddress() + ":" + addr.getPort() + ".", e);
						}
/*
if (!(destPort == 10002 || destPort == 10003)) {
	System.out.println("  sent to a child: addr " + addr.getAddress() + " port " + addr.getPort());
	System.out.flush();
}
*/
					}
				}
			}	// synchronized (forwarder)
		}
	}
}

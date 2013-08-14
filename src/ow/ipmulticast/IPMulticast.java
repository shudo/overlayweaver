/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.ipmulticast;

import java.io.IOException;
import java.net.Inet4Address;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class for sending and receiving IGMP packets.
 * Note that functions of this class requires native methods and "root" priviledge.
 */
public final class IPMulticast {
	private final static Logger logger = Logger.getLogger("ipmulticast");

	private static IPMulticast instance = null;

	private boolean working = false;
	private boolean suspended = false;
	private Thread receiverThread = null;

	public static IPMulticast getInstance() throws IOException {
		synchronized (IPMulticast.class) {
			if (instance == null) {
				instance = new IPMulticast();

				Native.initialize();	// throws IOException
			}
		}

		return instance;
	}

	private IPMulticast() {};	// prohibits instantiation

	public synchronized void start(final IPMulticastHandler handler) {
		if (this.working) return;

		Runnable r = new Runnable() {
			MulticastMessage container = new MulticastMessage();
			Inet4Address srcAddr, destAddr;

			public void run() {
				try {
					while (true) {
						synchronized (IPMulticast.this) {
							while (IPMulticast.this.suspended) {
								IPMulticast.this.wait();
							}
						}

						if (Thread.interrupted()) {
							break;
						}

						try {
							Native.receiveMulticast(container);
						}
						catch (IOException e) {
							logger.log(Level.WARNING, "Native#receiveMulticast() throws an IOException.", e);
							break;
						}

						srcAddr = Utilities.intToInet4Address(container.srcaddr);
						destAddr = Utilities.intToInet4Address(container.destaddr);
						handler.process(srcAddr, container.srcport, destAddr, container.destport,
								container.id, container.ttl, container.data);
					}
				}
				catch (InterruptedException e) {
					logger.log(Level.WARNING, "Multicast receiving daemon interrupted and die.");
				}

				Native.stop();
				working = false;
			}
		};
		this.receiverThread = new Thread(r);
		this.receiverThread.setName("Multicast receiving daemon");
		this.receiverThread.setDaemon(true);
		this.receiverThread.start();

		this.working = true;
	}

	public synchronized void stop() {
		if (this.receiverThread != null) {
			this.receiverThread.interrupt();
			this.receiverThread = null;
		}

		Native.stop();
		this.working = false;
	}

	public synchronized void suspend() {
		// suspend daemons
		this.suspended = true;
	}

	public synchronized void resume() {
		// resume daemons
		this.suspended = false;

		this.notifyAll();
	}

	/**
	 * Send a datagram to the specified multicast group address.
	 *
	 * @param data payload
	 */
	public synchronized void send(Inet4Address srcAddress, int srcPort, Inet4Address destAddress, int destPort, int id, int ttl,
			int payloadLength, byte[] payload) {
		Native.sendMulticast(Utilities.Inet4AddressToInt(srcAddress), srcPort, Utilities.Inet4AddressToInt(destAddress), destPort, id, ttl,
				payloadLength, payload);
	}

	/**
	 * Join a multicast group.
	 */
	public void joinGroup(Inet4Address group, VirtualInterface vif) {
		Native.joinGroup(
				Utilities.Inet4AddressToInt(group),
				Utilities.Inet4AddressToInt(vif.getLocalAddress()),
				vif.getIfIndex());
	}

	/**
	 * Leave a multicast group.
	 */
	public void leaveGroup(Inet4Address group, VirtualInterface vif) {
		Native.leaveGroup(
				Utilities.Inet4AddressToInt(group),
				Utilities.Inet4AddressToInt(vif.getLocalAddress()),
				vif.getIfIndex());
	}

	/**
	 * A class representing a multicast message.
	 * This class is only for exchange between Java code and native code.
	 */
	static class MulticastMessage {
		int srcaddr;
		int srcport;
		int destaddr;
		int destport;
		int id;
		int ttl;
		byte[] data;
		int datalen;
	}
}

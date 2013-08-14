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
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class for sending and receiving IGMP packets.
 * Note that functions of this class requires native methods and "root" privilege.
 */
public final class IGMP {
	private final static Logger logger = Logger.getLogger("ipmulticast");

	public static int DEFAULT_IGMP_VERSION = 3; 

	public static Inet4Address ALL_HOSTS_GROUP;
	public static Inet4Address ALL_ROUTERS_GROUP;

	// constants
	public final static int IGMP_MEMBERSHIP_QUERY		= 0x11;	// membership query
	public final static int IGMP_V1_MEMBERSHIP_REPORT	= 0x12;	// Ver. 1 membership report
	public final static int IGMP_V2_MEMBERSHIP_REPORT	= 0x16;	// Ver. 2 membership report
	public final static int IGMP_V3_MEMBERSHIP_REPORT	= 0x22;	// Ver. 3 membership report
	public final static int IGMP_V2_LEAVE_GROUP			= 0x17;	// Leave-group message

	public final static int IGMP_DVMRP					= 0x13;	// DVMRP routing message
	public final static int IGMP_PIM					= 0x14;	// PIM routing message
	public final static int IGMP_TRACE					= 0x15;

	public final static int IGMP_MTRACE_RESP			= 0x1e;	// traceroute resp.(to sender)
	public final static int IGMP_MTRACE					= 0x1f;	// mcast traceroute messages

	private static String[] TYPE_STRINGS = {
		/* 00 - 03 */	"", "", "", "",
		/* 04 - 07 */	"", "", "", "",
		/* 08 - 0b */	"", "", "", "",
		/* 0c - 0f */	"", "", "", "",
		/* 10 - 13 */	"", "IGMP_MEMBERSHIP_QUERY", "IGMP_V1_MEMBERSHIP_REPORT", "IGMP_DVMRP",
		/* 14 - 17 */	"IGMP_PIM", "IGMP_TRACE", "IGMP_V2_MEMBERSHIP_REPORT", "IGMP_V2_LEAVE_GROUP",
		/* 18 - 1b */	"", "", "", "",
		/* 1c - 1f */	"", "", "IGMP_MTRACE_RESP", "IGMP_MTRACE"
	};

	private static IGMP instance = null;

	private Thread receiverThread = null;
	private boolean working = false;
	private boolean suspended = false;

	static {
		byte[] addr = new byte[4];

		addr[0] = (byte)224; addr[1] = (byte)0; addr[2] = (byte)0; addr[3] = (byte)1;
		try {
			ALL_HOSTS_GROUP = (Inet4Address)Inet4Address.getByAddress(addr);
		}
		catch (UnknownHostException e) { e.printStackTrace(); }

		addr[0] = (byte)224; addr[1] = (byte)0; addr[2] = (byte)0; addr[3] = (byte)2;
		try {
			ALL_ROUTERS_GROUP = (Inet4Address)Inet4Address.getByAddress(addr);
		}
		catch (UnknownHostException e) { e.printStackTrace(); }
	}

	public static IGMP getInstance() throws IOException {
		synchronized (IGMP.class) {
			if (instance == null) {
				instance = new IGMP();

				Native.initialize();	// throws IOException
			}
		}

		return instance;
	}

	private IGMP() {};	// prohibits instantiation

	public synchronized void start(final IGMPHandler handler) {
		if (this.working) return;

		Runnable r = new Runnable() {
			IGMPMessage container = new IGMPMessage();
			Inet4Address src, dest, group;

			public void run() {
				try {
					while (true) {
						if (Thread.interrupted()) {
							break;
						}

						try {
							Native.receiveIGMP(container);
						}
						catch (IOException e) {
							logger.log(Level.WARNING, "Native#receiveIGMP() throws an IOException.", e);
							break;
						}

						src = Utilities.intToInet4Address(container.src);
						dest = Utilities.intToInet4Address(container.dest);
						group = Utilities.intToInet4Address(container.group);

						VirtualInterface vif =
							VirtualInterface.findVirtualInterface(src);

						handler.process(src, dest, container.type, container.code, group, container.data, vif);
					}	// while (true)
				}
				catch (InterruptedException e) {
					logger.log(Level.WARNING, "IGMP receiving daemon interrupted and die.", e);
				}

				Native.stop();
				working = false;
			}
		};
		this.receiverThread = new Thread(r);
		this.receiverThread.setName("IGMP receiving daemon");
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
		this.suspended = true;
	}

	public synchronized void resume() {
		this.suspended = false;

		this.notifyAll();
	}

	public synchronized void send(Inet4Address src, Inet4Address dest, int type, int code, Inet4Address group, byte[] data) {
		Native.sendIGMP(Utilities.Inet4AddressToInt(src), Utilities.Inet4AddressToInt(dest), type, code, Utilities.Inet4AddressToInt(group), data);
	}

	/**
	 * Returns a String representing the specified type.
	 */
	public static String typeString(int type) {
		String str;
		try {
			str = TYPE_STRINGS[type];
		}
		catch (ArrayIndexOutOfBoundsException e) {
			str = "";
		}

		return str;
	}

	/**
	 * A class representing an IGMP message.
	 * This class is only for exchange between Java code and native code.
	 */
	static class IGMPMessage {
		int src;
		int dest;
		int type;
		int code;
		int group;
		byte[] data;
	}
}

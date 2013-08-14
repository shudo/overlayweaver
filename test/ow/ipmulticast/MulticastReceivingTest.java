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
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class MulticastReceivingTest {
	public final static String DEFAULT_GROUP = "224.220.0.1";
	public final static int DEFAULT_PORT = 10000;
	public final static int DEFAULT_ID = 123;
	public final static int DEFAULT_TTL = 128;

	public static void main(String[] args) {
		IPMulticast mcast;
		try {
			mcast = IPMulticast.getInstance();
		}
		catch (IOException e) {
			System.err.println("Failed to intialize IGMP.");
			e.printStackTrace();
			return;
		}

		IPMulticastHandler handler = new AnMulticastHandler();

		mcast.start(handler);

		// parse arguments
		String groupName = DEFAULT_GROUP;
		int port = DEFAULT_PORT;

		if (args.length > 0) {
			groupName = args[0];
			if (args.length > 1) {
				port = Integer.parseInt(args[1]);
			}
		}

		Inet4Address addr = null;
		try {
			addr = (Inet4Address)Inet4Address.getByName(groupName);
		}
		catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// join the group
		MulticastSocket msock;
		try {
			msock = new MulticastSocket();
			msock.joinGroup(addr);
		}
		catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// send a multicast message
		Inet4Address selfAddress = null;
		try {
			selfAddress = (Inet4Address)Inet4Address.getLocalHost();
		}
		catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		byte[] data = new byte[4];
		data[0] = 1; data[1] = 2; data[2] = 3; data[3] = 4;
		mcast.send(selfAddress, DEFAULT_PORT, addr, DEFAULT_PORT, DEFAULT_ID, DEFAULT_TTL, data.length, data);

		// wait for a while
		try {
			Thread.sleep(300000L);	// 300 sec
		}
		catch (InterruptedException e) {
			System.err.println("Thread#sleep() interrupted:");
			e.printStackTrace();
		}

		mcast.stop();
	}

	private static class AnMulticastHandler implements IPMulticastHandler {
		public void process(Inet4Address src, int srcPort, Inet4Address dest, int destPort,
				int id, int ttl, byte[] data) {
			System.out.println();

			System.out.println("src:  " + src + ":" + srcPort);
			System.out.println("dest: " + dest + ":" + destPort);
			System.out.println("ID:   " + id);
			System.out.println("TTL:  " + ttl);
			if (data != null) {
				System.out.println("data len: " + data.length);
				if (data.length > 0) {
					System.out.print("data:");
					for (int i = 0; i < data.length; i++) {
						System.out.print(" " + Integer.toHexString(data[i] & 0xff));
					}
					System.out.println();
				}
			}
			else {
				System.out.println("data: NULL");
			}
		}
	}
}

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

package ow.tool.mrouted;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;

public class MulticastReceivingTest {
	public final static int DATAGRAM_BUF_SIZE = 8192;

	public final static void main(String[] args) {
		String mcastAddressStr = "224.220.0.1";	// for test
		int port = 10000;

		if (args.length > 0) {
			mcastAddressStr = args[0];

			if (args.length > 1) {
				port = Integer.parseInt(args[1]);
			}
		}

		InetAddress mcastAddress = null;
		try {
			mcastAddress = InetAddress.getByName(mcastAddressStr);
		}
		catch (UnknownHostException e) {
			System.err.println("The specified address could not be resolved: " + mcastAddressStr);
			e.printStackTrace();
			System.exit(1);
		}

		(new MulticastReceivingTest()).start(mcastAddress, port);
	}

	public void start(InetAddress group, int port) {
		MulticastSocket sock = null;
		try {
			sock = new MulticastSocket(port);
		}
		catch (IOException e) {
			System.err.println("Could not create a multicast socket.");
			e.printStackTrace();
			System.exit(1);
		}

		try {
			sock.joinGroup(group);
		}
		catch (IOException e) {
			System.err.println("Could not join a group: " + group);
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println("Joined: " + group);

		int bufSize = DATAGRAM_BUF_SIZE;
		byte[] buf = new byte[bufSize];
		DatagramPacket packet = new DatagramPacket(buf, bufSize);

		while (true) {
			try {
				sock.receive(packet);
			}
			catch (IOException e) {
				e.printStackTrace();
				break;
			}

			System.out.println(packet.getSocketAddress());
		}
	}
}

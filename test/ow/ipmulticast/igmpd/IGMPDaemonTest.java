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

package ow.ipmulticast.igmpd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.util.Set;

import ow.ipmulticast.Group;
import ow.ipmulticast.VirtualInterface;

public class IGMPDaemonTest {
	public final static void main(String[] args) {
		IGMPDaemonConfiguration config = new IGMPDaemonConfiguration();
		IGMPDaemon igmpd = null;

		try {
			igmpd = new IGMPDaemon(config);
		}
		catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// start an IP multicast router
		igmpd.start(new ACallback());

		// read lines from standard input
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String line = null;
			try {
				line = in.readLine();
			}
			catch (IOException e) {
				break;
			}

			if (line.length() > 0) break;

			igmpd.printStatus(System.out);
		}
	}

	private static class ACallback implements GroupChangeCallback {
		public void igmpMessageReceived(Inet4Address src, Inet4Address dest, int type, int code, Inet4Address groupAddress,
				byte[] data, VirtualInterface vif) {
			System.out.println("IGMP message received.");
		}

		public void included(Set<Group> includedGroupSet, VirtualInterface vif) {
			StringBuilder sb = new StringBuilder();

			sb.append("included:");
			for (Group g: includedGroupSet) {
				sb.append(" ");
				sb.append(g.getGroupAddress());
			}

			System.out.println(sb);
		}

		public void excluded(Set<Group> excludedGroupSet, VirtualInterface vif) {
			StringBuilder sb = new StringBuilder();

			sb.append("excluded:");
			for (Group g: excludedGroupSet) {
				sb.append(" ");
				sb.append(g.getGroupAddress());
			}

			System.out.println(sb);
		}
	}
}

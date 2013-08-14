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

public class IGMPReceivingTest {
	public static void main(String[] args) {
		IGMP igmp;
		try {
			igmp = IGMP.getInstance();
		}
		catch (IOException e) {
			System.err.println("Failed to intialize IGMP.");
			e.printStackTrace();
			return;
		}

		IGMPHandler handler = new AnIGMPHandler();

		igmp.start(handler);

		// send a membership query
		Inet4Address selfAddress = null;
		try {
			selfAddress = (Inet4Address)Inet4Address.getLocalHost();
		}
		catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		igmp.send(selfAddress, IGMP.ALL_HOSTS_GROUP, IGMP.IGMP_MEMBERSHIP_QUERY, 0, null, null);

		// wait for a while
		try {
			Thread.sleep(10000L);	// 10 sec
		}
		catch (InterruptedException e) {
			System.err.println("Thread#sleep() interrupted:");
			e.printStackTrace();
		}

		igmp.stop();
	}

	private static class AnIGMPHandler implements IGMPHandler {
		public void process(Inet4Address src, Inet4Address dest, int type, int code, Inet4Address group,
				byte[] data, VirtualInterface vif) {
			System.out.println();

			System.out.println("src:  " + src);
			System.out.println("dest: " + dest);
			System.out.println("type: " + IGMP.typeString(type));
			System.out.println("code: " + code);
		}
	}
}

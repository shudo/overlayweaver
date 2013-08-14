/*
 * Copyright 2008 Kazuyuki Shudo, and contributors.
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

package ow.messaging.util;

import java.net.InetAddress;

import ow.messaging.upnp.Mapping;

/**
 * A test for {@link ow.messaging.upnp.UPnPManager UPnPManager} class.
 * This class requires clink*.jar in the classpath.
 */
public class UPnPAddressPortMapperTest {
	public static void main(String[] args) {
		UPnPAddressPortMapper.start(
				"192.168.0.107",			// internal IP address
				3997,						// port
				Mapping.Protocol.UDP,	// protocol: TCP or UDP
				null,						// description (option)
				null,						// MessagingProvider
				90 * 1000L);				// UPnP timeout

		for (int i = 0; i < 45; i++) {
			Mapping mapping = UPnPAddressPortMapper.getAddedMapping();
			InetAddress extAddress = UPnPAddressPortMapper.getExternalAddress();

			System.out.println("mapping: " + mapping);
			System.out.println("external address: " + extAddress);

			if (mapping != null) break;

			try {
				Thread.sleep(2 * 1000L);	// 2 sec
			}
			catch (InterruptedException e) { /*ignore*/ }
		}
	}
}

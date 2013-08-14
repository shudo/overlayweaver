/*
 * Copyright 2007,2010 Kazuyuki Shudo, and contributors.
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

package ow.messaging.upnp;

/**
 * A test for {@link ow.messaging.upnp.UPnPManager UPnPManager} class.
 * This class requires clink*.jar in the classpath.
 */
public class UPnPTest {
	public static void main(String[] args) {
		UPnPManager upnp = UPnPManager.getInstance();
		if (!upnp.start()) {
			System.out.println("Could not start UPnPManager.");
			return;
		}

		upnp.waitForDeviceFound();

		System.out.println("External IP address: " + upnp.getExternalAddress());

		Mapping map = new Mapping(3997,	// external port
				"192.168.0.107",			// internal IP address
				3997,						// internal port
				Mapping.Protocol.UDP,	// protocol: TCP or UDP
				null);						// description (option)

		// adds a mapping
		if (upnp.addMapping(map))
			System.out.println("succeeded.");
		else
			System.out.println("failed.");

		// deletes a mapping
		if (upnp.deleteMapping(map.getExternalPort(), map.getProtocol()))
			System.out.println("succeeded.");
		else
			System.out.println("failed.");

		upnp.stop();
	}
}

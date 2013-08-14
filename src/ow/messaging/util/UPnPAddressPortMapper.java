/*
 * Copyright 2007-2008 Kazuyuki Shudo, and contributors.
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
import java.net.UnknownHostException;

import ow.messaging.MessagingProvider;
import ow.messaging.upnp.Mapping;
import ow.messaging.upnp.UPnPManager;

/**
 * An utility class to establish a UPnP address port mapping.
 */
public final class UPnPAddressPortMapper implements Runnable {
	private String internalAddr;
	private int port;
	private Mapping.Protocol protocol;
	private String desc;
	private MessagingProvider provider;
	private long timeout;

	private static UPnPAddressPortMapper singletonInstance = null;

	/**
	 * Starts a mapping processes.
	 * It finishes and disappears by itself.
	 */
	public static void start(
			String internalAddress, int port, Mapping.Protocol protocol, String description,
			MessagingProvider provider /* can be null */, long timeout) {
		synchronized (UPnPAddressPortMapper.class) {
			if (UPnPAddressPortMapper.singletonInstance != null) {
				return;
			}

			UPnPAddressPortMapper.singletonInstance =
				new UPnPAddressPortMapper(internalAddress, port, protocol, description, provider, timeout);
		}

		Thread t = new Thread(UPnPAddressPortMapper.singletonInstance);
		t.setName("UPnPAddressPortMapper");
		t.setDaemon(true);
		t.start();
	}

	private UPnPAddressPortMapper(
			String internalAddress, int port, Mapping.Protocol protocol, String description,
			MessagingProvider provider, long timeout) {
		this.internalAddr = internalAddress;
		this.port = port;
		this.protocol = protocol;
		this.desc = description;
		this.provider = provider;
		this.timeout = timeout;
	}

	public void run() {
		UPnPManager upnp = null;
		try {
			upnp = UPnPManager.getInstance();
		}
		catch (NoClassDefFoundError e) {
			// clink*.jar is not found
			System.out.println("UPnP library is not found and UPnP NAT Traversal is disabled.");
			System.out.println("If you want the function, add clink*.jar to your classpath.");
			System.out.println();

			return;
		}

		upnp.start();

		if (!upnp.waitForDeviceFound(this.timeout)) {
			upnp.stop();
			return;
		}

		// adds an address port mapping
		Mapping map = new Mapping(this.port,
				this.internalAddr, this.port, this.protocol, this.desc);
		boolean succeed = upnp.addMapping(map);

		if (succeed) { UPnPAddressPortMapper.addedMapping = map; }	// save

		// sets the external address to OW messaging facilities
		InetAddress extAddress = upnp.getExternalAddress();
		if (extAddress != null) {
			if (this.provider != null) {
				try {
					this.provider.setSelfAddress(extAddress.getHostAddress().toString());
				}
				catch (UnknownHostException e) {
					// NOTREACHED
				}
			}

			UPnPAddressPortMapper.extAddress = extAddress;	// save
		}

		upnp.stop();

		synchronized (UPnPAddressPortMapper.class) {
			UPnPAddressPortMapper.singletonInstance = null;
		}
	}

	//
	// Fields and methods to provide Internet router information
	//

	private static Mapping addedMapping = null;
	private static InetAddress extAddress = null;

	/**
	 * Returns the added address and port mapping.
	 *
	 * @return null if no mapping has been set.
	 */
	public static Mapping getAddedMapping() { return UPnPAddressPortMapper.addedMapping; }

	/**
	 * Returns the external IP address of an Internet router.
	 *
	 * @return null if failed to get the address.
	 */
	public static InetAddress getExternalAddress() { return UPnPAddressPortMapper.extAddress; }

	/**
	 * Returns whether this mapper is trying to set mapping or not.
	 */
	public static boolean isTrying() {
		boolean trying = false;

		synchronized (UPnPAddressPortMapper.class) {
			if (UPnPAddressPortMapper.singletonInstance != null)
				trying = true;
		}

		return trying;
	}
}

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

package ow.messaging;

import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.tool.emulator.RemoteAppInstanceTable;

/**
 * The factory which provides a messaging provider.
 */
public final class MessagingFactory {
	private final static Logger logger = Logger.getLogger("messaging");

	private final static Class/*<MessagingProvider>*/[] PROVIDERS = {
		ow.messaging.udp.UDPMessagingProvider.class,				// "UDP"
		ow.messaging.tcp.TCPMessagingProvider.class,				// "TCP"
		ow.messaging.emulator.EmuMessagingProvider.class,		// "Emulator"
		ow.messaging.distemulator.DEmuMessagingProvider.class	// "DistributedEmulator"
	};

	public final static String EMULATOR_PROVIDER_NAME = "Emulator";
	public final static String DISTRIBUTED_EMULATOR_PROVIDER_NAME = "DistributedEmulator";
	private static boolean FORCE_EMULATOR = false;
	private static boolean FORCE_DISTRIBUTED_EMULATOR = false;
	public static int INITIAL_EMULATOR_HOST_ID = 0;
	public static RemoteAppInstanceTable HOST_TABLE_FOR_DIST_EMULATOR = null;

	private static HashMap<String,MessagingProvider> providerTable;

	static {
		// register providers
		providerTable = new HashMap<String,MessagingProvider>();
		for (Class clazz: PROVIDERS) {
			Object o;
			try {
				o = clazz.newInstance();
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Could not instantiate an object of the class: " + clazz, e);
				continue;
			}

			if (o instanceof MessagingProvider) {
				MessagingProvider provider = (MessagingProvider)o;

				providerTable.put(provider.getName(), (MessagingProvider)provider);
			}
		}
	}

	/**
	 * Return a messaging provider associated to the given name.
	 * The name should be one of the following names: "TCP", "UDP" or "Emulator".
	 * There is an utility class {@link Signature Signature} to generate a signature.
	 *
	 * @param messagingType name of a messaging provider.
	 * @param messageSignature signature embedded to every message. 
	 * @return a message provider.
	 * @throws NoSuchProviderException
	 */
	public static MessagingProvider getProvider(String messagingType, byte[] messageSignature) throws NoSuchProviderException {
		return getProvider(messagingType, messageSignature, false);
	}

	public static MessagingProvider getProvider(String messagingType, byte[] messageSignature, boolean notForced) throws NoSuchProviderException {
		if (!notForced) {
			if (FORCE_DISTRIBUTED_EMULATOR) {
				messagingType = DISTRIBUTED_EMULATOR_PROVIDER_NAME;
			}
			else if (FORCE_EMULATOR) {
				messagingType = EMULATOR_PROVIDER_NAME;
			}
		}

		MessagingProvider provider = providerTable.get(messagingType);
		if (provider == null) {
			throw new NoSuchProviderException("No such provider: " + messagingType);
		}

		// A workaround for Emulator
		MessagingProvider substitutedProvider = provider.substitute();
		if (substitutedProvider != null) {
			provider = substitutedProvider;
		}

		// set message signature to provider
		if (messageSignature != null) {
			provider.setMessageSignature(messageSignature);
		}

		return provider;
	}

	/**
	 * Enforce returning the emulator provider on the factory.
	 *
	 * @param initialEmulatorHostID the first ID of virtual host names (emuXX) in an emulator.
	 */
	public static void forceEmulator(int initialEmulatorHostID) {
		FORCE_EMULATOR = true;
		INITIAL_EMULATOR_HOST_ID = initialEmulatorHostID;
	}

	/**
	 * Enforce returning the distributed emulator provider on the factory.
	 *
	 * @param initialEmulatorHostID the first ID of virtual host names (emuXX) in an emulator.
	 */
	public static void forceDistributedEmulator(int initialEmulatorHostID, RemoteAppInstanceTable hostTable) {
		FORCE_DISTRIBUTED_EMULATOR = true;
		INITIAL_EMULATOR_HOST_ID = initialEmulatorHostID;
		HOST_TABLE_FOR_DIST_EMULATOR = hostTable;
	}
}

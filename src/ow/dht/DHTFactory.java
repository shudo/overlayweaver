/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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

package ow.dht;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.dht.impl.DHTProvider;
import ow.id.ID;
import ow.messaging.Signature;
import ow.routing.RoutingService;

/**
 * A {@link ow.dht.DHT DHT} factory.
 */
public class DHTFactory {
	private final static Logger logger = Logger.getLogger("dht");

	private final static Class[] PROVIDERS = {
		ow.dht.impl.ChurnTolerantDHTProvider.class,	// "ChurnTolerantDHT", "DHT"
		ow.dht.impl.BasicDHTProvider.class,			// "BasicDHT"
		ow.dht.impl.CHTProvider.class,					// "CHT" (Centralized Hash Table)
		ow.dht.memcached.impl.MemcachedProvider.class	// "memcached"
	};

	private final static Map<String,DHTProvider> providerTable;
	private final static Map<String,Integer> idTable;

	static {
		// register providers and algorithm IDs
		providerTable = new HashMap<String,DHTProvider>();
		idTable = new HashMap<String,Integer>();

		int id = 1;
		for (Class clazz: PROVIDERS) {
			Object o;
			try {
				o = clazz.newInstance();
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Could not instantiate an object of the class: " + clazz, e);
				continue;
			}

			if (o instanceof DHTProvider) {
				DHTProvider provider = (DHTProvider)o;

				for (String dhtName: provider.getNames()) {
					// register provider
					providerTable.put(dhtName, provider);

					// register ID
					idTable.put(dhtName, id);
				}
				id++;
			}
		}
	}

	/**
	 * Returns a default configuration.
	 */
	public static DHTConfiguration getDefaultConfiguration() {
		return new DHTConfiguration();
	}

	/**
	 * Returns an instance of DHT.
	 * ID of this instance is determined consistently based on the hostname.
	 * Note that the working directory specified in the DHTConfiguration is created.
	 */
	public static <V extends Serializable> DHT<V> getDHT(DHTConfiguration config)
			throws Exception {
		DHTProvider provider = providerTable.get(config.getImplementationName());
		return provider.getDHT(
				Signature.getAllAcceptingApplicationID(), Signature.getAllAcceptingApplicationVersion(),
				config, (ID)null);
	}

	/**
	 * Returns an instance of DHT.
	 * Note that the working directory specified in the DHTConfiguration is created.
	 */
	public static <V extends Serializable> DHT<V> getDHT(DHTConfiguration config, ID selfID)
			throws Exception {
		DHTProvider provider = providerTable.get(config.getImplementationName());
		return provider.getDHT(
				Signature.getAllAcceptingApplicationID(), Signature.getAllAcceptingApplicationVersion(),
				config, selfID);
	}

	/**
	 * Returns an instance of DHT.
	 * ID of this instance is determined consistently based on the hostname.
	 * Note that the working directory specified in the DHTConfiguration is created.
	 *
	 * @param applicationID ID of application embedded in a message signature to avoid cross-talk between different applications.
	 */
	public static <V extends Serializable> DHT<V> getDHT(
			short applicationID, short applicationVersion, DHTConfiguration config)
				throws Exception {
		DHTProvider provider = providerTable.get(config.getImplementationName());
		return provider.getDHT(applicationID, applicationVersion, config, null);
	}

	/**
	 * Returns an instance of DHT.
	 * Note that the working directory specified in the DHTConfiguration is created.
	 *
	 * @param applicationID ID of application embedded in a message signature to avoid cross-talk between different applications.
	 */
	public static <V extends Serializable> DHT<V> getDHT(
			short applicationID, short applicationVersion, DHTConfiguration config, ID selfID)
				throws Exception {
		DHTProvider provider = providerTable.get(config.getImplementationName());
		return provider.getDHT(applicationID, applicationVersion, config, selfID);
	}

	/**
	 * Returns an instance of DHT.
	 * Note that the working directory specified in the DHTConfiguration is created.
	 */
	public static <V extends Serializable> DHT<V> getDHT(DHTConfiguration config, RoutingService routingSvc)
				throws Exception {
		DHTProvider provider = providerTable.get(config.getImplementationName());
		return provider.getDHT(config, routingSvc);
	}
}

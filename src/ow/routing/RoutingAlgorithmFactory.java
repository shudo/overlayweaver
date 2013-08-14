/*
 * Copyright 2006-2007,2011 National Institute of Advanced Industrial Science
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

package ow.routing;

import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoutingAlgorithmFactory {
	private final static Logger logger = Logger.getLogger("routing");

	private final static Class/*<RoutingAlgorithmProvider>*/[] PROVIDERS = {
		ow.routing.chord.ChordProvider.class,					// "Chord"
		ow.routing.kademlia.KademliaProvider.class,			// "Kademlia"
		ow.routing.koorde.KoordeProvider.class,				// "Koorde"
		ow.routing.linearwalker.LinearWalkerProvider.class,	// "LinearWalker"
		ow.routing.pastry.PastryProvider.class,				// "Pastry"
		ow.routing.tapestry.TapestryProvider.class,			// "Tapestry"
		ow.routing.frtchord.FRTChordProvider.class			// "FRT-Chord"
	};

	private final static Map<String,RoutingAlgorithmProvider> providerTable;
	private final static Map<String,Integer> idTable;

	static {
		// register providers and algorithm IDs
		providerTable = new HashMap<String,RoutingAlgorithmProvider>();
		idTable = new HashMap<String,Integer>();

		int id = 1;
		for (Class<RoutingAlgorithmProvider> clazz: PROVIDERS) {
			Object o;
			try {
				o = clazz.newInstance();
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Could not instantiate an object of the class: " + clazz, e);
				continue;
			}

			if (o instanceof RoutingAlgorithmProvider) {
				// register provider
				RoutingAlgorithmProvider provider = (RoutingAlgorithmProvider)o;
				providerTable.put(provider.getName(), provider);

				// register ID
				idTable.put(provider.getName(), id);
				id++;
			}
		}
	}

	/**
	 * Returns a routing algorithm provider associated with the given name.
	 *
	 * @param algorithmName name of a routing algorithm.
	 * @return a provider.
	 * @throws NoSuchProviderException
	 */
	public static RoutingAlgorithmProvider getProvider(String algorithmName) throws NoSuchProviderException {
		RoutingAlgorithmProvider provider = providerTable.get(algorithmName);
		if (provider == null) {
			throw new NoSuchProviderException("No such algorithm: " + algorithmName);
		}
		return provider;
	}

	/**
	 * Returns the ID of the specified algorithm.
	 * Note that an ID is an integer which is equal to or greater than 1.
	 *
	 * @param algorithmName name of a routing algorithm.
	 * @return an ID of the algorithm.
	 * @throws NoSuchProviderException
	 */
	public static byte getAlgorithmID(String algorithmName) throws NoSuchProviderException {
		Integer idInteger = idTable.get(algorithmName);
		if (idInteger == null) {
			if (idInteger == null) {
				throw new NoSuchProviderException("No such algorithm: " + algorithmName);
			}
		}
		return (byte)(int)idInteger;
	}
}

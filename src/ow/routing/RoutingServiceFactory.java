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

package ow.routing;

import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RoutingServiceFactory {
	private final static Logger logger = Logger.getLogger("routing");

	private final static Class/*<RoutingServiceProvider>*/[] PROVIDERS = {
		ow.routing.impl.IterativeRoutingDriverProvider.class,	// "Iterative"
		ow.routing.impl.RecursiveRoutingDriverProvider.class		// "Recursive"
	};

	private final static Map<String,RoutingServiceProvider> providerTable; 
	private final static Map<String,Integer> idTable;

	static {
		// register providers and algorithm IDs
		providerTable = new HashMap<String,RoutingServiceProvider>();
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

			if (o instanceof RoutingServiceProvider) {
				// register provider
				RoutingServiceProvider provider = (RoutingServiceProvider)o;

				// register ID
				idTable.put(provider.getName(), id);
				id++;

				providerTable.put(provider.getName(), provider);
			}
		}
	}

	/**
	 * Return a routing service provider associated with the specified routing style.
	 *
	 * @param routingStyle name of a routing style, which should be "Iterative" or "Recursive".
	 * @return a provider.
	 * @throws NoSuchProviderException
	 */
	public static RoutingServiceProvider getProvider(String routingStyle) throws NoSuchProviderException {
		RoutingServiceProvider provider = providerTable.get(routingStyle);
		if (provider == null) {
			throw new NoSuchProviderException("No such routing style: " + routingStyle);
		}
		return provider;
	}

	/**
	 * Return the ID of the specified routing style.
	 *
	 * @param routingStyle name of a routing style, which should be "Iterative" or "Recursive".
	 * @return a provider.
	 * @throws NoSuchProviderException
	 */
	public static byte getRoutingStyleID(String routingStyle) throws NoSuchProviderException {
		Integer idInteger = idTable.get(routingStyle);
		if (idInteger == null) {
			throw new NoSuchProviderException("No such routing style: " + routingStyle);
		}
		return (byte)(int)idInteger;
	}
}

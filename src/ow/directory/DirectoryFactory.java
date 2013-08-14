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

package ow.directory;

import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DirectoryFactory {
	private final static Logger logger = Logger.getLogger("directory");

	private final static Class/*<DirectoryProvider>*/[] PROVIDERS = {
		ow.directory.inmemory.VolatileMapProvider.class,		// "VolatileMap"
		ow.directory.inmemory.PersistentMapProvider.class,	// "PersistentMap"
		ow.directory.berkeleydb.BerkeleyDBProvider.class		// "BerkeleyDB"
	};

	private final static HashMap<String,DirectoryProvider> providerTable;

	static {
		// register providers
		providerTable = new HashMap<String,DirectoryProvider>();
		for (Class<DirectoryProvider> clazz: PROVIDERS) {
			Object o;
			try {
				o = clazz.newInstance();
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "Could not instantiate an object of the class: " + clazz, e);
				continue;
			}

			if (o instanceof DirectoryProvider) {
				DirectoryProvider provider = (DirectoryProvider)o;
				providerTable.put(provider.getName(), provider);
			}
		}
	}

	/**
	 * Return a directory provider associate with the given name.
	 * The name should be one of the following names: "BerkeleyDB", "PersistentMap" or "VolatileMap".
	 *
	 * @param providerName name of a directory provider. 
	 * @return a directory provider.
	 * @throws NoSuchProviderException
	 */
	public static DirectoryProvider getProvider(String providerName) throws NoSuchProviderException {
		DirectoryProvider provider = providerTable.get(providerName);
		if (provider == null) {
			throw new NoSuchProviderException("No such provider: " + providerName);
		}
		return provider;
	}
}

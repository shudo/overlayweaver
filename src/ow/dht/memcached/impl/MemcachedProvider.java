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

package ow.dht.memcached.impl;

import java.io.Serializable;

import ow.dht.DHT;
import ow.dht.DHTConfiguration;
import ow.dht.impl.DHTProvider;
import ow.id.ID;
import ow.routing.RoutingService;

public class MemcachedProvider implements DHTProvider {
	private final static String[] DHT_NAMES = { "memcached" };

	public String[] getNames() {
		return DHT_NAMES;
	}

	public <V extends Serializable> DHT getDHT(
			short applicationID, short applicationVersion, DHTConfiguration config, ID selfID)
				throws Exception {
		config.setMultipleValuesForASingleKey(false);	// memcached does not allow multiple values for a key

		return new MemcachedImpl(applicationID, applicationVersion, config, selfID);
	}

	public <V extends Serializable> DHT getDHT(DHTConfiguration config, RoutingService routingSvc)
			throws Exception {
		config.setMultipleValuesForASingleKey(false);	// memcached does not allow multiple values for a key

		return new MemcachedImpl(config, routingSvc);
	}
}

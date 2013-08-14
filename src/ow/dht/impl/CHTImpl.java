/*
 * Copyright 2006-2010 Kazuyuki Shudo, and contributors.
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

package ow.dht.impl;

import java.io.File;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.DHTConfiguration;
import ow.dht.ValueInfo;
import ow.directory.DirectoryConfiguration;
import ow.directory.DirectoryFactory;
import ow.directory.DirectoryProvider;
import ow.directory.MultiValueDirectory;
import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.MessagingAddress;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;

/**
 * An implementation of DHT service.
 * This implementation is not distributed and to be called Centralized Hash Table (CHT) :)
 */
public final class CHTImpl<V extends Serializable> implements DHT<V> {
	final static Logger logger = Logger.getLogger("dht");

	private final static String GLOBAL_DB_NAME = "global";

	private DHTConfiguration config;
	private RoutingAlgorithmConfiguration algoConfig;
	private IDAddressPair selfIDAddressPair;
	private MultiValueDirectory<ID,ValueInfo<V>> globalDir;

	private ByteArray hashedSecretForPut;
	private long ttlForPut;

	public CHTImpl(short applicationID, short applicationVersion,
			DHTConfiguration config, ID selfID /* possibly null */)
				throws Exception {
		this.init(config, selfID);
	}

	public CHTImpl(DHTConfiguration config, RoutingService routingSvc)
			throws Exception {
		this.init(config,
			(routingSvc != null ? routingSvc.getSelfIDAddressPair().getID() : null));
	}

	private void init(DHTConfiguration config, ID selfID /* will be null */)
			throws Exception {
		this.config = config;
		this.hashedSecretForPut = null;
		this.ttlForPut = config.getDefaultTTL();

		this.algoConfig = new RoutingAlgorithmConfiguration();

		if (selfID != null)
			this.selfIDAddressPair = IDAddressPair.getIDAddressPair(selfID, null);
		else
			this.selfIDAddressPair = IDAddressPair.getIDAddressPair(20, null);

		// prepare working directory
		File workingDirFile = new File(config.getWorkingDirectory());
		workingDirFile.mkdirs();

		// initialize directories
		DirectoryProvider dirProvider = DirectoryFactory.getProvider(config.getDirectoryType());
		DirectoryConfiguration dirConfig = DirectoryConfiguration.getDefaultConfiguration();

		if (config.getDoExpire())
			dirConfig.setExpirationTime(config.getDefaultTTL());
		else
			dirConfig.setExpirationTime(-1L);

		this.globalDir = dirProvider.openMultiValueDirectory(
				ID.class, config.getValueClass(), config.getWorkingDirectory(), GLOBAL_DB_NAME,
				dirConfig);

		// initialize last routing result
		RoutingHop[] route = new RoutingHop[1];
		route[0] = RoutingHop.newInstance(selfIDAddressPair);
		IDAddressPair[] respNodes = new IDAddressPair[1];
		respNodes[0] = selfIDAddressPair;
		this.lastRoutingResults = new RoutingResult[1];
		this.lastRoutingResults[0] = new RoutingResult(route, respNodes);
	}

	public MessagingAddress joinOverlay(String hostAndPort, int defaultPort) throws UnknownHostException {
		return joinOverlay();
	}
	public MessagingAddress joinOverlay(String hostAndPort) throws UnknownHostException {
		return joinOverlay();
	}
	private MessagingAddress joinOverlay() {
		this.lastKeys[0] = this.selfIDAddressPair.getID();

		return null;
	}

	public void clearRoutingTable() {
		this.lastKeys[0] = null;
	}

	public void clearDHTState() {}

	public Set<ValueInfo<V>> put(ID key, V value) {
		V[] values = (V[])new Object/*V*/[1];
		values[0] = value;

		return this.put(key, values);
	}
	public Set<ValueInfo<V>> put(ID key, V[] values) {
		Set<ValueInfo<V>> existedValue = null;

		try {
			existedValue = this.globalDir.get(key);

			for (V v: values) {
				this.globalDir.put(key, new ValueInfo<V>(v, this.ttlForPut, this.hashedSecretForPut), this.ttlForPut);
			}
		}
		catch (Exception e) {
			// NOTREACHED
			logger.log(Level.WARNING, "An Exception thrown by Directory#put().", e);
		}

		this.lastKeys[0] = key;

		return existedValue;
	}
	public Set<ValueInfo<V>>[] put(DHT.PutRequest<V>[] requests) {
		Set<ValueInfo<V>>[] results = new Set/*<ValueInfo<V>>*/[requests.length];

		for (int i = 0; i < requests.length; i++) {
			DHT.PutRequest<V> req = requests[i];
			results[i] = this.put(req.getKey(), req.getValues());

			if (results[i] == null) results[i] = new HashSet<ValueInfo<V>>();
		}

		return results;
	}

	public Set<ValueInfo<V>> get(ID key) {
		Set<ValueInfo<V>> results = null;
		try {
			results = this.globalDir.get(key);
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "An Exception thrown during getting.", e);
		}

		this.lastKeys[0] = key;

		return results;
	}
	public Set<ValueInfo<V>>[] get(ID[] keys) {
		Set<ValueInfo<V>>[] results = new Set/*<ValueInfo<V>>*/[keys.length];

		for (int i = 0; i < keys.length; i++){
			results[i] = this.get(keys[i]);

			if (results[i] == null) results[i] = new HashSet<ValueInfo<V>>();
		}

		return results;
	}

	public Set<ValueInfo<V>> remove(ID key, V[] values, ByteArray hashedSecret) {
		ID[] valueHash = new ID[values.length];

		for (int j = 0; j < values.length; j++) {
			try {
				valueHash[j] = ID.getSHA1BasedID(
						values[j].toString().getBytes(config.getValueEncoding()));
			}
			catch (UnsupportedEncodingException e) {
				// NOTREACHED
			}
		}

		return this.removeInternally(key, valueHash, hashedSecret);
	}
	public Set<ValueInfo<V>> remove(ID key, ID[] valueHash, ByteArray hashedSecret) {
		return this.removeInternally(key, valueHash, hashedSecret);
	}
	public Set<ValueInfo<V>> remove(ID key, ByteArray hashedSecret) {
		return this.removeInternally(key, null, hashedSecret);
	}
	public Set<ValueInfo<V>>[] remove(DHT.RemoveRequest<V>[] requests, ByteArray hashedSecret) {
		Set<ValueInfo<V>>[] results = new Set/*<ValueInfo<V>>*/[requests.length];

		for (int i = 0; i < requests.length; i++) {
			DHT.RemoveRequest<V> req = requests[i];

			ID[] valueHash;

			if (req.getValues() != null) {
				V[] values = req.getValues();
				valueHash = new ID[values.length];

				for (int j = 0; j < values.length; j++) {
					try {
						valueHash[j] = ID.getSHA1BasedID(
								values[j].toString().getBytes(config.getValueEncoding()));
					}
					catch (UnsupportedEncodingException e) {
						// NOTREACHED
					}
				}
			}
			else {
				valueHash = req.getValueHash();
			}

			results[i] = this.removeInternally(
					req.getKey(), valueHash, hashedSecret);
		}

		return results;
	}

	private Set<ValueInfo<V>> removeInternally(ID key, ID[] valueHash /* possibly null */, ByteArray hashedSecret) {
		Set<ValueInfo<V>> ret = new HashSet<ValueInfo<V>>();

		synchronized (this) {
			try {
				Set<ValueInfo<V>> existedValues = this.globalDir.get(key);

				if (existedValues != null) {
					ValueInfo<V>[] existedValueArray = new ValueInfo/*<V>*/[existedValues.size()];
					existedValues.toArray(existedValueArray);

					for (ValueInfo<V> v: existedValueArray) {
						ID h = ID.getSHA1BasedID(
								v.getValue().toString().getBytes(config.getValueEncoding()));

						if (hashedSecret.equals(v.getHashedSecret())) {
							boolean remove = false;

							if (valueHash == null)
								remove = true;
							else {
								for (ID valH: valueHash) {
									if (h.equals(valH)) remove = true;
								}
							}

							if (remove) {
								synchronized (globalDir) {
									globalDir.remove(key, v);
								}

								ret.add(v);
							}
						}
					}
				}
			}
			catch (Exception e) {
				logger.log(Level.WARNING, "An Exception thrown during getting.", e);
			}
		}

		this.lastKeys[0] = key;

		return ret;
	}

	public ByteArray setHashedSecretForPut(ByteArray hashedSecret) {
		ByteArray old = this.hashedSecretForPut;
		this.hashedSecretForPut = hashedSecret;
		return old;
	}

	public long setTTLForPut(long ttl) {
		long old = this.ttlForPut;
		this.ttlForPut = ttl;
		return old;
	}

	public void stop() {}
	public void suspend() {}
	public void resume() {}

	public Set<ID> getLocalKeys() {
		return this.globalDir.keySet();
	}

	public Set<ValueInfo<V>> getLocalValues(ID key) {
		return this.getValuesInternally(key);
	}

	public Set<ID> getGlobalKeys() {
		return this.globalDir.keySet();
	}

	public Set<ValueInfo<V>> getGlobalValues(ID key) {
		return this.getValuesInternally(key);
	}

	private Set<ValueInfo<V>> getValuesInternally(ID key) {
		Set<ValueInfo<V>> ret = null;
		try {
			ret = this.globalDir.get(key);
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "An Exception thrown when retrieve from the globalDir.", e);
		}

		return ret;
	}

	public RoutingService getRoutingService() {
		return null;
	}

	public DHTConfiguration getConfiguration() {
		return this.config;
	}

	public RoutingAlgorithmConfiguration getRoutingAlgorithmConfiguration() {
		return this.algoConfig;
	}

	public IDAddressPair getSelfIDAddressPair() {
		return this.selfIDAddressPair;
	}

	public void setStatCollectorAddress(String host, int port) throws UnknownHostException {}

	private ID[] lastKeys = new ID[1];
	public ID[] getLastKeys() { return this.lastKeys; }

	private RoutingResult[] lastRoutingResults;
	public RoutingResult[] getLastRoutingResults() { return this.lastRoutingResults; }

	public String getRoutingTableString(int verboseLevel) {
		return "";
	}
}

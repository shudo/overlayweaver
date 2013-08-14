/*
 * Copyright 2006-2010 National Institute of Advanced Industrial Science
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

package ow.directory.expiration;

import java.io.Serializable;
import java.util.Set;

import ow.directory.SingleValueDirectory;
import ow.util.Timer;

public class ExpiringSingleValueDirectory<K,V> extends AbstractExpiringDirectory<K,V>
		implements SingleValueDirectory<K,V>, Serializable {
	private SingleValueDirectory<K,ExpiringValue<V>> internalDirectory;

	public ExpiringSingleValueDirectory(SingleValueDirectory<K,ExpiringValue<V>> dir,
			long expirationTime) {
		super(dir, expirationTime);
		this.internalDirectory = dir;
	}

	public V put(K key, V value, long ttl) throws Exception {
		if (value instanceof Expirable) {
			((Expirable)value).setTTL(ttl);
		}

		ExpiringValue<V> entry = new ExpiringValue<V>(value, ttl);
		long expiringTime = entry.getExpiringTime();

		synchronized (this) {
			entry = this.internalDirectory.put(key, entry);

			super.initExpiringTask(expiringTime + 100L);
		}

		V ret = null;
		if (entry != null) {
			ret = entry.getValue();

			ttl = entry.getExpiringTime() - Timer.currentTimeMillis();
			if (ttl < 0L) ttl = 0L;
			if (ret instanceof Expirable) {
				((Expirable)ret).setTTL(ttl);
			}
		}

		return ret;
	}

	public V put(K key, V value) throws Exception {
		long ttl = 0L;
		if (value instanceof Expirable) {
			ttl = ((Expirable)value).getTTL();
		}

		if (ttl <= 0L) {
			ttl = super.defaultTTL;
		}

		return this.put(key, value, ttl);
	}

	private V getAndRemove(K key, boolean remove) throws Exception {
		ExpiringValue<V> entry;
		if (remove) {
			synchronized (this) {
				entry = this.internalDirectory.remove(key);

				if (this.internalDirectory.isEmpty()) {
					super.stopExpiringTask();
				}
			}
		}
		else
			entry = this.internalDirectory.get(key);

		V ret = null;
		if (entry != null) {
			ret = entry.getValue();

			int ttl = (int)(entry.getExpiringTime() - Timer.currentTimeMillis());
			if (ttl < 0) ttl = 0;
			if (ret instanceof Expirable) {
				((Expirable)ret).setTTL(ttl);
			}
		}

		return ret;
	}

	public V get(K key) throws Exception {
		return getAndRemove(key, false);
	}

	public V remove(K key) throws Exception {
		return getAndRemove(key, true);
	}

	public boolean isEmpty() {
		return this.internalDirectory.isEmpty();
	}

	public Set<K> keySet() {
		return this.internalDirectory.keySet();
	}

	public void clear() {
		synchronized (this) {
			super.stopExpiringTask();

			this.internalDirectory.clear();
		}
	}

	public void close() {
		super.close();

		this.internalDirectory.close();
	}
}

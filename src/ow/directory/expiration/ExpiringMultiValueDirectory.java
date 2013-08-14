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
import java.util.HashSet;
import java.util.Set;

import ow.directory.MultiValueDirectory;
import ow.util.Timer;

public class ExpiringMultiValueDirectory<K,V> extends AbstractExpiringDirectory<K,V>
		implements MultiValueDirectory<K,V>, Serializable {
	private MultiValueDirectory<K,ExpiringValue<V>> dir;

	public ExpiringMultiValueDirectory(MultiValueDirectory<K,ExpiringValue<V>> dir,
			long defaultTTL) {
		super(dir, defaultTTL);
		this.dir = dir;
	}

	public V put(K key, V value, long ttl) throws Exception {
		if (value instanceof Expirable) {
			((Expirable)value).setTTL(ttl);
		}

		ExpiringValue<V> entry = new ExpiringValue<V>(value, ttl);
		long expiringTime = entry.getExpiringTime();

		synchronized (this) {
			entry = this.dir.put(key, entry);

			super.initExpiringTask(expiringTime + 100L);
		}

		V ret = null;
		if (entry != null) {
			ret = entry.getValue();

			ttl = (int)(entry.getExpiringTime() - Timer.currentTimeMillis());
			if (ttl < 0) ttl = 0;
			if (value instanceof Expirable) {
				((Expirable)value).setTTL(ttl);
			}
		}

		return ret;
	}

	public V put(K key, V value) throws Exception {
		long ttl = 0L;
		if (value instanceof Expirable) {
			ttl = ((Expirable)value).getTTL();
		}

		if (ttl <= 0) {
			ttl = super.defaultTTL;
		}

		return this.put(key, value, ttl);
	}

	private Set<V> getAndRemove(K key, boolean remove) throws Exception {
		Set<ExpiringValue<V>> c;
		if (remove) {
			synchronized (this) {
				c = this.dir.remove(key);

				if (this.dir.isEmpty()) {
					super.stopExpiringTask();
				}
			}
		}
		else
			c = this.dir.get(key);

		Set<V> ret = null;
		if (c != null) {
			ret = new HashSet<V>();

			long curTime = Timer.currentTimeMillis();
			for (ExpiringValue<V> entry: c) {
				V v = entry.getValue();

				int ttl = (int)(entry.getExpiringTime() - curTime);
				if (ttl < 0L) ttl = 0;
				if (v instanceof Expirable) {
					((Expirable)v).setTTL(ttl);
				}

				ret.add(v);
			}
		}

		return ret;
	}

	public Set<V> get(K key) throws Exception {
		return getAndRemove(key, false);
	}

	public Set<V> remove(K key) throws Exception {
		return getAndRemove(key, true);
	}

	public V remove(K key, V value) throws Exception {
		ExpiringValue<V> entry = new ExpiringValue<V>(value, super.defaultTTL);
		synchronized (this) {
			entry = this.dir.remove(key, entry);

			if (this.dir.isEmpty()) {
				super.stopExpiringTask();
			}
		}

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

	public boolean isEmpty() {
		return this.dir.isEmpty();
	}

	public Set<K> keySet() {
		return this.dir.keySet();
	}

	public void clear() {
		synchronized (this) {
			super.stopExpiringTask();

			this.dir.clear();
		}
	}

	public void close() {
		super.close();

		this.dir.close();
	}
}

/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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

package ow.directory.inmemory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ow.directory.DirectoryConfiguration;
import ow.directory.MultiValueDirectory;
import ow.directory.SingleValueDirectory;

public final class MultipleValueHashDirectory<K,V> implements MultiValueDirectory<K,V> {
	SingleValueDirectory<K,Map<V,V>> internalDir;
		// value of this directory should be Map, not Set
		// because it.remove() has to return a value.

	MultipleValueHashDirectory(Class typeK, Class typeV, String workingDir, String dbName, String dbNameOld,
			DirectoryConfiguration config, long syncInterval) throws Exception {
		this.internalDir = new SingleValueHashDirectory<K,Map<V,V>>(typeK, null /* Set<V>.class */, workingDir, dbName, dbNameOld,
				config, syncInterval);
	}

	public Set<V> get(K key) throws Exception {
		Map<V,V> map = this.internalDir.get(key);

		Set<V> ret = null;
		if (map != null) {
			ret = new HashSet<V>();
			ret.addAll(map.keySet());
			// instantiates a new Set which is Serializable.
			// because map.keySet() returns HashMap$KeySet and it is not Serializable.
		}

		return ret;
	}

	public V put(K key, V value) throws Exception {
		V ret = null;

		synchronized (this) {
			Map<V,V> map = this.internalDir.get(key);
			if (map == null) {
				map = new HashMap<V,V>();
				this.internalDir.put(key, map);
			}
			else {
				// reput to kick off LRU expiration
				// though essentially this reputting is not required
				this.internalDir.put(key, map);
			}

			// remove the old value
			ret = map.remove(value);
			// put the new value
			map.put(value, value);
		}

		return ret;
	}

	/** For compatibility with ExpiringMultiValueDirectory. */
	public V put(K key, V value, long ttl) throws Exception {
		// ignore ttl
		return this.put(key, value);
	}

	public Set<V> remove(K key) throws Exception {
		Map<V,V> map = null;
		synchronized (this) {
			map = this.internalDir.remove(key);
		}

		Set<V> ret = null;
		if (map != null)
			ret = map.keySet();
		return ret;
	}

	public V remove(K key, V value) throws Exception {
		V ret = null;

		synchronized (this) {
			Map<V,V> map = this.internalDir.get(key);

			if (map != null) {
				ret = map.remove(value);

				if (map.isEmpty()) {
					this.internalDir.remove(key);
				}
			}
		}

		return ret;
	}

	public boolean isEmpty() {
		return this.internalDir.isEmpty();
	}

	public Set<K> keySet() {
		return this.internalDir.keySet();
	}

	public Set<Map.Entry<K,V>> entrySet() {
		Set<Map.Entry<K,V>> result = new HashSet<Map.Entry<K,V>>();

		synchronized (this) {
			for (Map.Entry<K,Map<V,V>> anEntry: this.internalDir.entrySet()) {
				for (V value: anEntry.getValue().keySet()) {
					result.add(new MultiValueHashDirectoryEntry(anEntry.getKey(), value));
				}
			}
		}

		return result;
	}

	public void clear() {
		this.internalDir.clear();
	}

	public void close() {
		this.internalDir.close();

		return;
	}

	public Iterator<Entry<K,V>> iterator() {
		return new MultiValueHashDirectoryIterator();
	}

	private class MultiValueHashDirectoryIterator implements Iterator<Entry<K,V>> {
		Iterator<Entry<K,Map<V,V>>> it0;
		Entry<K,Map<V,V>> e0;
		Iterator<V> it1;

		MultiValueHashDirectoryIterator() {
			this.it0 = MultipleValueHashDirectory.this.internalDir.iterator();
			if (it0.hasNext()) {
				this.e0 = it0.next();
				this.it1 = e0.getValue().keySet().iterator();
			}
			else
				this.it1 = null;
		}

		public boolean hasNext() {
			if (this.it1 == null)		// there is no entry
				return false;

			if (this.it1.hasNext()) {
				return true;
			}
			else {
				if (it0.hasNext()) {
					this.e0 = it0.next();
					this.it1 = e0.getValue().keySet().iterator();
				}
				else
					return false;

				return it1.hasNext();
			}
		}

		public Entry<K,V> next() {
			K key = e0.getKey();
			V value = it1.next();
			return new MultiValueHashDirectoryEntry(this, key, value);
		}

		public void remove() {
			this.it1.remove();

			if (e0.getValue().isEmpty()) {
				it0.remove();

				if (it0.hasNext()) {
					this.e0 = it0.next();
					this.it1 = e0.getValue().keySet().iterator();
				}
				else
					it1 = null;
			}
		}
	}

	private class MultiValueHashDirectoryEntry implements Entry<K,V> {
		K k;  V v;
		MultiValueHashDirectoryIterator it = null;

		MultiValueHashDirectoryEntry(K k, V v) {
			this.k = k; this.v = v;
		}
		MultiValueHashDirectoryEntry(MultiValueHashDirectoryIterator it, K k, V v) {
			this.it = it;
			this.k = k; this.v = v;
		}

		public K getKey() { return k; }
		public V getValue() { return v; }
		public V setValue(V newValue) {
			V ret = this.v;
			this.v = newValue;
			if (this.it != null) {
				it.remove();
				it.e0.getValue().put(newValue, newValue);
			}
			return ret;
		}

		public String toString() {
			return this.k + "=" + this.v;
		}
	}
}

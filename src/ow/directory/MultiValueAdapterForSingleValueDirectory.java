/*
 * Copyright 2008-2009 Kazuyuki Shudo, and contributors.
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

/**
 * An adapter which converts a {@link SingleValueDirectory SingleValueDirectory}
 * to a {@link MultiValueDirectory MultiValueDirectory}.
 */
public final class MultiValueAdapterForSingleValueDirectory<K,V> implements MultiValueDirectory<K,V> {
	private final SingleValueDirectory<K,V> dir;

	public MultiValueAdapterForSingleValueDirectory(SingleValueDirectory<K,V> dir) {
		this.dir = dir;
	}

	public Set<V> get(K key) throws Exception {
		V value = this.dir.get(key);

		Set<V> ret = null;
		if (value != null) {
			ret = new HashSet<V>();
			ret.add(value);
		}

		return ret;
	}

	public V remove(K key, V value) throws Exception {
		if (value != null && value.equals(this.dir.get(key)))
			return this.dir.remove(key);
		else
			return null;
	}

	public Set<V> remove(K key) throws Exception {
		V value = this.dir.remove(key);

		Set<V> ret = null;
		if (value != null) {
			ret = new HashSet<V>();
			ret.add(value);
		}

		return ret;
	}

	//
	// call directly the corresponding methods provided by the SingleValueDirectory
	//

	public V put(K key, V value) throws Exception { return this.dir.put(key, value); }
	public V put(K key, V value, long ttl) throws Exception { return this.dir.put(key, value, ttl); }
	public boolean isEmpty() { return this.dir.isEmpty(); }
	public Set<K> keySet() { return this.dir.keySet(); }
	public Set<Entry<K, V>> entrySet() { return this.dir.entrySet(); }
	public void clear() { this.dir.clear(); }
	public void close() { this.dir.close(); }
	public Iterator<Entry<K, V>> iterator() { return this.dir.iterator(); }
}

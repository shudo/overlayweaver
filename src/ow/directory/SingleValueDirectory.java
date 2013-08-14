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

package ow.directory;

import java.util.Map;
import java.util.Set;

/**
 *	A directory that maps keys to values.
 */
public interface SingleValueDirectory<K,V> extends Iterable<Map.Entry<K,V>> {
	V get(K key) throws Exception;
	V put(K key, V value) throws Exception;
	V remove(K key) throws Exception;
	boolean isEmpty();
	Set<K> keySet();
	Set<Map.Entry<K,V>> entrySet();
	void clear();
	void close();

	/** For compatibility with ExpiringDirectory. */
	V put(K key, V value, long ttl) throws Exception;
}

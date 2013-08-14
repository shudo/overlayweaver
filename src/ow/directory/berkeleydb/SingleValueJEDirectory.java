/*
 * Copyright 2006,2008-2009 National Institute of Advanced Industrial Science
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

package ow.directory.berkeleydb;

import ow.directory.SingleValueDirectory;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.Transaction;

/**
 * A directory that maps a key to a value.
 * This directory (hash table) does not allow multiple values for a key. 
 *
 * @param <K> Type of keys.
 * @param <V> Type of values.
 */
public class SingleValueJEDirectory<K,V> extends AbstractJEDirectory<K,V> implements SingleValueDirectory<K,V> {
	protected SingleValueJEDirectory(Class<K> typeK, Class<V> typeV, Environment env, String dbName) throws Exception {
		super(typeK, typeV, env, dbName, false);
	}

	public V get(K key) throws DatabaseException {
		V ret;

		Transaction txn = super.env.beginTransaction(null, null);
		ret = super.map.get(key);
		txn.commit();

		return ret;
	}

	public V put(K key, V value) throws Exception {
		V ret;

		Transaction txn = super.env.beginTransaction(null, null);
		ret = super.map.put(key, value);
		txn.commit();

		return ret;
	}

	/** For compatibility with ExpiringDirectory. */
	public V put(K key, V value, long ttl) throws Exception {
		// ignore ttl
		return this.put(key, value);
	}

	public V remove(K key) throws Exception {
		V ret;

		Transaction txn = super.env.beginTransaction(null, null);
		ret = super.map.remove(key);
		txn.commit();

		return ret;
	}
}

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

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import ow.directory.MultiValueDirectory;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

public class MultiValueJEDirectory<K,V> extends AbstractJEDirectory<K,V> implements MultiValueDirectory<K,V> {
	protected MultiValueJEDirectory(Class<K> typeK, Class<V> typeV, Environment env, String dbName) throws Exception {
		super(typeK, typeV, env, dbName, true);
	}

	public Set<V> get(K key) throws DatabaseException {
		return getAndRemove(key, false);
	}

	public V put(K key, V value) throws Exception {
		DatabaseEntry keyEntry = new DatabaseEntry();
		DatabaseEntry dataEntry = new DatabaseEntry();
		keyBinding.objectToEntry(key, keyEntry);
		dataBinding.objectToEntry(value, dataEntry);

		V ret = null;

		Transaction txn = env.beginTransaction(null, null);
		Cursor cursor = db.openCursor(txn, null);

		if ((cursor.getSearchKey(keyEntry, dataEntry, LockMode.DEFAULT)) == OperationStatus.SUCCESS) {
			V v = (V)dataBinding.entryToObject(dataEntry);
			if (value.equals(v)) {		// found
				ret = v;
				cursor.delete();		// remove here. and put later.
			}
			else {
				while ((cursor.getNextDup(keyEntry, dataEntry, LockMode.DEFAULT)) == OperationStatus.SUCCESS) {
					v = (V)dataBinding.entryToObject(dataEntry);
					if (value.equals(v)) {	// found
						ret = v;
						cursor.delete();	// remove here. and put later.
						break;
					}
				}
			}
		}

		// put
		keyBinding.objectToEntry(key, keyEntry);
		dataBinding.objectToEntry(value, dataEntry);

		if ((cursor.put(keyEntry, dataEntry)) != OperationStatus.SUCCESS) {
			// NOTREACHED
			String msg = "Could not put: " + key + ", " + value;
			logger.log(Level.SEVERE, msg);
			//throw new DatabaseException(msg);
		}

		cursor.close();
		txn.commit();

		return ret;
	}

	/** For compatibility with ExpiringMultiValueDirectory. */
	public V put(K key, V value, long ttl) throws Exception {
		// ignore ttl
		return this.put(key, value);
	}

	public Set<V> remove(K key) throws DatabaseException {
		return getAndRemove(key, true);
	}

	public V remove(K key, V value) throws Exception {
		DatabaseEntry keyEntry = new DatabaseEntry();
		DatabaseEntry foundEntry = new DatabaseEntry();
		keyBinding.objectToEntry(key, keyEntry);

		V ret = null;

		Transaction txn = env.beginTransaction(null, null);
		Cursor cursor = db.openCursor(txn, null);

		if ((cursor.getSearchKey(keyEntry, foundEntry, LockMode.DEFAULT)) == OperationStatus.SUCCESS) {
			do {
				V v = (V)dataBinding.entryToObject(foundEntry);
				if (value.equals(v)) {
					ret = v;
					cursor.delete();
					break;
				}
			}
			while ((cursor.getNextDup(keyEntry, foundEntry, LockMode.DEFAULT)) == OperationStatus.SUCCESS);
		}
		// Cannot use Cursor#getSearchBoth() because it compare values byte-wise without Object#equal(). 

		cursor.close();
		txn.commit();

		return ret;
	}

	private Set<V> getAndRemove(K key, boolean remove) throws DatabaseException {
		DatabaseEntry searchKey = new DatabaseEntry();
		keyBinding.objectToEntry(key, searchKey);
		DatabaseEntry foundKey = new DatabaseEntry();
		DatabaseEntry foundData = new DatabaseEntry();

		Transaction txn = env.beginTransaction(null, null);
		Cursor cursor = db.openCursor(txn, null);

		Set<V> s = null;
		if ((cursor.getSearchKey(searchKey, foundData, LockMode.DEFAULT)) == OperationStatus.SUCCESS) {
			s = new HashSet<V>();
			s.add((V)dataBinding.entryToObject(foundData));

			while ((cursor.getNextDup(foundKey, foundData, LockMode.DEFAULT)) == OperationStatus.SUCCESS) {
//				K k = (K)keyBinding.entryToObject(foundKey);
//				if (!key.equals(k)) {
//					break;
//				}

				s.add((V)dataBinding.entryToObject(foundData));
			}
		}

		// remove all entries associated with the given key
		if (remove) {
			db.delete(txn, searchKey);
				// does not check the result of this operation.
		}

		cursor.close();
		txn.commit();

		return s;
	}
}

/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;

import com.sleepycat.bind.ByteArrayBinding;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.ClassCatalog;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

public abstract class AbstractJEDirectory<K,V> {
	final static Logger logger = Logger.getLogger("directory");

	private final String dbName;
	Environment env;
	Database db;
	SortedMap<K,V> map;
	EntryBinding<K> keyBinding;
	EntryBinding<V> dataBinding;

	private ClassCatalog catalog;
	private boolean catalogPrepared = false;

	protected AbstractJEDirectory(Class<K> typeK, Class<V> typeV, Environment env, String dbName,
			boolean allowMultipleValues) throws Exception {
		this.env = env;

		// prepare DatabaseConfig
		DatabaseConfig dbConfig = new DatabaseConfig();
		dbConfig.setTransactional(true);
		dbConfig.setAllowCreate(BerkeleyDBProvider.ALLOW_CREATE);
		if (allowMultipleValues)
			dbConfig.setSortedDuplicates(true);

		// prepare bindings
		if (true) {
			this.keyBinding = getBinding(typeK);
			this.dataBinding = getBinding(typeV);
		}
		else {
			this.keyBinding = (EntryBinding<K>)new IDBinding();
			this.dataBinding = (EntryBinding<V>)new StringBinding();
		}

		// open DB
		Transaction txn = env.beginTransaction(null, null);

		this.dbName = dbName;
		this.db = env.openDatabase(null, this.dbName, dbConfig);

		txn.commit();

		this.map = new StoredSortedMap<K,V>(db, keyBinding, dataBinding, true);
	}

	/**
	 * The finalizer.
	 */
	protected void finalize() throws Throwable {
		this.close();
	}

	public boolean isEmpty() {
		long count = 0L;

		try {
			count = this.db.count();
		}
		catch (DatabaseException e) { /* ignore */ }

		return (count == 0L);
	}

	public Set<K> keySet() {
		return this.map.keySet();
	}

	public void clear() {
		this.map.clear();
	}

	public void close() {
		try {
			if (this.catalog != null) {
				this.catalog.close();  this.catalog = null;
			}

			if (this.db != null) {
				this.db.close();  this.db = null;
			}
		}
		catch (DatabaseException e) {
			logger.log(Level.WARNING, e.getMessage());
		}
	}

	public Set<Map.Entry<K,V>> entrySet() {
		return this.map.entrySet();
/*
		Set<Map.Entry<K,V>> result = new HashSet<Map.Entry<K,V>>();

		DatabaseEntry keyEntry = new DatabaseEntry();
		DatabaseEntry dataEntry = new DatabaseEntry();

		Transaction txn = env.beginTransaction(null, null);
		Cursor cursor = this.db.openCursor(txn, null);

		OperationStatus status = cursor.getFirst(keyEntry, dataEntry, LockMode.DEFAULT);
		while (status == OperationStatus.SUCCESS) {
			K key = (K)this.keyBinding.entryToObject(keyEntry);
			V value = (V)this.dataBinding.entryToObject(dataEntry);

			result.add(new JEDirectoryEntry(key, value));
		}

		cursor.close();
		txn.commit();

		return result;
*/
	}

	public Iterator<Map.Entry<K,V>> iterator() {
		return new JEDirectoryIterator(); 
	}

	private class JEDirectoryIterator implements Iterator<Map.Entry<K,V>> {
		Transaction txn = null;
		Cursor cursor = null;
		OperationStatus lastStatus;
		DatabaseEntry keyEntry = new DatabaseEntry();
		DatabaseEntry dataEntry = new DatabaseEntry();

		private JEDirectoryIterator() {
			try {
				this.txn = env.beginTransaction(null, null);
				this.cursor = db.openCursor(this.txn, null);
			}
			catch (DatabaseException e) {
				logger.log(Level.WARNING, "Could not begin transaction.");
			}
		}

		protected void finalize() throws Throwable {
			try {
				if (this.cursor != null)
					this.cursor.close();
					this.cursor = null;	// prevent re-invocation of close().
			}
			catch (DatabaseException e) {
				logger.log(Level.WARNING, "Cursor#close() failed." , e);
			}
			try {
				if (this.txn != null)
					this.txn.commit();
					this.txn = null;		// prevent re-invocatoin of commit().
			}
			catch (DatabaseException e) {
				logger.log(Level.WARNING, "Transaction#commit() failed." , e);
			}
		}

		public boolean hasNext() {
			// proceed the cursor
			try {
				lastStatus = this.cursor.getNext(keyEntry, dataEntry, LockMode.DEFAULT);
			}
			catch (DatabaseException e) {
				logger.log(Level.WARNING, "Cursor#getNext() throws a DatabaseException.", e);
			}

			if (lastStatus == OperationStatus.SUCCESS)
				return true;
			else {
				try {
					this.finalize();
				}
				catch (Throwable e) {}
				return false;
			}
		}

		public Map.Entry<K,V> next() {
			K key = (K)keyBinding.entryToObject(this.keyEntry);
			V value = (V)dataBinding.entryToObject(this.dataEntry);

			return new JEDirectoryEntry(this, key, value);
		}

		public void remove() {
			try {
				this.cursor.delete();
			}
			catch (DatabaseException e) {
				logger.log(Level.WARNING, "Cursor#delete() or getNext() throws a DatabaseException.", e);
			}
		}

	}

	private class JEDirectoryEntry implements Map.Entry<K,V> {
		K key;  V value;
		JEDirectoryIterator it = null;

		JEDirectoryEntry(K key, V value) {
			this.key = key; this.value = value;
		}
		JEDirectoryEntry(JEDirectoryIterator it, K key, V value) {
			this.it = it;
			this.key = key; this.value = value;
		}

		public K getKey() { return this.key; }
		public V getValue() { return this.value; }

		public V setValue(V value) {
			V ret = this.value;
			this.value = value;

			if (this.it != null) {
				DatabaseEntry newDataEntry = new DatabaseEntry();
				dataBinding.objectToEntry(value, newDataEntry);

				try {
					it.cursor.putCurrent(newDataEntry);
				}
				catch (DatabaseException e) {
					throw new IllegalArgumentException(e);
				}
			}

			return ret;
		}

		public String toString() {
			return this.key + "=" + this.value;
		}
	}

	private <E> EntryBinding<E> getBinding(Class<E> type) throws DatabaseException {
		EntryBinding<E> binding = null;

		if (type.equals(ID.class)) {
			return (EntryBinding<E>)new IDBinding();
		}
		else if (type.equals(String.class) || type.equals(Character.class) || type.equals(Boolean.class) ||
				type.equals(Byte.class) || type.equals(Short.class) || type.equals(Integer.class) ||
				type.equals(Long.class) || type.equals(Float.class) || type.equals(Double.class)) {
			binding = TupleBinding.getPrimitiveBinding(type);
		}
		else if (type.equals(byte[].class)) {
			binding = (EntryBinding<E>)new ByteArrayBinding();
		}
		else {
			synchronized (this) {
				if (!this.catalogPrepared) {
					// prepare a ClassCatalog
					DatabaseConfig dbConfig = new DatabaseConfig();
					dbConfig.setTransactional(true);
					dbConfig.setAllowCreate(BerkeleyDBProvider.ALLOW_CREATE);

					Database catalogDB = env.openDatabase(null, this.dbName + ".catalog", dbConfig);
					this.catalog = new StoredClassCatalog(catalogDB);
					this.catalogPrepared = true;
				}
			}

			binding = new SerialBinding(this.catalog, type);
		}

		return binding;
	}
}

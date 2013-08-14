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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.directory.DirectoryConfiguration;
import ow.directory.DirectoryConfiguration.HeapOverflowAction;
import ow.directory.OutOfHeapException;
import ow.directory.SingleValueDirectory;

public final class SingleValueHashDirectory<K,V> implements SingleValueDirectory<K,V> {
	private final static Logger logger = Logger.getLogger("directory");

	private final String dbName, dbNameOld;
	private long syncInterval;
	private HashMap<K,V> rawMap;
	private DirectoryConfiguration config;
	private Map<K,V> map;
	private boolean changed = false;	// if true, this map is to be synchronized
	private Synchronizer synchronizer;

	protected SingleValueHashDirectory(Class typeK, Class typeV, String workingDir, String dbName, String dbNameOld,
			DirectoryConfiguration config, long syncInterval) throws Exception {
		this.dbName = dbName;
		this.dbNameOld = dbNameOld;
		this.syncInterval = syncInterval;
		this.config = (config != null ? config : DirectoryConfiguration.getDefaultConfiguration());

		// load the saved map
		boolean loaded = false;
		if (syncInterval > 0) {
			if (!workingDir.endsWith(File.separator)) {
				workingDir += File.separator;
			}
			File dbFile = new File(workingDir + this.dbName);
			if (dbFile.exists()) {
				ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dbFile));
				this.config = (DirectoryConfiguration)ois.readObject();
				this.rawMap = (HashMap<K,V>)ois.readObject();
				ois.close();

				loaded = true;
			}
		}

		if (!loaded) {
			if (config.heapOverflowAction == HeapOverflowAction.LRU) {
				this.rawMap = new LRUExpiringMap(this.config.getRequiredFreeHeapToPut());
			}
			else {
				this.rawMap = new HashMap<K,V>();
			}
		}

		this.map = Collections.synchronizedMap(this.rawMap);

		// start a synchronizing thread
		if (this.syncInterval > 0) {
			this.synchronizer = new Synchronizer(
					this.syncInterval,
					workingDir + this.dbName,
					workingDir + this.dbNameOld);

			Thread t = new Thread(this.synchronizer);
			t.setDaemon(true);
			t.setName("Map synchronizer");
			t.start();
		}
	}

	private static class LRUExpiringMap extends LinkedHashMap {
		// Note: This class is to be static.
		// Otherwise SingleValueHashDirectory instance is also serialized.

		private final long requiredFreeHeap;
		private LRUExpiringMap(long reqFreeHeap) { this.requiredFreeHeap = reqFreeHeap; }

		protected boolean removeEldestEntry(Map.Entry eldest) {
			Runtime r = Runtime.getRuntime();
			if (r.freeMemory() < this.requiredFreeHeap) {
				System.gc();
				if (r.freeMemory() < this.requiredFreeHeap) {
					// out of heap
					// kick off LRU expiration
					return true;
				}
			}

			return false;
		}
	}

	public V get(K key) {
		return this.map.get(key);
	}

	public V put(K key, V value) throws OutOfHeapException {
		HeapOverflowAction a = this.config.getHeapOverflowAction();
		if (a == HeapOverflowAction.IGNORE
				|| a == HeapOverflowAction.THROW_AN_OUT_OF_HEAP_EXCEPTION) {
			Runtime r = Runtime.getRuntime();

			if (r.freeMemory() < this.config.getRequiredFreeHeapToPut()) {
				System.gc();
				if (r.freeMemory() < this.config.getRequiredFreeHeapToPut()) {
					// out of heap
					if (a == HeapOverflowAction.THROW_AN_OUT_OF_HEAP_EXCEPTION) {
						// throw an OutOfHeapException
						throw new OutOfHeapException("Remaining heap: " + Runtime.getRuntime().freeMemory() + "key: " + key);
					}
					else {
						// ignore
						return null;
					}
				}
			}
		}

		V ret = this.map.put(key, value);
		this.changed = true;

		return ret;
	}

	/** For compatibility with ExpiringDirectory. */
	public V put(K key, V value, long ttl) throws OutOfHeapException {
		// ignore ttl
		return this.put(key, value);
	}

	public V remove(K key) {
		V ret = this.map.remove(key);
		this.changed = true;

		return ret;
	}

	public boolean isEmpty() {
		return this.map.isEmpty();
	}

	public Set<K> keySet() {
		return this.map.keySet();
	}

	public Set<Map.Entry<K,V>> entrySet() {
		return this.map.entrySet();
	}

	public void clear() {
		this.map.clear();
		this.changed = true;
	}

	public void close() {
		if (this.synchronizer != null)
			this.synchronizer.sync();
	}

	public Iterator<Map.Entry<K,V>> iterator() {
		return this.map.entrySet().iterator();
	}

	private class Synchronizer implements Runnable {
		private long syncInterval;
		private File file;
		private File oldfile;
		private File tmpfile;

		Synchronizer(long syncInterval, String filename, String oldfilename) {
			this.syncInterval = syncInterval;
			this.file = new File(filename);
			this.oldfile = new File(oldfilename);
			this.tmpfile = new File(filename + ".tmp"); 
		}

		public void run() {
			while (true) {
				try {
					Thread.sleep(this.syncInterval);
				}
				catch (InterruptedException e) {
					logger.log(Level.INFO, "A Synchronized interrupted.", e);
				}

				sync();
			}	// while (true)
		}

		private synchronized void sync() {
			if (changed) {
				ObjectOutputStream oos = null;
				try {
					// write to a temporary file
					oos = new ObjectOutputStream(new FileOutputStream(tmpfile));
					synchronized (map) {	// lock the map
						oos.writeObject(config);
						oos.writeObject(rawMap);
						oos.close();
					}

					// back up
					file.renameTo(oldfile);

					// rename the temporary file
					tmpfile.renameTo(file);

					changed = false;
				}
				catch (IOException e) {
					logger.log(Level.WARNING, "Could not open, write or rename.", e);
				}
				finally {
					try {
						if (oos != null) oos.close();
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "Could not close file streams.", e);
					}
				}
			}	// if (changed)
		}
	}
}

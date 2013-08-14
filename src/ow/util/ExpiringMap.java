/*
 * Copyright 2006-2007,2012 Kazuyuki Shudo, and contributors.
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

package ow.util;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

public final class ExpiringMap<K,V> {
	final static Logger logger = Logger.getLogger("util");

	private final long expiration;

	private final Map<K,V> internalMap = new HashMap<K,V>();
	private final SortedMap<Long,K> expirationMap = new TreeMap<Long,K>();
	private Thread expiringThread = null;

	/**
	 * The initializer.
	 * @param expiration Expiration duration in millisecond.
	 */
	public ExpiringMap(long expiration) {
		this.expiration = expiration;
	}

	public void put(final K key, final V value) {
		synchronized (this.internalMap) {
			this.internalMap.put(key, value);
			this.expirationMap.put(System.currentTimeMillis() + this.expiration, key);
		}

		synchronized (this.expirationMap) {
			if (this.expiringThread != null) {
				this.expiringThread.interrupt();
			}
			else {
				// start the expiring thread
				this.expiringThread = new Thread(new Expirer());
				this.expiringThread.setName("Expirer in an ExpiringMap");
				this.expiringThread.setDaemon(true);
				this.expiringThread.start();
			}
		}
	}

	public V get(K key) {
		synchronized (this.internalMap) {
			return this.internalMap.get(key);
		}
	}

	public void clear() {
		synchronized (this.internalMap) {
			this.internalMap.clear();
			this.expirationMap.clear();
		}

		Thread t = this.expiringThread;
		if (t != null) t.interrupt();
	}

	private class Expirer implements Runnable {
		public void run() {
			while (true) {
				long expirationTime;
				K key;

				try {
					synchronized (ExpiringMap.this.internalMap) {
						expirationTime = ExpiringMap.this.expirationMap.firstKey();
						key = ExpiringMap.this.expirationMap.get(expirationTime);
					}
				}
				catch (NoSuchElementException e) { break; }

				if (expirationTime > 0) {
					try {
						Thread.sleep(expirationTime - System.currentTimeMillis());
					}
					catch (InterruptedException e) {
						Thread.interrupted();	// clears the interrupted status
						continue;
					}
				}

				synchronized (ExpiringMap.this.internalMap) {
					ExpiringMap.this.internalMap.remove(key);
					ExpiringMap.this.expirationMap.remove(expirationTime);
				}
			}

			synchronized (ExpiringMap.this.expirationMap) {
				ExpiringMap.this.expiringThread = null;
			}
		}
	}

	/**
	 * For test.
	 */
	public static void main(String[] args) throws InterruptedException {
		ExpiringMap<String,String> s = new ExpiringMap<String,String>(3000L);

		s.put("Hello", "World.");

		for (int i = 0; i < 5; i++) {
			if (s.get("Hello") != null)
				System.out.println("Contains.");
			else
				System.out.println("Not contains.");

			Thread.sleep(1000L);
		}
	}
}

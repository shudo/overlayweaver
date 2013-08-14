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

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

public final class ExpiringSet<T> {
	final static Logger logger = Logger.getLogger("util");

	private final long expiration;

	private final Set<T> internalSet = new HashSet<T>();
	private final SortedMap<Long,T> expirationMap = new TreeMap<Long,T>();
	private Thread expiringThread = null;

	/**
	 * The initializer.
	 * @param expiration Expiration duration in millisecond.
	 */
	public ExpiringSet(long expiration) {
		this.expiration = expiration;
	}

	public void add(final T elem) {
		synchronized (this.internalSet) {
			this.internalSet.add(elem);
			this.expirationMap.put(System.currentTimeMillis() + this.expiration, elem);
		}
		
		synchronized (this.expirationMap) {
			if (this.expiringThread != null) {
				this.expiringThread.interrupt();
			}
			else {
				// start the expiring thread
				this.expiringThread = new Thread(new Expirer());
				this.expiringThread.setName("Expirer in an ExpiringSet");
				this.expiringThread.setDaemon(true);
				this.expiringThread.start();
			}
		}
	}

	public boolean contains(T elem) {
		synchronized (this.internalSet) {
			return this.internalSet.contains(elem);
		}
	}

	public void clear() {
		synchronized (this.internalSet) {
			this.internalSet.clear();
			this.expirationMap.clear();
		}

		Thread t = this.expiringThread;
		if (t != null) t.interrupt();
	}

	private class Expirer implements Runnable {
		public void run() {
			while (true) {
				long expirationTime;
				T elem;

				try {
					synchronized (ExpiringSet.this.internalSet) {
						expirationTime = ExpiringSet.this.expirationMap.firstKey();
						elem = ExpiringSet.this.expirationMap.get(expirationTime);
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

				synchronized (ExpiringSet.this.internalSet) {
					ExpiringSet.this.internalSet.remove(elem);
					ExpiringSet.this.expirationMap.remove(expirationTime);
				}
			}

			synchronized (ExpiringSet.this.expirationMap) {
				ExpiringSet.this.expiringThread = null;
			}
		}
	}

	/**
	 * For test.
	 */
	public static void main(String[] args) throws InterruptedException {
		ExpiringSet<String> s = new ExpiringSet<String>(3000L);

		s.add("Hello.");

		for (int i = 0; i < 5; i++) {
			if (s.contains("Hello."))
				System.out.println("Contains.");
			else
				System.out.println("Not contains.");

			Thread.sleep(1000L);
		}
	}
}

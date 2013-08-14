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

package ow.directory.expiration;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import ow.util.Timer;

public abstract class AbstractExpiringDirectory<K,V> implements Iterable<Map.Entry<K,V>> {
	protected long defaultTTL;
	private Iterable<Map.Entry<K,ExpiringValue<V>>> iterable;
	protected static Timer timer = Timer.getSingletonTimer();
	private TimerTask expiringTask = null;

	public AbstractExpiringDirectory(
			Iterable<Map.Entry<K,ExpiringValue<V>>> directory /* SingleValueDirectory or MultiValueDirectory */,
			long defaultTTL) {
		this.iterable = directory;
		this.defaultTTL = defaultTTL;
	}

	public abstract boolean isEmpty();	// called by ExpiringTask#run()

	protected void close() {
	}

	protected void initExpiringTask(long expiringTime) {
		synchronized (this) {
			if (this.expiringTask != null) {
				long scheduledTime = timer.getScheduledTime(this.expiringTask);
				if (expiringTime < scheduledTime) {
					// reschedule
//System.out.println("[ExpiringTask rescheduled]");
					timer.cancel(this.expiringTask);
					timer.schedule(this.expiringTask, expiringTime, true /*isDaemon*/);
				}
			}
			else {
				// create an ExpiringTask and schedule it
//System.out.println("[ExpiringTask started]");
				this.expiringTask = new ExpiringTask(this, this.iterable);
				timer.schedule(this.expiringTask, expiringTime, true /*isDaemon*/);
			}
		}
	}

	protected void stopExpiringTask() {
		synchronized (this) { 
			if (this.expiringTask != null) {
//System.out.println("[ExpiringTask stopped]");
				this.expiringTask.cancel();
				this.expiringTask = null;
			}
		}
	}

	private class ExpiringTask extends TimerTask {
		AbstractExpiringDirectory<K,V> dir;
		Iterable<Map.Entry<K,ExpiringValue<V>>> iterable;

		ExpiringTask(AbstractExpiringDirectory<K,V> dir,
				Iterable<Map.Entry<K,ExpiringValue<V>>> iterable) {
			this.dir = dir;
			this.iterable = iterable;
		}

		public void run() {
			// expire
			long currentTime = Timer.currentTimeMillis();
			long nearestExpiringTime = Long.MAX_VALUE;

			synchronized (this.dir) {
				// expecting user programs which use an Iterator over a Directory
				// to lock the Directory.

				synchronized (this.iterable) { 
					for (Iterator<Map.Entry<K,ExpiringValue<V>>> it = this.iterable.iterator(); it.hasNext(); ) {
						Map.Entry<K,ExpiringValue<V>> e = it.next();
						long expiringTime = e.getValue().getExpiringTime();
						if (expiringTime < currentTime) {
							it.remove();	// expire
						}
						else if (expiringTime < nearestExpiringTime) {
							nearestExpiringTime = expiringTime;
						}
					}
				}	// synchronized (this.iterable)

				if (this.dir.isEmpty()) {
					this.dir.stopExpiringTask();
				}
			}	// synchronized (this.dir)

			if (nearestExpiringTime < Long.MAX_VALUE) {
//System.out.println("[ExpiringTask rescheduled: " + (nearestExpiringTime - timer.currentTimeMillis()) + "]");
				// reschedule
				timer.schedule(this, nearestExpiringTime, true /*isDaemon*/);
			}
			else {
//System.out.println("[ExpiringTask stopping]");
				// stop
				synchronized (this.dir) {
					this.dir.expiringTask = null;
				}
			}
		}
	}

	public Set<Map.Entry<K,V>> entrySet() {
		Set<Map.Entry<K,V>> result = new HashSet<Map.Entry<K,V>>();

		synchronized (this) {
			synchronized (this.iterable) {
				for (Map.Entry<K,V> entry: this) {
					result.add(entry);
				}
			}
		}

		return result;
	}

	public Iterator<Map.Entry<K,V>> iterator() {
		return new ExpiringDirIterator();
	}

	private class ExpiringDirIterator implements Iterator<Map.Entry<K,V>> {
		Iterator<Map.Entry<K,ExpiringValue<V>>> it;
		Map.Entry<K,ExpiringValue<V>> currentEntry;
		ExpiringDirEntry ret;

		ExpiringDirIterator() {
			this.it = iterable.iterator();
		}
		public boolean hasNext() { return this.it.hasNext(); }
		public Map.Entry<K,V> next() {
			Map.Entry<K,ExpiringValue<V>> internalEntry = this.it.next();
			return new ExpiringDirEntry(this,
					internalEntry.getKey(),
					internalEntry.getValue().getValue());
		}
		public void remove() { this.it.remove(); }

	}

	class ExpiringDirEntry implements Map.Entry<K,V> {
		K k;  V v;
		ExpiringDirIterator it = null;

		ExpiringDirEntry(K k, V v) {
			this.k = k; this.v = v;
		}
		ExpiringDirEntry(ExpiringDirIterator it, K k, V v) {
			this.it = it;
			this.k = k; this.v = v;
		}

		public K getKey() { return k; }
		public V getValue() { return v; }
		public V setValue(V newValue) {
			V ret = this.v;
			if (this.it != null) {
				this.it.currentEntry.setValue(new ExpiringValue<V>(newValue, defaultTTL));
			}
			return ret;
		}

		public String toString() {
			return this.k + "=" + this.v;
		}
	}
}

/*
 * Copyright 2006-2007,2009-2010 National Institute of Advanced Industrial Science
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

import java.io.Serializable;

import ow.util.Timer;

public class ExpiringValue<V> implements Serializable {
	private V value;

	/**
	 * The absolute time on which this value is expired.
	 */
	private long expire;

	/**
	 * The constructor.
	 *
	 * @param ttl this instance expires after this time passed. -1 means that this instance does not expire.
	 */
	ExpiringValue(V value, long ttl) {
		this.value = value;
		if (ttl >= 0L) {
			this.expire = Timer.currentTimeMillis() + ttl;
		}
		else {
			this.expire = Long.MAX_VALUE;
				// this instance does not expire
		}
	}

	public long getExpiringTime() { return this.expire; }
	public V getValue() { return this.value; }

	public int hashCode() {	// does not consider 'expire' field
		return value.hashCode();
	}

	public boolean equals(Object o) {	// does not compare expire field
		if (o instanceof ExpiringValue<?>) {
			ExpiringValue<?> other = (ExpiringValue<?>)o;		// actually unchecked

			if (this.value.equals(other.value)) {
				return true;
			}
		}

		return true;
	}

	public String toString() {
		return "{expire=" + this.expire + ", value=" + this.value + "}";
	}
}

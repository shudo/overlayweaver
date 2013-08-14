/*
 * Copyright 2006,2008-2010 Kazuyuki Shudo, and contributors.
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

package ow.dht;

import java.io.Serializable;

import ow.directory.expiration.Expirable;

/**
 * A pair of a stored value and a secret.
 */
public final class ValueInfo<V extends Serializable> implements Expirable, Serializable {
	private final V value;
	private final Attributes attr;

	public ValueInfo(V value, long ttl, ByteArray hashedSecret) {
		this.value = value;
		this.attr = new Attributes(ttl, hashedSecret);
	}
	public ValueInfo(V value, Attributes attr) {
		this.value = value;
		this.attr = attr;
	}

	public Attributes getAttributes() { return this.attr; }

	public V getValue() { return this.value; }
	public long setTTL(long ttl) { long old = this.attr.ttl; this.attr.ttl = ttl; return old; }
	public long getTTL() { return this.attr.ttl; }
	public ByteArray getHashedSecret() { return this.attr.hashedSecret; }

	public int hashCode() {	// ignore TTL
		int h = this.value.hashCode();

		if (this.attr.hashedSecret != null)
			h ^= this.attr.hashedSecret.hashCode();

		return h;
	}

	public boolean equals(Object o) {	// ignore TTL
		if (o == null || !(o instanceof ValueInfo<?>)) return false;

		ValueInfo<?> other = (ValueInfo<?>)o;

		if (!this.value.equals(other.value))
			return false;

		if (this.attr.hashedSecret == null) {
			if (other.attr.hashedSecret != null)
				return false;
		}
		else if (!this.attr.hashedSecret.equals(other.attr.hashedSecret)) {
			return false;
		}

		return true;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("{");
		sb.append(this.value);
		sb.append(",");
		sb.append(this.attr.ttl / 1000L);
		sb.append(",");
		sb.append(this.attr.hashedSecret);
		sb.append("}");

		return sb.toString();
	}

	public final static class Attributes implements Serializable {
		private long ttl;
		private ByteArray hashedSecret;

		public Attributes(long ttl, ByteArray hashedSecret) {
			this.ttl = ttl;
			this.hashedSecret = hashedSecret;
		}

		public long getTTL() { return this.ttl; }
		public ByteArray getHashedSecret() { return this.hashedSecret; }

		public int hashCode() {	// consider TTL
			int ret = ((int)ttl) ^ (int)(ttl >>> 32);

			if (this.hashedSecret != null)
				ret ^= this.hashedSecret.hashCode();

			return ret;
		}

		public boolean equals(Object o) {	// consider TTL
			if (o == null || !(o instanceof Attributes)) return false;

			Attributes other = (Attributes)o;

			if (this.ttl != other.ttl) return false;

			if (this.hashedSecret == null) {
				if (other.hashedSecret != null) return false;
			}
			else if (!this.hashedSecret.equals(other.hashedSecret)) {
				return false;
			}

			return true;
		}
	}
}

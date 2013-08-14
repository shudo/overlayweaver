/*
 * Copyright 2008,2010 Kazuyuki Shudo, and contributors.
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

package ow.dht.memcached;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Item implements Externalizable {
	// for message digest
	private static MessageDigest md = null;
	private final static String mdAlgoName = "SHA1";
	static {
		try {
			md = MessageDigest.getInstance(mdAlgoName);
		}
		catch (NoSuchAlgorithmException e) { /* NOTREACHED */ }
	}

	private byte[] data;
	private long flag;
	private volatile int cachedHashCode;

	public Item(byte[] data, long flag) {
		this.data = data;
		this.flag = flag;

		this.init();
	}

	private void init() {
		// calculate hash code
		byte[] hash;
		synchronized (md) {
			md.update(data);
			for (int i = 56; i >= 0; i -= 8) md.update((byte)(flag >>> i));
			hash = md.digest();
		}
		int hashCode = 0;
		int index = 24;
		for (int i = 0; i < hash.length; i++) {
			hashCode ^= (((int)hash[i]) << index);

			index -= 8;
			if (index < 0) index = 24;
		}
		cachedHashCode = hashCode;
	}

	public byte[] getData() { return this.data; }
	public long getFlag() { return this.flag; }

	public long getCasUnique() {
		long uniq = this.hashCode();
		if (uniq < 0) uniq += (1L << 32);
		return uniq;
	}

	public int hashCode() { return this.cachedHashCode; }

	public boolean equals(Object o) {
		if (o == null || !(o instanceof Item)) return false;

		Item other = (Item)o;

		if (this.hashCode() != other.hashCode()) return false;

		// compare data
		byte[] d0 = this.getData();
		byte[] d1 = other.getData();
		if (d0.length != d1.length) return false;
		for (int i = 0; i < d0.length; i++) {
			if (d0[i] != d1[i]) return false;
		}

		// compare flag
		if (this.getFlag() != other.getFlag()) return false;

		return true;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		try {
			sb.append("{ data: ").append(new String(this.data, Memcached.ENCODING));
		}
		catch (UnsupportedEncodingException e) { /*NOTREACHED*/ }
		sb.append(", flag:").append(this.flag);
		sb.append(" }");

		return sb.toString();
	}

	//
	// for object serialization
	//

	/**
	 * A public constructor with no argument required to implement Externalizable interface.
	 */
	public Item() {}

	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(this.data.length);
		out.write(this.data);
		out.writeLong(this.flag);
	}

	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int len = in.readInt();
		this.data = new byte[len];
		in.readFully(this.data);
		this.flag = in.readLong();

		this.init();
	}
}

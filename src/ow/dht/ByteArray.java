/*
 * Copyright 2006-2008,2010 Kazuyuki Shudo, and contributors.
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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A byte array with utility methods.
 */
public final class ByteArray implements java.io.Externalizable {
	// for message digest
	private static MessageDigest md = null;
	private final static String mdAlgoName = "SHA1";
	static {
		try {
			md = MessageDigest.getInstance(mdAlgoName);
		}
		catch (NoSuchAlgorithmException e) { /* NOTREACHED */ }
	}

	private byte[] barray;
	private volatile int hashCode;

	public ByteArray(byte[] bytes) {
		this.barray = bytes;

		this.init();
	}

	private void init() {
		int h = 0;
		for (int i = 0; i < this.barray.length; i++) {
			h ^= this.barray[i] << ((i % 4) * 8);
		}
		this.hashCode = h;
	}

	public byte[] getBytes() { return this.barray; }

	/**
	 * Returns a ByteArray instance based on the specified String.
	 */
	public static ByteArray valueOf(String str, String encoding)
			throws UnsupportedEncodingException{
		return new ByteArray(str.getBytes(encoding));
	}

	/**
	 * Returns a newly generated instance
	 * with the hashed value of the original instance.
	 */
	public ByteArray hashWithSHA1() {
		byte[] hashed;
		synchronized (md) {
			hashed = md.digest(this.barray);
		}

		return new ByteArray(hashed);
	}

	public boolean equals(Object o) {
		if (o == null || !(o instanceof ByteArray)) return false;

		ByteArray other = (ByteArray)o;

		if (this.barray.length != other.barray.length)
			return false;

		for (int i = 0; i < this.barray.length; i++) {
			if (this.barray[i] != other.barray[i])
				return false;
		}

		return true;
	}

	public int hashCode() { return this.hashCode; }

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("0x");
		for (int i = 0; i < this.barray.length; i++) {
			int b = this.barray[i] & 0xff;
			if (b < 16)
				sb.append("0");
			sb.append(Integer.toHexString(b));
		}

		return sb.toString();
	}

	// for object serialization
	// to reduce the size of a serialized message.

	/**
	 * A public constructor with no argument required to implement Externalizable interface.
	 */
	public ByteArray() {}

	public void writeExternal(java.io.ObjectOutput out)
			throws java.io.IOException {
		int len = barray.length;

		if (len < 255) {
			out.writeByte(len);
		}
		else {
			out.writeByte(0xff);
			out.writeInt(len);
		}

		out.write(barray);
	}

	public void readExternal(java.io.ObjectInput in)
			throws java.io.IOException, ClassNotFoundException {
		int len = in.readByte() & 0xff;
		if (len == 0xff) {
			len = in.readInt();
		}

		this.barray = new byte[len];
		in.readFully(this.barray);

		this.init();
	}
}

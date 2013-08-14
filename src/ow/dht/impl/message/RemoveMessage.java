/*
 * Copyright 2011 Kazuyuki Shudo, and contributors.
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

package ow.dht.impl.message;

import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.messaging.Message;

public final class RemoveMessage<V> extends Message {
	public final static String NAME = "REMOVE";
	public final static boolean TO_BE_REPORTED = true;
	public final static Color COLOR = null;

	// message members
	public DHT.RemoveRequest<V>[] requests;
	public ByteArray hashedSecret;
	public int numReplica;

	public RemoveMessage() { super(); }	// for Class#newInstance()

	public RemoveMessage(
			DHT.RemoveRequest<V>[] requests, ByteArray hashedSecret, int numReplica) {
		this.requests = requests;
		this.hashedSecret = hashedSecret;
		this.numReplica = numReplica;
	}

	public void encodeContents(ObjectOutputStream oos) throws IOException {
		oos.writeObject(this.requests);
		oos.writeObject(this.hashedSecret);
		oos.writeInt(this.numReplica);
	}

	public void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		this.requests = (DHT.RemoveRequest<V>[])ois.readObject();
		this.hashedSecret = (ByteArray)ois.readObject();
		this.numReplica = ois.readInt();
	}
}

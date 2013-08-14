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

package ow.mcast.impl.message;

import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import ow.id.ID;
import ow.messaging.Message;

public final class MulticastMessage extends Message {
	public final static String NAME = "MULTICAST";
	public final static boolean TO_BE_REPORTED = true;
	public final static Color COLOR = null;

	// message members
	public ID groupID;
	public int ttl;
	public Serializable payload;

	public MulticastMessage() { super(); }	// for Class#newInstance()

	public MulticastMessage(
			ID groupID, int ttl, Serializable payload) {
		this.groupID = groupID;
		this.ttl = ttl;
		this.payload = payload;
	}

	public void encodeContents(ObjectOutputStream oos) throws IOException {
		oos.writeObject(this.groupID);
		oos.writeInt(this.ttl);
		oos.writeObject(this.payload);
	}

	public void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		this.groupID = (ID)ois.readObject();
		this.ttl = ois.readInt();
		this.payload = (Serializable)ois.readObject();
	}
}

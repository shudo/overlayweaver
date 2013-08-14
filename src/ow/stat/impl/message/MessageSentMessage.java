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

package ow.stat.impl.message;

import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ow.messaging.Message;
import ow.messaging.MessagingAddress;

public final class MessageSentMessage extends Message {
	public final static String NAME = "MESSAGE_SENT";
	public final static boolean TO_BE_REPORTED = false;
	public final static Color COLOR = null;

	// message members
	public MessagingAddress msgSrc;
	public MessagingAddress msgDest;
	public int msgTag;
	public int msgLen;

	public MessageSentMessage() { super(); }	// for Class#newInstance()

	public MessageSentMessage(
			MessagingAddress msgSrc, MessagingAddress msgDest, int msgTag, int msgLen) {
		this.msgSrc = msgSrc;
		this.msgDest = msgDest;
		this.msgTag = msgTag;
		this.msgLen = msgLen;
	}

	public void encodeContents(ObjectOutputStream oos) throws IOException {
		oos.writeObject(this.msgSrc);
		oos.writeObject(this.msgDest);
		oos.writeInt(this.msgTag);
		oos.writeInt(this.msgLen);
	}

	public void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		this.msgSrc = (MessagingAddress)ois.readObject();
		this.msgDest = (MessagingAddress)ois.readObject();
		this.msgTag = ois.readInt();
		this.msgLen = ois.readInt();
	}
}

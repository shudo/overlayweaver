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

package ow.routing.impl.message;

import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.routing.RoutingResult;

public final class RecResultMessage extends Message {
	public final static String NAME = "REC_RESULT";
	public final static boolean TO_BE_REPORTED = true;
	public final static Color COLOR = Color.ORANGE;

	// message members
	public int routingID;
	public boolean succeed;
	public ID[] target;
	public RoutingResult[] routingRes;
	public IDAddressPair[] blackList;
	public Serializable[] callbackResult;

	public RecResultMessage() { super(); }	// for Class#newInstance()

	public RecResultMessage(
			int routingID, boolean succeed, ID[] target, RoutingResult[] routingRes, IDAddressPair[] blackList, Serializable[] callbackResult) {
		this.routingID = routingID;
		this.succeed = succeed;
		this.target = target;
		this.routingRes = routingRes;
		this.blackList = blackList;
		this.callbackResult = callbackResult;
	}

	public void encodeContents(ObjectOutputStream oos) throws IOException {
		oos.writeInt(this.routingID);
		oos.writeBoolean(this.succeed);
		oos.writeObject(this.target);
		oos.writeObject(this.routingRes);
		oos.writeObject(this.blackList);
		oos.writeObject(this.callbackResult);
	}

	public void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		this.routingID = ois.readInt();
		this.succeed = ois.readBoolean();
		this.target = (ID[])ois.readObject();
		this.routingRes = (RoutingResult[])ois.readObject();
		this.blackList = (IDAddressPair[])ois.readObject();
		this.callbackResult = (Serializable[])ois.readObject();
	}
}

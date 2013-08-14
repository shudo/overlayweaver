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
import ow.routing.RoutingContext;
import ow.routing.RoutingHop;

public final class RecRouteInvokeMessage extends AbstractRecRouteMessage {
	public final static String NAME = "REC_ROUTE_INVOKE";
	public final static boolean TO_BE_REPORTED = true;
	public final static Color COLOR = Color.BLUE;

	// message members
	public int callbackTag;
	public Serializable[][] callbackArgs;

	public RecRouteInvokeMessage() { super(); }	// for Class#newInstance()

	public RecRouteInvokeMessage(
			int routingID, ID[] target, RoutingContext[] cxt, int numRespNodeCands, IDAddressPair initiator, int ttl, RoutingHop[] route, IDAddressPair[] blackList,
			int callbackTag, Serializable[][] callbackArgs) {
		super(routingID, target, cxt, numRespNodeCands, initiator, ttl, route, blackList);
		this.callbackTag = callbackTag;
		this.callbackArgs = callbackArgs;
	}

	public void encodeContents(ObjectOutputStream oos) throws IOException {
		super.encodeContents(oos);
		oos.writeInt(this.callbackTag);
		oos.writeObject(this.callbackArgs);
	}

	public void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		super.decodeContents(ois);
		this.callbackTag = ois.readInt();
		this.callbackArgs = (Serializable[][])ois.readObject();
	}
}

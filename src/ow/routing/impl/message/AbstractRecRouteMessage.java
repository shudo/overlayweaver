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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.routing.RoutingContext;
import ow.routing.RoutingHop;

public abstract class AbstractRecRouteMessage extends Message {
	// message members
	public int routingID;
	public ID[] target;
	public RoutingContext[] cxt;
	public int numRespNodeCands;
	public IDAddressPair initiator;
	public int ttl;
	public RoutingHop[] route;
	public IDAddressPair[] blackList;

	public AbstractRecRouteMessage() { super(); }	// for Class#newInstance()

	public AbstractRecRouteMessage(
			int routingID, ID[] target, RoutingContext[] cxt, int numRespNodeCands, IDAddressPair initiator, int ttl, RoutingHop[] route, IDAddressPair[] blackList) {
		this.routingID = routingID;
		this.target = target;
		this.cxt = cxt;
		this.numRespNodeCands = numRespNodeCands;
		this.initiator = initiator;
		this.ttl = ttl;
		this.route = route;
		this.blackList = blackList;
	}

	public void encodeContents(ObjectOutputStream oos) throws IOException {
		oos.writeInt(this.routingID);
		oos.writeObject(this.target);
		oos.writeObject(this.cxt);
		oos.writeInt(this.numRespNodeCands);
		oos.writeObject(this.initiator);
		oos.writeInt(this.ttl);
		oos.writeObject(this.route);
		oos.writeObject(this.blackList);
	}

	public void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		this.routingID = ois.readInt();
		this.target = (ID[])ois.readObject();
		this.cxt = (RoutingContext[])ois.readObject();
		this.numRespNodeCands = ois.readInt();
		this.initiator = (IDAddressPair)ois.readObject();
		this.ttl = ois.readInt();
		this.route = (RoutingHop[])ois.readObject();
		this.blackList = (IDAddressPair[])ois.readObject();
	}
}

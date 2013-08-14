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

public abstract class AbstractIteRouteMessage extends Message {
	// message members
	public ID[] target;
	public RoutingContext[] cxt;
	public IDAddressPair[] lastHop;
	public int numNextHopCandidates;
	public int numRespNodeCands;

	public AbstractIteRouteMessage() { super(); }	// for Class#newInstance()

	public AbstractIteRouteMessage(
			ID[] target, RoutingContext[] cxt, IDAddressPair[] lastHop, int numNextHopCandidates, int numRespNodeCands) {
		this.target = target;
		this.cxt = cxt;
		this.lastHop = lastHop;
		this.numNextHopCandidates = numNextHopCandidates;
		this.numRespNodeCands = numRespNodeCands;
	}

	public void encodeContents(ObjectOutputStream oos) throws IOException {
		oos.writeObject(this.target);
		oos.writeObject(this.cxt);
		oos.writeObject(this.lastHop);
		oos.writeInt(this.numNextHopCandidates);
		oos.writeInt(this.numRespNodeCands);
	}

	public void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		this.target = (ID[])ois.readObject();
		this.cxt = (RoutingContext[])ois.readObject();
		this.lastHop = (IDAddressPair[])ois.readObject();
		this.numNextHopCandidates = ois.readInt();
		this.numRespNodeCands = ois.readInt();
	}
}

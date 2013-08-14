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

import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.routing.RoutingContext;

public final class IteReplyMessage extends Message {
	public final static String NAME = "ITE_REPLY";
	public final static boolean TO_BE_REPORTED = true;
	public final static Color COLOR = null;

	// message members
	public IDAddressPair[][] nextHopCandidates;
	public IDAddressPair[][] responsibleNodeCands;
	public RoutingContext[] routingContexts;
	public Serializable[] callbackResult;

	public IteReplyMessage() { super(); }	// for Class#newInstance()

	public IteReplyMessage(
			IDAddressPair[][] nextHopCandidates, IDAddressPair[][] responsibleNodeCands, RoutingContext[] routingContexts,
			Serializable[] callbackResult) {
		this.nextHopCandidates = nextHopCandidates;
		this.responsibleNodeCands = responsibleNodeCands;
		this.routingContexts = routingContexts;
		this.callbackResult = callbackResult;
	}

	public void encodeContents(ObjectOutputStream oos) throws IOException {
		oos.writeObject(this.nextHopCandidates);
		oos.writeObject(this.responsibleNodeCands);
		oos.writeObject(this.routingContexts);
		oos.writeObject(this.callbackResult);
	}

	public void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		this.nextHopCandidates = (IDAddressPair[][])ois.readObject();
		this.responsibleNodeCands = (IDAddressPair[][])ois.readObject();
		this.routingContexts = (RoutingContext[])ois.readObject();
		this.callbackResult = (Serializable[])ois.readObject();
	}
}

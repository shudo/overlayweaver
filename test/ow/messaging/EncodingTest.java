/*
 * Copyright 2007-2008,2010-2011 Kazuyuki Shudo, and contributors.
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

package ow.messaging;

import java.nio.ByteBuffer;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.routing.impl.message.IteRouteNoneMessage;

public class EncodingTest {
	private final static String TRANSPORT = "UDP";
	private static int ID_SIZE = 20;

	public static void main(String[] args) throws Exception{
		MessagingProvider provider = MessagingFactory.getProvider(TRANSPORT, Signature.getAllAcceptingSignature());
		MessagingAddress anAddr = provider.getMessagingAddress(50000);
		ID anID = ID.getRandomID(ID_SIZE);
		IDAddressPair src = IDAddressPair.getIDAddressPair(anID, anAddr);
		ID[] target = new ID[1]; target[0] = anID;
		IDAddressPair[] lastHop = new IDAddressPair[1]; lastHop[0] = src;

		Message aMessage = new IteRouteNoneMessage(
				target, null, lastHop, 1, 1);
		aMessage.setSource(src);

		ByteBuffer buf = aMessage.encode();

		aMessage = Message.decode(buf);

		System.out.println("ITE_ROUTE_NONE is expected: " + aMessage.getName());
		target = ((IteRouteNoneMessage)aMessage).target;
		lastHop = ((IteRouteNoneMessage)aMessage).lastHop;
	}
}

/*
 * Copyright 2006,2008-2011 Kazuyuki Shudo, and contributors.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.impl.message.PutMessage;
import ow.id.ID;
import ow.id.IDAddressPair;
import ow.routing.RoutingHop;
import ow.routing.impl.message.IteRouteInvokeMessage;
import ow.routing.impl.message.IteRouteNoneMessage;
import ow.routing.impl.message.PingMessage;
import ow.routing.impl.message.RecRouteInvokeMessage;
import ow.routing.impl.message.RecRouteNoneMessage;

/**
 * An utility to check the size of a message.
 * This class prints the size of a message and serializes it into a file. 
 */
public class MessageSerializingUtil {
	private final static int ID_SIZE = 20;
	private final static String FILENAME_PREFIX = "message-";

	// conditions
	private final static int CONCURRENCY = 10;
	private final static int VALUE_SIZE = 10;
	private final static int ROUTE_LENGTH = 10;

	public static void main(String[] args) throws Exception {
		// temporary variables
		MessagingAddress addr;
		IDAddressPair idAddr;

		// conditions
		IDAddressPair[] blackList = null;	//new IDAddressPair[0]

		// message fields
		byte[] sig = Signature.getAllAcceptingSignature();
		MessagingProvider msgProvider = MessagingFactory.getProvider("UDP", sig);
			// throws NoSuchProviderException
		addr = msgProvider.getMessagingAddress(4000);
		IDAddressPair selfIDAddr = IDAddressPair.getIDAddressPair(ID_SIZE, addr);

		addr = msgProvider.getMessagingAddress("192.168.10.1", 4000);
		IDAddressPair otherIDAddr = IDAddressPair.getIDAddressPair(ID_SIZE, addr);

		DHT.PutRequest<String>[] requests = new DHT.PutRequest/*<String>*/[CONCURRENCY];
		ID[] keys = new ID[CONCURRENCY];
//		String[][] values = new String[CONCURRENCY][1];
		IDAddressPair[] lastHops = new IDAddressPair[CONCURRENCY];
		Serializable[][] callbackArgs = new Serializable[CONCURRENCY][1];
		for (int i = 0; i < CONCURRENCY; i++) {
			keys[i] = ID.getRandomID(ID_SIZE);
			String[] values = new String[1];
			values[0] = ("0000000000" + Integer.toString(i)).substring(0, VALUE_SIZE);

			requests[i] = new DHT.PutRequest<String>(keys[i], values);

			addr = msgProvider.getMessagingAddress("192.168.0." + i, 4000);
			lastHops[i] = IDAddressPair.getIDAddressPair(ID.getRandomID(ID_SIZE), addr);

			callbackArgs[i][0] = keys[i];	//ID.getRandomID(ID_SIZE);
		}

		ByteArray secret = ByteArray.valueOf("secret", "UTF-8");
		secret = secret.hashWithSHA1();

		RoutingHop[] route = new RoutingHop[ROUTE_LENGTH];
		for (int i = 0; i < ROUTE_LENGTH; i++) {
			addr = msgProvider.getMessagingAddress("192.168.1." + i, 4000);
			idAddr = IDAddressPair.getIDAddressPair(ID.getRandomID(ID_SIZE), addr);
			route[i] = RoutingHop.newInstance(idAddr);
		}

		Message msg;

		// PING
		msg = new PingMessage();
		msg.setSource(selfIDAddr);
		printMessageSize(msg);

		// PUT
		msg = new PutMessage<String>(
				requests, 128, secret, 1);
		msg.setSource(selfIDAddr);
		printMessageSize(msg);

		// ITE_ROUTE_NONE
		msg = new IteRouteNoneMessage(
				keys, null, lastHops, 3, 8);
		msg.setSource(selfIDAddr);
		printMessageSize(msg, "ITE_");

		// ITE_ROUTE_INVOKE
		msg = new IteRouteInvokeMessage(
				keys, null, lastHops, 3, 8,
				123, callbackArgs);
		msg.setSource(selfIDAddr);
		printMessageSize(msg, "ITE_");

		// REC_ROUTE_NONE
		msg = new RecRouteNoneMessage(
				12345, keys, null, 8, otherIDAddr, 128, route, blackList);
		msg.setSource(selfIDAddr);
		printMessageSize(msg, "REC_");

		// REC_ROUTE_INVOKE
		msg = new RecRouteInvokeMessage(
				23456, keys, null, 8, otherIDAddr, 128, route, blackList,
				123, callbackArgs);
		msg.setSource(selfIDAddr);
		printMessageSize(msg, "REC_");
	}

	private static void printMessageSize(Message msg) {
		printMessageSize(msg, null);
	}

	private static void printMessageSize(Message msg, String tagNamePrefix) {
		ByteBuffer buf = msg.encode();

		// print a message size
		int len = buf.remaining();
		System.out.println(msg.getName() + ": " + len);

		// write to a file
		RandomAccessFile f = null;
		try {
			f = new RandomAccessFile(FILENAME_PREFIX
					+ (tagNamePrefix != null ? tagNamePrefix : "")
					+ msg.getName(), "rw");
		}
		catch (FileNotFoundException e) {
			// NOTREACHED
		}

		FileChannel ch = f.getChannel();

		try {
			ch.write(buf);

			ch.close();
			f.close();
		}
		catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}

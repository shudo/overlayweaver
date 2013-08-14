/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
 * and Technology (AIST), and contributors.
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

// A sample program to use DHT service.
// Usage: java ow.dht.DHTTest [<initial contact>[:<port>]]

package ow.dht;

import java.io.IOException;
import java.util.Set;

import ow.id.ID;
import ow.routing.RoutingException;

public class DHTTest {
	private final static String COMMAND = "java ow.dht.DHTTest";
	private final static int PORT = 3997;
	private final static long TTL = 600 * 1000L;	// in millisecond

	public static void usage(String command) {
		System.err.print("Usage: " + command);
		System.err.println(" [<initial contact>[:<port>]]");
	}

	public static void main(String[] args) {
		// obtain a DHT object
		DHTConfiguration config = DHTFactory.getDefaultConfiguration();
		config.setRoutingStyle("Iterative");
		config.setRoutingAlgorithm("Pastry");
		config.setSelfPort(PORT);

		DHT<String> dht = null;
		try {
			dht = DHTFactory.<String>getDHT(config);
		}
		catch (Exception e) {
			System.err.println("Failed to obtain a DHT object.");
			e.printStackTrace();

			System.exit(1);
		}


		// join an overlay
		if (args.length >= 1) {
			try {
				dht.joinOverlay(args[0], PORT /* is default port number */);
			}
			catch (IOException e) {
				System.err.println("Failed to join an overlay.");
				e.printStackTrace();

				System.exit(1);
			}
		}

		System.out.println("A DHT object initialized.");


		// put and get
		try { Thread.sleep(3000L); } catch (InterruptedException e) {}

		dht.setTTLForPut(TTL);

		int idSize = dht.getRoutingAlgorithmConfiguration().getIDSizeInByte();
		ID key = ID.getHashcodeBasedID("hosts", idSize);	
		String value = dht.getSelfIDAddressPair().getAddress().toString();
		try {
			dht.put(key, value);					// put	
		}
		catch (Exception e) {
			System.err.println("Failed to put.");
			e.printStackTrace();

			System.exit(1);
		}

		try { Thread.sleep(3000); } catch (InterruptedException e) {}

		Set<ValueInfo<String>> valueSet = null;
		try {
			valueSet = dht.get(key);		// get

			if (valueSet != null) {
				for (ValueInfo<String> s: valueSet) {
					System.out.println("value: " + s.getValue());
				}
			}
			else {
				System.out.println("[Error] No value obtained.");
			}
		}
		catch (RoutingException e) {
			System.out.println("Routing failed.");
		}

		// sleep forever
		try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException e) {}
	}
}

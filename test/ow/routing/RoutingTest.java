/*
 * Copyright 2006-2007,2009,2011 National Institute of Advanced Industrial Science
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

package ow.routing;

import ow.id.ID;
import ow.messaging.MessagingFactory;
import ow.messaging.MessagingProvider;
import ow.messaging.Signature;

public class RoutingTest {
	private final static String COMMAND = "java RoutingTest";

	private final static int ID_SIZE = 20;

	private final static String MESSAGING_TRANSPORT = "Emulator";
	private final static String ROUTING_STYLE = "Recursive";
	//private final static String ROUTING_STYLE = "Iterative";
	private final static String ROUTING_ALGORITHM = "Kademlia";
	private final static int PORT = 10000;
	private final static long TIMEOUT = 2000;

	private final static String[] ID_LIST = {
		"0000000000000000000000000000000000000000",	// 0
/*
		"ffffffffffffffffffffffffffffffffffffffff",	// 1
		"fffffffffffffffffffffffffffffffffffffffe",	// 2
		"fffffffffffffffffffffffffffffffffffffffc",	// 3
		"fffffffffffffffffffffffffffffffffffffff8",	// 4
		"fffffffffffffffffffffffffffffffffffffff0",	// 5
		"ffffffffffffffffffffffffffffffffffffffe0",	// 6
		"ffffffffffffffffffffffffffffffffffffffc0",	// 7
		"ffffffffffffffffffffffffffffffffffffff80",	// 8
		"ffffffffffffffffffffffffffffffffffffff00",	// 9
		"fffffffffffffffffffffffffffffffffffffe00",	// 10
		"0102030405060708091011121314151617181920"
*/
	};

	private final static String[] HOST_LIST = {
		"emu0", "emu0", "emu1", "emu2", "emu3", "emu4", "emu5", "emu6", "emu7", "emu8", "emu9", 
		"emu10", "emu11", "emu12", "emu13", "emu14", "emu15", "emu16", "emu17", "emu18", "emu19" 
	};

	MessagingProvider msgProvider;
	RoutingService routingSvc;
	RoutingAlgorithm routingAlgo;

	String initialContact = null;

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			usage(COMMAND);
			System.exit(1);
		}

		int n = Integer.parseInt(args[0]);

		RoutingTest r = new RoutingTest(n);
		r.start(n);
	}

	public void start(int n) throws Exception {
		RoutingHop[] route;

		if (n == 0) {					// 0
			Thread.sleep(6000);

			ID target = ID.getID("fff0000000000000000000000000000000000000", ID_SIZE);
			routingSvc.invokeCallbacksOnRoute(target, 1, null, 123, null);
		}
		else if (n >= 1 && n < 100) {	// 1-99
			RoutingResult res =
				this.routingSvc.join(msgProvider.getMessagingAddress(this.initialContact, PORT));
			route = res.getRoute();
			if (route != null) {
				System.out.print("Route from & to: ");
				System.out.println(this.routingSvc.getSelfIDAddressPair());
				System.out.println(this.routingSvc.routeToString(route));
			}
			else {
				System.err.println("Joining failed.");
				return;
			}

			Thread.sleep(5000);
		}
		else {
			RoutingResult res =
				this.routingSvc.join(msgProvider.getMessagingAddress(this.initialContact, PORT));
			if (res == null) {
				System.err.println("Joining failed.");
				return;
			}

			Thread.sleep(5000);
		}

		System.out.print("Routing table of node " +
				this.routingSvc.getSelfIDAddressPair() + " ");
		System.out.println(this.routingAlgo.getRoutingTableString(0));

		Thread.sleep(60000);
	}

	private static void usage(String cmd) {
		System.out.print("usage: ");
		System.out.print(cmd);
		System.out.println(" <number>");
	}

	public RoutingTest(final int n) throws Exception {
		ID myID = null;
		try {
			String myIDString = ID_LIST[n];
			myID = ID.getID(myIDString, ID_SIZE);
		}
		catch (ArrayIndexOutOfBoundsException e) {
			myID = ID.getRandomID(ID_SIZE);
		}

		try {
			this.initialContact = HOST_LIST[n];
		}
		catch (ArrayIndexOutOfBoundsException e) {
			this.initialContact = "emu0";
		}

		// initialize
		initRoutingService(
				myID, MESSAGING_TRANSPORT, ROUTING_STYLE, ROUTING_ALGORITHM, PORT, TIMEOUT);
	}

	private void initRoutingService(
			ID selfID, String messagingType, String runtimeType, String algoName,
			int selfPort, long timeout) throws Exception {
		// Initialize basic components
		this.msgProvider = MessagingFactory.getProvider(messagingType, Signature.getAllAcceptingSignature());

		RoutingAlgorithmProvider algoProvider = RoutingAlgorithmFactory.getProvider(algoName);
		RoutingAlgorithmConfiguration algoConfig = algoProvider.getDefaultConfiguration();

		RoutingServiceProvider svcProvider = RoutingServiceFactory.getProvider(runtimeType);
		RoutingServiceConfiguration svcConf = svcProvider.getDefaultConfiguration();
		this.routingSvc = svcProvider.getService(svcConf,
				msgProvider, this.msgProvider.getDefaultConfiguration(), selfPort, 1,
				algoProvider, algoConfig, selfID);

		RoutingAlgorithm algo = algoProvider.initializeAlgorithmInstance(algoConfig, this.routingSvc);

		this.routingAlgo = algo;	// a backup to peek the routing table
	}
}

/*
 * Copyright 2006,2009,2011 National Institute of Advanced Industrial Science
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

package ow.messaging;

public class MsgTestServer {
	private final static String COMMAND = "java MsgTestServer";
	private final static int DEFAULT_PORT = 10000;
	private final static int DEFAULT_PORT_RANGE = 10;
	private final static String DEFAULT_TRANSPORT = "TCP"; 

	private static void usage() {
		System.out.print("usage: ");
		System.out.print(COMMAND);
		System.out.println(" [-hut] [port]");
	}

	public static void main(String[] args) throws Exception {
		String transport = DEFAULT_TRANSPORT;
		int port = DEFAULT_PORT;
		int portRange = DEFAULT_PORT_RANGE;
		int i = 0;

		while (i < args.length && args[i].startsWith("-")) {
			if (args[i].equals("-h")) {
				usage();
				System.exit(0);
			}
			else if (args[i].equals("-t")) {
				transport = "TCP";
				i++;
			}
			else if (args[i].equals("-u")) {
				transport = "UDP";
				i++;
			}
		}

		if (args.length - i > 0) {
			port = Integer.parseInt(args[i]);
			if (port <= 0) {
				usage();
				System.exit(1);
			}
		}

		start(transport, port, portRange);
	}

	private static void start(String transport, int port, int portRange) throws Exception {
		MessagingProvider provider = MessagingFactory.getProvider(transport, Signature.getAllAcceptingSignature());
		MessageReceiver receiver = provider.getReceiver(provider.getDefaultConfiguration(), port, portRange);
		//receiver.start();

		receiver.addHandler(
				new MessageHandler() {
					public Message process(Message msg) {
						System.out.println("msg received.");
						System.out.println("  tag: " + msg.getTag());
//try {Thread.sleep(100000);} catch (Exception e) {}
						return msg;
					};
				});

		System.out.println("transport: " + transport);
		System.out.println("port #: " + port);
		System.out.println("wait for 30 sec.");
		Thread.sleep(30000);
	}
}

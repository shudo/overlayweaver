/*
 * Copyright 2006-2008,2011 National Institute of Advanced Industrial Science
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

import ow.routing.impl.message.AckMessage;
import ow.routing.impl.message.PingMessage;

public class MessagingBenchmark {
	private final static String COMMAND = "java MessagingBenchmark";
	private final static int DEFAULT_PORT = 10000;
	private final static String DEFAULT_TRANSPORT = "UDP";

	private final static int DEFAULT_REPEAT = 10000;

	private static void usage() {
		System.out.print("usage: ");
		System.out.print(COMMAND);
		System.out.println(" [-hut] [-r <repeat>] [-s <self IP address>] <hostname <port>>");
	}

	public static void main(String[] args) throws Exception {
		String transport = DEFAULT_TRANSPORT;
		String host = null;
		int port = DEFAULT_PORT;
		int repeat = DEFAULT_REPEAT;
		String selfIPAddress = null;
		int i = 0;

		while (i < args.length && args[i].startsWith("-")) {
			if (args[i].equals("-h")) {
				usage();
				System.exit(0);
			}
			else if (args[i].equals("-t")) {
				i++;
				transport = "TCP";
			}
			else if (args[i].equals("-u")) {
				i++;
				transport = "UDP";
			}
			else if (args[i].equals("-r")) {
				i++;
				repeat = Integer.parseInt(args[i++]);
			}
			else if (args[i].equals("-s")) {
				i++;
				selfIPAddress = args[i++];
			}
		}

		if (args.length - i >= 1) {
			host = args[i];
		}

		if (args.length - i >= 2) {
			port = Integer.parseInt(args[i + 1]);
			if (port <= 0) {
				usage();
				System.exit(1);
			}
		}

		start(transport, host, port, repeat, selfIPAddress);
	}

	private static void start(String transport, String host, int port, int repeat, String selfIPAddress) throws Exception {
		System.out.println("transport: " + transport);

		MessagingProvider provider = MessagingFactory.getProvider(transport, Signature.getAllAcceptingSignature());
		if (selfIPAddress != null) {
			provider.setSelfAddress(selfIPAddress);
		}
		MessageReceiver receiver = provider.getReceiver(provider.getDefaultConfiguration(), port, 1);
		MessageSender sender = receiver.getSender();

		// register MessageHandler
		ReplyingHandler msgHandler = new ReplyingHandler(repeat);
		receiver.addHandler(msgHandler);

		// send
		if (host != null) {
			MessagingAddress dest = provider.getMessagingAddress(host, port);

			Message reqMsg = new PingMessage();
			Message repMsg;

			long time = -System.currentTimeMillis();
			for (int i = 0; i < repeat; i++) {
				repMsg = sender.sendAndReceive(dest, reqMsg);
			}
			time += System.currentTimeMillis();

			System.out.println(time + " msec (" + repeat + " times)");
		}
		else {
			synchronized (msgHandler) {
				while (true) {
					if (msgHandler.finished) break;
					msgHandler.wait();
				}
			}
		}
	}

	private static class ReplyingHandler implements MessageHandler {
		private Message repMsg = new AckMessage();
		private int repeat;
		public boolean finished = false;

		ReplyingHandler(int repeat) {
			this.repeat = repeat;
		}

		public Message process(Message msg) {
			if (--repeat <= 0) {
				synchronized (this) {
					this.finished = true;
					this.notifyAll();
				}
			}

			return this.repMsg;
		}
	}
}

/*
 * Copyright 2007 Kazuyuki Shudo, and contributors.
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

package ow.tool.listnodes;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import ow.id.ID;
import ow.messaging.MessageReceiver;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.messaging.util.MessagingUtility;
import ow.stat.NodeCollector;
import ow.stat.NodeCollectorCallback;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;
import ow.util.HTMLUtil;

public class Main {
	private final static String COMMAND = "owlistnodes";

	private static void usage(String command) {
		System.out.println("usage: " + command
				+ " [-r]"
				+ " [-t UDP|TCP]"
				+ " [-s [<self address>][[:]<port>]"
				+ " <host>[:<port>] [<port>]");
	}

	public static void main(String[] args) throws Exception {
		boolean printRawForm = false;
		String transport = null;
		String selfAddressAndPort = null;

		// parse command-line arguments
		Options opts = new Options();
		opts.addOption("h", "help", false, "print help");
		opts.addOption("r", "raw", false, "print nodes in the raw form (hostname:port)");
		opts.addOption("t", "transport", true, "transpoft, UDP or TCP");
		opts.addOption("s", "selfipaddress", true, "self IP address (and port)");

		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(opts, args);
		}
		catch (ParseException e) {
			System.out.println("There is an invalid option.");
			e.printStackTrace();
			System.exit(1);
		}

		String optVal;
		if (cmd.hasOption('h')) {
			usage(COMMAND);
			System.exit(1);
		}
		if (cmd.hasOption('r')) {
			printRawForm = true;
		}
		optVal = cmd.getOptionValue('t');
		if (optVal != null) {
			transport = optVal;
		}
		optVal = cmd.getOptionValue('s');
		if (optVal != null) {
			selfAddressAndPort = optVal;
		}

		args = cmd.getArgs();

		// parse initial contact
		String contactHostAndPort = null;
		int contactPort = -1;

		if (args.length < 1) {
			usage(COMMAND);
			System.exit(1);
		}

		contactHostAndPort = args[0];

		if (args.length >= 2)
			contactPort = Integer.parseInt(args[1]);

		//
		// prepare a NodeCollector and invoke it
		//

		// prepare StatConfiguration
		StatConfiguration config = StatFactory.getDefaultConfiguration();
		config.setDoUPnPNATTraversal(false);
		if (transport != null) {
			config.setMessagingTransport(transport);
		}
		if (selfAddressAndPort != null) {
			MessagingUtility.HostAndPort hostAndPort =
				MessagingUtility.parseHostnameAndPort(selfAddressAndPort, config.getSelfPort());

			config.setSelfAddress(hostAndPort.getHostName());
			config.setSelfPort(hostAndPort.getPort());
		}

		// prepare MessageReceiver and initial contact
		MessagingProvider provider = config.deriveMessagingProvider();
		MessageReceiver receiver = config.deriveMessageReceiver(provider);

		MessagingAddress contact;
		try {
			contact = provider.getMessagingAddress(contactHostAndPort, contactPort);
		}
		catch (IllegalArgumentException e) {
			contact = provider.getMessagingAddress(contactHostAndPort, config.getContactPort());
		}

		// prepare a callback
		NodeCollectorCallback cb;
		if (printRawForm) {
			cb = new NodeCollectorCallback() {
				public void addNode(ID id, MessagingAddress address) {
					System.out.println(new MessagingUtility.HostAndPort(address.getHostname(), address.getPort()));
						// <hostname>:<port>
				}
				public void removeNode(ID id) {}
			};
		}
		else {
			cb = new NodeCollectorCallback() {
				public void addNode(ID id, MessagingAddress address) {
					System.out.println(HTMLUtil.convertMessagingAddressToURL(address));
						// http://<hostname>:port/
				}
				public void removeNode(ID id) {}
			};
		}

		// instantiate and invoke a NodeCollector
		NodeCollector collector =
			StatFactory.getNodeCollector(config, contact, cb, receiver);

		collector.investigate();

		// stop MessageReceiver to prevent
		// handling incoming messages and submissions to a thread pool
		receiver.stop();
	}
}

/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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

package ow.tool.util.toolframework;

import java.net.UnknownHostException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import ow.id.ID;
import ow.mcast.Mcast;
import ow.mcast.McastConfiguration;
import ow.mcast.McastFactory;
import ow.messaging.util.MessagingUtility;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;

/**
 * A base class of Mcast-based tools.
 */
public abstract class AbstractMcastBasedTool {
	private final static int MAX_ID_SIZE = 20;

	protected abstract void usage(String command);

	protected void usage(String command, String optionDescriptions) {
		System.out.print("Usage: " + command);

		if (optionDescriptions != null) {
			System.out.print(" ");
			System.out.print(optionDescriptions);
		}

		System.out.println(" [-i <self ID>] [-m <stat collector addr>[:<port>]]"
				+ " [-t UDP|TCP] [-a algorithm name] [-r routing style]"
				+ " [-s [<self address>[:<port>]]|<port>] [--no-upnp]"
				+ " [<host>[:<port>]] [<port>]");
	}

	protected Options getInitialOptions() {
		Options opts = new Options();
		opts.addOption("h", "help", false, "print help");
		opts.addOption("i", "id", true, "self ID");
		opts.addOption("m", "statcollector", true, "address of stat collector");
		opts.addOption("t", "transport", true, "transport, UDP or TCP");
		opts.addOption("a", "algorithm", true, "routing algorithm");
		opts.addOption("r", "routingstyle", true, "routing style, Iterative or Recursive");
		opts.addOption("s", "selfaddress", true, "self IP address");
		opts.addOption("N", "no-upnp", false, "disable UPnP address port mapping");

		return opts;
	}

	public Mcast initialize(short applicationID, short applicationVersion,
			String command, CommandLine cmd) throws Exception {
		String transport = null;
		String algorithm = null;
		String routingStyle = null;
		ID selfID = null;
		String statCollectorAddressAndPort = null;
		String selfAddressAndPort = null;
		boolean noUPnP = false;

		boolean join = false;
		String contactHost = null;
		int contactPort = -1;
		String contactString = null;

		// parse command-line arguments
		String optVal;
		if (cmd.hasOption('h')) {
			usage(command);
			System.exit(1);
		}
		optVal = cmd.getOptionValue('i');
		if (optVal != null) {
			selfID = ID.getID(optVal, MAX_ID_SIZE);
		}
		optVal = cmd.getOptionValue('m');
		if (optVal != null) {
			statCollectorAddressAndPort = optVal;
		}
		optVal = cmd.getOptionValue('t');
		if (optVal != null) {
			transport = optVal;
		}
		optVal = cmd.getOptionValue('a');
		if (optVal != null) {
			algorithm = optVal;
		}
		optVal = cmd.getOptionValue('r');
		if (optVal != null) {
			routingStyle = optVal;
		}
		optVal = cmd.getOptionValue('s');
		if (optVal != null) {
			selfAddressAndPort = optVal;
		}
		if (cmd.hasOption('N')) {
			noUPnP = true;
		}

		String[] args = cmd.getArgs();

		// parse initial contact
		if (args.length >= 1) {
			contactHost = args[0];
			join = true;

			if (args.length >= 2)
				contactPort = Integer.parseInt(args[1]);
		}

		// initialize a Mcast
		McastConfiguration config = McastFactory.getDefaultConfiguration();
		if (transport != null) config.setMessagingTransport(transport);
		if (algorithm != null) config.setRoutingAlgorithm(algorithm);
		if (routingStyle != null) config.setRoutingStyle(routingStyle);
		if (selfAddressAndPort != null) {
			MessagingUtility.HostAndPort hostAndPort =
				MessagingUtility.parseHostnameAndPort(selfAddressAndPort, config.getSelfPort());

			config.setSelfAddress(hostAndPort.getHostName());
			config.setSelfPort(hostAndPort.getPort());
		}
		if (noUPnP) config.setDoUPnPNATTraversal(false);

		if (contactPort < 0) {	// not initialized
			contactPort = config.getSelfPort();
		}

		Mcast mcast = McastFactory.getMcast(
				applicationID, applicationVersion, config, selfID);	// throws Exception

		StringBuilder sb = new StringBuilder();
		sb.append("Mcast configuration:\n");
		sb.append("  hostname:port:     ").append(mcast.getRoutingService().getSelfIDAddressPair().getAddress()).append('\n');
		sb.append("  transport type:    ").append(config.getMessagingTransport()).append('\n');
		sb.append("  routing algorithm: ").append(config.getRoutingAlgorithm()).append('\n');
		sb.append("  routing style:     ").append(config.getRoutingStyle()).append('\n');
		System.out.print(sb);

		try {
			if (statCollectorAddressAndPort != null) {
				StatConfiguration statConfig = StatFactory.getDefaultConfiguration();
					// provides the default port number of stat collector

				MessagingUtility.HostAndPort hostAndPort =
					MessagingUtility.parseHostnameAndPort(
							statCollectorAddressAndPort, statConfig.getSelfPort());

				mcast.setStatCollectorAddress(hostAndPort.getHostName(), hostAndPort.getPort());
			}

			if (join) {
				if (contactPort >= 0) {
					mcast.joinOverlay(contactHost, contactPort);
					contactString = contactHost + " : " + contactPort;
				}
				else {
					try {
						mcast.joinOverlay(contactHost);
						contactString = contactHost;
					}
					catch (IllegalArgumentException e) {	// port is not specified
						contactPort = config.getContactPort();
						mcast.joinOverlay(contactHost, contactPort);
						contactString = contactHost + ":" + contactPort;
					}
				}
			}
		}
		catch (UnknownHostException e) {
			System.err.println("A hostname could not be resolved: " + contactHost);
			e.printStackTrace();
			System.exit(1);
		}

		if (join) {
			System.out.println("  initial contact:   " + contactString);
		}

		System.out.println("A Mcast started.");
		System.out.flush();

		return mcast;
	}
}

/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.tool.visualizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import ow.routing.RoutingAlgorithmConfiguration;

/**
 * The main class of Overlay Visualizer.
 */
public final class Main {
	private final static String COMMAND = "owviz";

	private static void usage(String command) {
		System.out.println("usage: " + command
				+ " [-t UDP|TCP] [-i <ID size in bit>]"
				+ " [-s [<self address>][[:]<port>] [--no-upnp]"
				+ " [<host>[:<port>]] [<port>]");
	}

	public static void main(String[] args) {
		String transport = null;
		int idSizeInBit = RoutingAlgorithmConfiguration.DEFAULT_ID_SIZE * 8;
		String selfAddressAndPort = null;
		boolean noUPnP = false;

		// parse command-line arguments
		Options opts = new Options();
		opts.addOption("h", "help", false, "print help");
		opts.addOption("t", "transport", true, "transpoft, UDP or TCP");
		opts.addOption("i", "idsize", true, "ID size in bit");
		opts.addOption("s", "selfipaddress", true, "self IP address (and port)");
		opts.addOption("N", "no-upnp", false, "disable UPnP address port mapping");

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
		optVal = cmd.getOptionValue('t');
		if (optVal != null) {
			transport = optVal;
		}
		optVal = cmd.getOptionValue('i');
		if (optVal != null) {
			idSizeInBit = Integer.valueOf(optVal);
		}
		optVal = cmd.getOptionValue('s');
		if (optVal != null) {
			selfAddressAndPort = optVal;
		}
		if (cmd.hasOption('N')) {
			noUPnP = true;
		}

		args = cmd.getArgs();

		// parse initial contact
		String contactHostAndPort = null;
		int contactPort = -1;

		if (args.length >= 1) {
			contactHostAndPort = args[0];

			if (args.length >= 2)
				contactPort = Integer.parseInt(args[1]);
		}

		try {
			new OverlayVisualizer(
					transport, idSizeInBit, selfAddressAndPort, noUPnP,
					contactHostAndPort, contactPort);
		}
		catch (Exception e) {
			System.err.println("Failed to initialize a visualizer.");
			e.printStackTrace();
			System.exit(1);
		}
	}
}

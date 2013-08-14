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

package ow.tool.mrouted;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import ow.ipmulticast.IPMulticast;
import ow.ipmulticast.igmpd.IGMPDaemon;
import ow.ipmulticast.igmpd.IGMPDaemonConfiguration;
import ow.mcast.Mcast;
import ow.messaging.Signature;
import ow.tool.mcastshell.commands.HaltCommand;
import ow.tool.mcastshell.commands.HelpCommand;
import ow.tool.mcastshell.commands.InitCommand;
import ow.tool.mcastshell.commands.NeighborsCommand;
import ow.tool.mcastshell.commands.QuitCommand;
import ow.tool.mcastshell.commands.StatusCommand;
import ow.tool.mrouted.commands.GroupsCommand;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.MessagePrinter;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellServer;
import ow.tool.util.toolframework.AbstractMcastBasedTool;

/**
 * The main class of Application-level multicast (ALM) router.
 * This ALM router is built on the Mcast service (ow.mcast)
 * and IGMP Daemon (ow.ipmulticast.igmpd).
 */
public class Main extends AbstractMcastBasedTool {
	private final static String COMMAND = "owmrouted";	// A shell/batch script provided as bin/owmrouted
	private final int DEFAULT_SHELL_PORT = -1;

	private final static Class/*<Command<ApplicationLevelMulticastRouter>>*/[] COMMANDS = {
		InitCommand.class,
		GroupsCommand.class, NeighborsCommand.class,
		StatusCommand.class,
		HelpCommand.class,
		QuitCommand.class, HaltCommand.class
	};

	private final static List<Command<ApplicationLevelMulticastRouter>> commandList;
	private final static Map<String,Command<ApplicationLevelMulticastRouter>> commandTable;

	static {
		commandList = ShellServer.createCommandList(COMMANDS);
		commandTable = ShellServer.createCommandTable(commandList);
	}

	protected void usage(String command) {
		super.usage(command, "[-p <shell port>]"); 
	}

	public static void main(String[] args) {
		(new Main()).start(args);
	}

	protected void start(String[] args) {
		int shellPort = DEFAULT_SHELL_PORT;

		// parse command-line arguments
		Options opts = this.getInitialOptions();
		opts.addOption("p", "port", true, "port number");

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

		parser = null;
		opts = null;

		String optVal;
		optVal = cmd.getOptionValue('p');
		if (optVal != null) {
			shellPort = Integer.parseInt(optVal);
		}

		// initialize sub-components:
		// IGMP Daemon, Multicast communicator and Mcast
		IGMPDaemon igmpd = null;
		IPMulticast mcast = null;
		Mcast mcService = null;

		try {
			igmpd = new IGMPDaemon(new IGMPDaemonConfiguration());
			mcast = IPMulticast.getInstance();
			mcService = super.initialize(Signature.APPLICATION_ID_ALM_ROUTER, (short)0x10001,
					COMMAND, cmd);
		}
		catch (Exception e) {
			e.printStackTrace();
			return;
		}

		cmd = null;

		// create an instance of ALM router
		ApplicationLevelMulticastRouterConfiguration config =
			new ApplicationLevelMulticastRouterConfiguration();
		ApplicationLevelMulticastRouter almRouter = null;
		try {
			almRouter = new ApplicationLevelMulticastRouter(config, igmpd, mcast, mcService);

			almRouter.start();
		}
		catch (IOException e) {
			e.printStackTrace();
			return;
		}


		// initialize and start a ShellServer
		if (shellPort >= 0 && shellPort < 65536) {
			ShellServer<ApplicationLevelMulticastRouter> shellServ =
				new ShellServer<ApplicationLevelMulticastRouter>(
						commandTable, commandList,
						new ShowPromptPrinter(), new NoCommandPrinter(), null,
						almRouter, shellPort);
		}

		try {
			Thread.sleep(Long.MAX_VALUE);
		}
		catch (InterruptedException e) {
			System.err.println("sleep interrupted.");
		}	// sleep forever
	}

	private static class ShowPromptPrinter implements MessagePrinter {
		public void execute(PrintStream out, String hint) {
			out.print("Ready." + Shell.CRLF);
			out.flush();
		}
	}

	private static class NoCommandPrinter implements MessagePrinter {
		public void execute(PrintStream out, String hint) {
			out.print("No such command");

			if (hint != null)
				out.print(": " + hint);
			else
				out.print(".");
			out.print(Shell.CRLF);

			out.flush();
		}
	}
}

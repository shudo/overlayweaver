/*
 * Copyright 2006-2008,2012 National Institute of Advanced Industrial Science
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

package ow.tool.msgcounter;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import ow.messaging.util.AccessController;
import ow.tool.emulator.EmulatorControllable;
import ow.tool.msgcounter.commands.HaltCommand;
import ow.tool.msgcounter.commands.HelpCommand;
import ow.tool.msgcounter.commands.QuitCommand;
import ow.tool.msgcounter.commands.StatusCommand;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.MessagePrinter;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellServer;

/**
 * The main class of Message Counter.
 */
public final class Main implements EmulatorControllable {
	private final static String COMMAND = "owmsgcounter";	// A shell/batch script provided as bin/owmsgcounter
	private final int DEFAULT_SHELL_PORT = -1;

	private final static Class/*<Command<MessageCounter>>*/[] COMMANDS = {
		StatusCommand.class,
		HelpCommand.class,
		QuitCommand.class, HaltCommand.class
	};

	private final static List<Command<MessageCounter>> commandList;
	private final static Map<String,Command<MessageCounter>> commandTable;

	static {
		commandList = ShellServer.createCommandList(COMMANDS);
		commandTable = ShellServer.createCommandTable(commandList);
	}

	private Shell<MessageCounter> stdioShell;

	protected void usage(String command) {
		System.out.println("Usage: " + command
				+ " [-p <shell port>] [--acl <ACL file>] [-t UDP|TCP] [--no-upnp]"); 
	}

	public static void main(String[] args) {
		(new Main()).start(args);
	}

	protected void start(String[] args) {
		Shell<MessageCounter> stdioShell = null;

		stdioShell = this.init(args, System.in, System.out, true);

		if (stdioShell != null) {
			stdioShell.run();	// this call is blocked
		}
	}

	/**
	 * Implements {@link EmulatorControllable#invoke(String[], PrintStream)
	 * EmulatorControllableApplication#start}.
	 */
	public void invoke(String[] args, PrintStream out) {
		Shell<MessageCounter> stdioShell = this.init(args, null, out, false);
	}

	/**
	 * Implements {@link EmulatorControllable#getControlPipe()
	 * EmulatorControllable#getControlPipe}.
	 */
	public Writer getControlPipe() {
		if (this.stdioShell != null)
			return this.stdioShell.getWriter();
		else
			return null;
	}

	private Shell<MessageCounter> init(String[] args, InputStream in, PrintStream out, boolean interactive) {
		String transport = null;
		int shellPort = DEFAULT_SHELL_PORT;
		AccessController ac = null;
		boolean noUPnP = false;

		Options opts = new Options();
		opts.addOption("h", "help", false, "print help");
		opts.addOption("p", "port", true, "port number");
		opts.addOption("A", "acl", true, "access control list file");
		opts.addOption("t", "transport", true, "transport, UDP or TCP");
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
		optVal = cmd.getOptionValue('p');
		if (optVal != null) {
			shellPort = Integer.parseInt(optVal);
		}
		optVal = cmd.getOptionValue("A");
		if (optVal != null) {
			try {
				ac = new AccessController(optVal);
			}
			catch (IOException e) {
				System.err.println("An Exception thrown:");
				e.printStackTrace();
				return null;
			}
		}
		optVal = cmd.getOptionValue('t');
		if (optVal != null) {
			transport = optVal;
		}
		if (cmd.hasOption('N')) {
			noUPnP = true;
		}

		args = cmd.getArgs();

		// create a MessageCounter
		MessageCounter msgCounter = new MessageCounter(transport, noUPnP);
		try {
			msgCounter.start();
		}
		catch (Exception e) {
			System.err.println("An Exception thrown:");
			e.printStackTrace();
			return null;
		}


		// initialize and start a ShellServer
		MessagePrinter showPromptPrinter = new ShowPromptPrinter();
		MessagePrinter noCommandPrinter = new NoCommandPrinter();

		if (shellPort >= 0 && shellPort < 65536) {
			ShellServer<MessageCounter> shellServ =
				new ShellServer<MessageCounter>(commandTable, commandList,
						showPromptPrinter, noCommandPrinter, null,
						msgCounter, shellPort, ac);
		}

		Shell<MessageCounter> stdioShell =
			new Shell<MessageCounter>(in, out, commandTable, commandList,
					(interactive ? showPromptPrinter : null), noCommandPrinter, null,
					msgCounter, interactive);

		return stdioShell;
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

/*
 * Copyright 2008-2009,2012 Kazuyuki Shudo, and contributors.
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

package ow.tool.memcached;

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

import ow.dht.DHTConfiguration;
import ow.dht.DHTFactory;
import ow.dht.memcached.Item;
import ow.dht.memcached.Memcached;
import ow.messaging.Signature;
import ow.messaging.util.AccessController;
import ow.tool.emulator.EmulatorControllable;
import ow.tool.memcached.commands.AddCommand;
import ow.tool.memcached.commands.AppendCommand;
import ow.tool.memcached.commands.CasCommand;
import ow.tool.memcached.commands.DecrCommand;
import ow.tool.memcached.commands.DeleteCommand;
import ow.tool.memcached.commands.FlushAllCommand;
import ow.tool.memcached.commands.GetCommand;
import ow.tool.memcached.commands.GetsCommand;
import ow.tool.memcached.commands.HelpCommand;
import ow.tool.memcached.commands.IncrCommand;
import ow.tool.memcached.commands.InitCommand;
import ow.tool.memcached.commands.LocaldataCommand;
import ow.tool.memcached.commands.PrependCommand;
import ow.tool.memcached.commands.QuitCommand;
import ow.tool.memcached.commands.ReplaceCommand;
import ow.tool.memcached.commands.SetCommand;
import ow.tool.memcached.commands.StatsCommand;
import ow.tool.memcached.commands.StatusCommand;
import ow.tool.memcached.commands.VerbosityCommand;
import ow.tool.memcached.commands.VersionCommand;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.MessagePrinter;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellServer;
import ow.tool.util.toolframework.AbstractDHTBasedTool;

/**
 * The main class of memcached.
 */
public final class Main extends AbstractDHTBasedTool<Item>
		implements EmulatorControllable {
	public final static String VERSION = "1.2.6";	// for "version" command

	private final static String COMMAND = "owmemcached";	// A shell/batch script provided as bin/owmemcached
	private final static int DEFAULT_PORT = 11211;

	private final static Class/*Command<Memcached>*/[] COMMANDS = {
		// retrieval commands
		GetCommand.class,
		GetsCommand.class,
		// storage commands
		SetCommand.class,
		AddCommand.class,
		ReplaceCommand.class,
		AppendCommand.class,
		PrependCommand.class,
		CasCommand.class,
		// deletion
		DeleteCommand.class,
		// increment/decrement
		IncrCommand.class,
		DecrCommand.class,
		// statistics
		StatsCommand.class,		// implemented partially
		// other commands
		FlushAllCommand.class,	// does not work
		VersionCommand.class,
		VerbosityCommand.class,
		QuitCommand.class,
		// Overlay Weaver specific commands
		InitCommand.class,
		StatusCommand.class,
		LocaldataCommand.class,
		HelpCommand.class
	};

	private final static List<Command<Memcached>> commandList;
	private final static Map<String,Command<Memcached>> commandTable;

	static {
		commandList = ShellServer.createCommandList(COMMANDS);
		commandTable = ShellServer.createCommandTable(commandList);
	}

	private Shell<Memcached> stdioShell;

	protected void usage(String command) {
		super.usage(command, "[-p <shell port>] [--acl <ACL file>] [-n]"); 
	}

	public static void main(String[] args) {
		(new Main()).start(args);
	}

	protected void start(String[] args) {
		this.stdioShell = this.init(args, System.in, System.out, true);

		if (this.stdioShell != null) {
			this.stdioShell.run();	// this call is blocked
		}
	}

	/**
	 * Implements {@link EmulatorControllable#invoke(String[], PrintStream)
	 * EmulatorControllable#invoke}.
	 */
	public void invoke(String[] args, PrintStream out) {
		this.stdioShell = this.init(args, null, out, false);
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

	private Shell<Memcached> init(String[] args, InputStream in, PrintStream out, boolean interactive) {
		int shellPort = DEFAULT_PORT;
		AccessController ac = null;
		boolean disableStdin = false;

		// parse command-line arguments
		Options opts = this.getInitialOptions();
		opts.addOption("p", "port", true, "port number");
		opts.addOption("A", "acl", true, "access control list file");
		opts.addOption("n", "disablestdin", false, "disable standard input");

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
		if (cmd.hasOption('n')) {
			disableStdin = true;
		}

		// parse remaining arguments
		// and initialize a DHT
		DHTConfiguration config = DHTFactory.getDefaultConfiguration();
		config.setImplementationName("memcached");
		config.setMultipleValuesForASingleKey(false);	// Memcached holds a single value for a single key.
		config.setDoReputOnReplicas(true);	// Memcached does not support reputting by a node which initially put a key-value pair.

		Memcached dht = null;
		try {
			dht = (Memcached)super.initialize(Signature.APPLICATION_ID_MEMCACHED, (short)0x10000,
					config,
					COMMAND, cmd);
		}
		catch (Exception e) {
			System.err.println("An Exception thrown:");
			e.printStackTrace();
			return null;
		}

		cmd = null;


		// start a ShellServer
		MessagePrinter errPrinter = new ErrorPrinter();
		ShellServer<Memcached> shellServ =
			new ShellServer<Memcached>(commandTable, commandList,
					null /*prompt printer*/, errPrinter, errPrinter,
					dht, shellPort, ac);

		Shell<Memcached> stdioShell = null;
		if (disableStdin) {
			try {
				Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {}
		}
		else {
			stdioShell = new Shell<Memcached>(in, out, shellServ, dht, interactive);
		}

		return stdioShell;
	}

	private static class ErrorPrinter implements MessagePrinter {
		public void execute(PrintStream out, String hint) {
			out.print("ERROR" + Shell.CRLF);
			out.flush();
		}
	}
}

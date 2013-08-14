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

package ow.tool.emulator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import ow.messaging.MessagingFactory;
import ow.messaging.util.MessagingUtility;
import ow.messaging.util.MessagingUtility.HostAndPort;
import ow.tool.emulator.commands.ArgumentsCommand;
import ow.tool.emulator.commands.ClassCommand;
import ow.tool.emulator.commands.ControlCommand;
import ow.tool.emulator.commands.ControlseriallyCommand;
import ow.tool.emulator.commands.HaltCommand;
import ow.tool.emulator.commands.HelpCommand;
import ow.tool.emulator.commands.IncludeCommand;
import ow.tool.emulator.commands.InvokeCommand;
import ow.tool.emulator.commands.PriorityCommand;
import ow.tool.emulator.commands.QuitCommand;
import ow.tool.emulator.commands.RemoteCommand;
import ow.tool.emulator.commands.ScheduleCommand;
import ow.tool.emulator.commands.ScheduledaemonCommand;
import ow.tool.emulator.commands.TimeoffsetCommand;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.MessagePrinter;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellServer;

/**
 * The main class of distributed environment emulator.
 * This emulator hosts multiple instances of Java applications and
 * provides messaging service (ow.messaging) to them.
 * It reads commands from the specified scenario file or standard input.
 */
public class Main {
	private final static String COMMAND = "owemu";
		// A shell/batch script provided as bin/owemu

	public final static int DIST_EMU_PORT = 3997;
	public final static long DIST_EMU_REMOTE_INVOCATION_WAIT = 10 * 1000L;
	public final static String ENCODING = "US-ASCII";
	public final static long DEFAULT_WAIT_MILLIS = 2000L;

	public final static String RSH_COMMAND = "ssh";
	public final static String JAVA_COMMAND = "java";

	public final int DEFAULT_CONNECTION_PORT = 10101;

	private final static Class/*<Command<EmulatorContext>>*/[] COMMANDS = {
		ClassCommand.class,
		ArgumentsCommand.class,
		InvokeCommand.class,
		PriorityCommand.class,
		ControlCommand.class,
		ControlseriallyCommand.class,
		HaltCommand.class,
		TimeoffsetCommand.class,
		ScheduleCommand.class,
		ScheduledaemonCommand.class,
		IncludeCommand.class,
		HelpCommand.class,
		QuitCommand.class,
		RemoteCommand.class
	};

	private final static List<Command<EmulatorContext>> commandList;
	private final static Map<String,Command<EmulatorContext>> commandTable;

	static {
		commandList = ShellServer.createCommandList(COMMANDS);
		commandTable = ShellServer.createCommandTable(commandList);
	}

	private OutputRedirector outputRedirector;

	private Main() {
		this.outputRedirector = new OutputRedirector(System.out);
	}

	public static void main(String[] args) {
		Main emu = new Main();
		emu.start(args);
	}

	private static void usage() {
		System.out.print("Usage: " + COMMAND);
		System.out.println(" [-h] [--eventdriven] [-f <host list file>] [-w <str rep of host list>] [-c <connection target file>] [-s <self hostname>] [<scenario URL|file> ...]");
	}

	public void start(String[] args) {
		EmulatorMode mode = EmulatorMode.NORMAL;
		boolean eventDrivenMode = false;
		RemoteAppInstanceTable workerTable = null;
		String controlTargetListFilename = null;
		EmulatorContext emuContext = null;
		int initialHostID = 0;

		// get self hostname
		InetAddress selfHostAddress = null;
		try {
			selfHostAddress = InetAddress.getLocalHost();
		}
		catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		}

		// parse command-line arguments
		Options opts = new Options();
		opts.addOption("h", "help", false, "print help");
		opts.addOption("E", "eventdriven", false, "emulator runs in event driven mode");
		opts.addOption("f", "hostfile", true, "host file for working in master mode");
		opts.addOption("w", "workertable", true, "works in worker mode");
		opts.addOption("s", "selfipaddress", true, "self IP address (and port)");
		opts.addOption("c", "controltargetlistfile", true, "control target list file");

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
			usage();
			System.exit(1);
		}
		if (cmd.hasOption('E')) {
			eventDrivenMode = true;
		}
		optVal = cmd.getOptionValue('f');
		if (optVal != null) {
			try {
				workerTable = RemoteAppInstanceTable.readHostFile(optVal);
			}
			catch (IOException e) {
				e.printStackTrace();
				usage();
				System.exit(1);
			}

			mode = EmulatorMode.MASTER;
		}
		optVal = cmd.getOptionValue('w');
		if (optVal != null) {
			try {
				workerTable = RemoteAppInstanceTable.parseString(optVal);
			}
			catch (UnknownHostException e) {
				e.printStackTrace();
				usage();
				System.exit(1);
			}

			mode = EmulatorMode.WORKER;
		}
		optVal = cmd.getOptionValue('s');
		if (optVal != null) {
			try {
				selfHostAddress = InetAddress.getByName(optVal);
			}
			catch (UnknownHostException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
		optVal = cmd.getOptionValue('c');
		if (optVal != null) {
			controlTargetListFilename = optVal;

			mode = EmulatorMode.CONTROLLER;
		}

		args = cmd.getArgs();


		if (mode == EmulatorMode.WORKER) {
			// set initial host ID (emuXX)
			initialHostID = workerTable.getStartHostID(selfHostAddress);
		}

		// provide an EmulatorContext
		if (mode == EmulatorMode.MASTER) {
			emuContext = new EmulatorContext(
					System.out, initialHostID, workerTable, eventDrivenMode, mode);
		}
		else {
			emuContext = new EmulatorContext(
					System.out, initialHostID, new LocalAppInstanceTable(), eventDrivenMode, mode);
		}

		// connect to controlee
		if (mode == EmulatorMode.CONTROLLER) {
			try {
				this.connect(emuContext, controlTargetListFilename);
			}
			catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		// force the messaging service to use Emulator
		if (mode == EmulatorMode.WORKER) {
			MessagingFactory.forceDistributedEmulator(initialHostID, workerTable);
		}
		else {
			MessagingFactory.forceEmulator(initialHostID);
		}


		// parse scenario files
		try {
			parseScenario(emuContext, args);
		}
		catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	private void parseScenario(EmulatorContext emuContext, String[] filenames) throws IOException {
		// prepare commands
		ShellServer<EmulatorContext> shellServ =
			new ShellServer<EmulatorContext>(commandTable, commandList,
					new ShowPromptPrinter(), new NoCommandPrinter(), null,
					emuContext, -1);

		if (filenames != null && filenames.length > 0) {
			// parse scenario files
			for (int i = 0; i < filenames.length; i++) {
				InputStream in = null;

				try {
					in = (new URL(filenames[i])).openStream();
				}
				catch (IOException e) {
					in = new FileInputStream(filenames[i]);
				}

				this.parseScenario(emuContext, shellServ, in, false);

				if (in != null) in.close();
			}
		}
		else {
			// read standard input
			this.parseScenario(emuContext, shellServ, System.in, emuContext.getEmulatorMode() != EmulatorMode.WORKER);
		}
	}

	private void parseScenario(EmulatorContext emuContext, ShellServer<EmulatorContext> shServer, InputStream in, boolean interactive) {
		Shell<EmulatorContext> shell = new Shell<EmulatorContext>(in, System.out, shServer, emuContext, interactive);
		shell.run();
	}

	private void connect(EmulatorContext emuContext, String targetListFile) throws IOException {
		InputStream in = null;

		try {
			in = (new URL(targetListFile)).openStream();
		}
		catch (IOException e) {
			in = new FileInputStream(targetListFile);
		}

		BufferedReader bin =
			new BufferedReader(new InputStreamReader(in, ENCODING));

		int hostID = 0;
		String aLine;
		while ((aLine = bin.readLine()) != null) {
			HostAndPort hostPort =
				MessagingUtility.parseHostnameAndPort(aLine, DEFAULT_CONNECTION_PORT);

			System.out.println("connect to " + hostPort);
			Socket sock = null;
			try {
				sock = new Socket(hostPort.getHostAddress(), hostPort.getPort());
			}
			catch (IOException e) {
				System.out.println("Could not connect to " + hostPort);
				throw e;
			}

			// start an output redirector
			this.outputRedirector.redirect(sock.getInputStream(), "Redirector for " + hostPort);

			// register pipe
			emuContext.setControlPipe(hostID++,
					new BufferedWriter(new OutputStreamWriter(sock.getOutputStream(), ENCODING)),
					null);
		}
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

/*
 * Copyright 2006-2009,2012 National Institute of Advanced Industrial Science
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

package ow.tool.dhtshell;

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

import ow.dht.DHT;
import ow.dht.DHTFactory;
import ow.messaging.Signature;
import ow.messaging.util.AccessController;
import ow.oasis.OASISResponder;
import ow.tool.dhtshell.commands.ClearCommand;
import ow.tool.dhtshell.commands.GetCommand;
import ow.tool.dhtshell.commands.HaltCommand;
import ow.tool.dhtshell.commands.HelpCommand;
import ow.tool.dhtshell.commands.InitCommand;
import ow.tool.dhtshell.commands.LocaldataCommand;
import ow.tool.dhtshell.commands.PutCommand;
import ow.tool.dhtshell.commands.QuitCommand;
import ow.tool.dhtshell.commands.RemoveCommand;
import ow.tool.dhtshell.commands.ResumeCommand;
import ow.tool.dhtshell.commands.SetSecretCommand;
import ow.tool.dhtshell.commands.SetTTLCommand;
import ow.tool.dhtshell.commands.StatusCommand;
import ow.tool.dhtshell.commands.SuspendCommand;
import ow.tool.emulator.EmulatorControllable;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.MessagePrinter;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellServer;
import ow.tool.util.toolframework.AbstractDHTBasedTool;

/**
 * The main class of DHT shell server.
 * This shell is an utility to use/test a DHT.
 */
public final class Main extends AbstractDHTBasedTool<String>
		implements EmulatorControllable {
	private final static String COMMAND = "owdhtshell";	// A shell/batch script provided as bin/owdhtshell
	private final static int SHELL_PORT = -1;
	public final static int XMLRPC_PORT_DIFF = +1;
	private final static int XMLRPC_PORT_RANGE = 100;

	public final static String ENCODING = "UTF-8";

	private final static Class/*Command<<DHT<String>>>*/[] COMMANDS = {
		StatusCommand.class,
		InitCommand.class,
		GetCommand.class, PutCommand.class, RemoveCommand.class,
		SetTTLCommand.class, SetSecretCommand.class,
		LocaldataCommand.class,
//		SourceCommand.class,
		HelpCommand.class,
		QuitCommand.class,
		HaltCommand.class,
		ClearCommand.class,
		SuspendCommand.class, ResumeCommand.class
	};

	private final static List<Command<DHT<String>>> commandList;
	private final static Map<String,Command<DHT<String>>> commandTable;

	static {
		commandList = ShellServer.createCommandList(COMMANDS);
		commandTable = ShellServer.createCommandTable(commandList);
	}

	private Shell<DHT<String>> stdioShell;

	protected void usage(String command) {
		super.usage(command, "[-p <shell port>] [--acl <ACL file>] [-n] [--web] [--showmap] [--oasis]"); 
	}

	public static void main(String[] args) {
		(new Main()).start(args);
	}

	private void start(String[] args) {
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
			return stdioShell.getWriter();
		else
			return null;
	}

	private Shell<DHT<String>> init(String[] args, InputStream in, PrintStream out, boolean interactive) {
		int shellPort = SHELL_PORT;
		AccessController ac = null;
		boolean disableStdin = false;
		boolean invokeXMLRPCServer = false;
		boolean showMapOnWebInterface = false;
		int oasisPort = -1;

		// parse command-line arguments
		Options opts = this.getInitialOptions();
		opts.addOption("p", "port", true, "port number");
		opts.addOption("A", "acl", true, "access control list file");
		opts.addOption("n", "disablestdin", false, "disable standard input");
		opts.addOption("W", "web", false, "invoke a web/XML-RPC server");
		opts.addOption("M", "showmap", false, "show a Google Map on a web interface");
		opts.addOption("O", "oasis", true, "invoke an OASIS responder");

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
		if (cmd.hasOption('W')) {
			invokeXMLRPCServer = true;
		}
		if (cmd.hasOption('M')) {
			showMapOnWebInterface = true;
		}
		optVal = cmd.getOptionValue("O");
		if (optVal != null) {
			oasisPort = Integer.parseInt(optVal);
		}

		// parse remaining arguments
		// and initialize a DHT
		DHT<String> dht = null;
		try {
			dht = super.initialize(Signature.APPLICATION_ID_DHT_SHELL, (short)0x10000,
					DHTFactory.getDefaultConfiguration(),
					COMMAND, cmd);
		}
		catch (Exception e) {
			System.err.println("An Exception thrown:");
			e.printStackTrace();
			return null;
		}

		cmd = null;

		// start an XML-RPC server
		XmlRpcDHTServer rpcServer = null;
		if (invokeXMLRPCServer) {
			rpcServer = new XmlRpcDHTServer(dht, showMapOnWebInterface);
			int xmlrpcPort = dht.getConfiguration().getContactPort()
					+ XMLRPC_PORT_DIFF;
			try {
				String url = rpcServer.start(xmlrpcPort, XMLRPC_PORT_RANGE);

				System.out.println("Web and XML-RPC server: " + url);
			}
			catch (Exception e) {
				System.err.println("Failed to start a web server.");
				e.printStackTrace();
				return null;
			}
		}


		// start an OASIS responder
		if (oasisPort >= 1 && oasisPort < 65535) {
			OASISResponder s = null;
			try {
				s = new OASISResponder(oasisPort);
			}
			catch (IOException e) {
				System.err.println("Failed to start an OASIS responder on port " + oasisPort);
				e.printStackTrace();
				return null;
			}

			Thread t = new Thread(s);
			t.setName("OASIS responder");
			t.setDaemon(true);
			t.start();

			System.out.println("OASIS responder: port " + oasisPort);
		}


		// start a ShellServer
		ShellServer<DHT<String>> shellServ =
			new ShellServer<DHT<String>>(commandTable, commandList,
					new ShowPromptPrinter(), new NoCommandPrinter(), null,
					dht, shellPort, ac);
		if (rpcServer != null)
			shellServ.addInterruptible(rpcServer);	// interrupted by "halt" command

		Shell<DHT<String>> stdioShell = null;
		if (disableStdin) {
			try {
				Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {}
		}
		else {
			stdioShell = new Shell<DHT<String>>(in, out, shellServ, dht, interactive);
		}

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

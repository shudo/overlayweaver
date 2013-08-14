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

package ow.tool.mcastshell;

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

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.mcast.Mcast;
import ow.mcast.McastCallback;
import ow.mcast.SpanningTreeChangedCallback;
import ow.messaging.Signature;
import ow.messaging.util.AccessController;
import ow.tool.emulator.EmulatorControllable;
import ow.tool.mcastshell.commands.ClearCommand;
import ow.tool.mcastshell.commands.GroupsCommand;
import ow.tool.mcastshell.commands.HaltCommand;
import ow.tool.mcastshell.commands.HelpCommand;
import ow.tool.mcastshell.commands.InitCommand;
import ow.tool.mcastshell.commands.JoinCommand;
import ow.tool.mcastshell.commands.LeaveCommand;
import ow.tool.mcastshell.commands.LeaveallCommand;
import ow.tool.mcastshell.commands.MulticastCommand;
import ow.tool.mcastshell.commands.NeighborsCommand;
import ow.tool.mcastshell.commands.QuitCommand;
import ow.tool.mcastshell.commands.ResumeCommand;
import ow.tool.mcastshell.commands.StatusCommand;
import ow.tool.mcastshell.commands.SuspendCommand;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.MessagePrinter;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellServer;
import ow.tool.util.toolframework.AbstractMcastBasedTool;

/**
 * The main class of Mcast shell server.
 * This shell is an utility to use/test Mcast.
 */
public final class Main extends AbstractMcastBasedTool
		implements EmulatorControllable {
	private final static String COMMAND = "owmcastshell";	// A shell/batch script provided as bin/owmcastshell
	private final static int SHELL_PORT = -1;

	private final static Class/*<Command<Mcast>>*/[] COMMANDS = {
		StatusCommand.class,
		InitCommand.class,
		JoinCommand.class, LeaveCommand.class, LeaveallCommand.class,
		MulticastCommand.class,
		GroupsCommand.class, NeighborsCommand.class,
//		SourceCommand.class,
		HelpCommand.class,
		QuitCommand.class,
		HaltCommand.class,
		ClearCommand.class,
		SuspendCommand.class, ResumeCommand.class,
	};

	private final static List<Command<Mcast>> commandList;
	private final static Map<String,Command<Mcast>> commandTable;

	static {
		commandList = ShellServer.createCommandList(COMMANDS);
		commandTable = ShellServer.createCommandTable(commandList);
	}

	private Shell<Mcast> stdioShell;

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

	private Shell<Mcast> init(String[] args, InputStream in, PrintStream out, boolean interactive) {
		int shellPort = SHELL_PORT;
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
		// and initialize Mcast
		Mcast mcast = null;
		try {
			mcast = super.initialize(Signature.APPLICATION_ID_GROUP_MANAGER_SHELL, (short)0x10001,
					COMMAND, cmd);
		}
		catch (Exception e) {
			System.err.println("An Exception thrown:");
			e.printStackTrace();
			return null;
		}

		cmd = null;


		// initialize and start a ShellServer
		ShellServer<Mcast> shellServ =
			new ShellServer<Mcast>(commandTable, commandList,
					new ShowPromptPrinter(), new NoCommandPrinter(), null,
					mcast, shellPort, ac);

		// register callbacks
		prepareMessageReceiver(mcast, shellServ);
		prepareSpanningTreeChangeObserver(mcast, shellServ);

		Shell<Mcast> stdioShell = null;
		if (disableStdin) {
			try {
				Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {}
		}
		else {
			stdioShell = new Shell<Mcast>(in, out, shellServ, mcast, interactive);
		}

		return stdioShell;
	}

	private static void prepareMessageReceiver(Mcast mcast, final ShellServer<Mcast> shellServ) {
		McastCallback msgReceiver = new McastCallback() {
			public void received(ID groupID, java.io.Serializable payload) {
				ShellServer.println("Message to group " + groupID + ":\n" + payload);
			}
		};
		mcast.addMulticastCallback(msgReceiver);
	}

	private static void prepareSpanningTreeChangeObserver(Mcast mcast, final ShellServer<Mcast> shellServ) {
		SpanningTreeChangedCallback callback = new SpanningTreeChangedCallback() {
			public void topologyChanged(ID groupID, IDAddressPair parent, IDAddressPair[] children) {
				StringBuilder sb = new StringBuilder();

				sb.append("Spanning tree changed:");
				sb.append("\n group ID: ");
				sb.append(groupID);
				sb.append("\n parent:   ");
				if (parent != null) {
					sb.append(parent.getAddress());
				}
				else {
					sb.append("NONE");
				}
				sb.append("\n children:");
				if (children != null) {
					for (IDAddressPair p: children) {
						if (p != null) {
							sb.append(" ").append(p.getAddress());
						}
					}
				}
				else {
					sb.append(" NONE");
				}

				ShellServer.println(sb.toString());
			}
		};
		mcast.addSpanningTreeChangedCallback(callback);
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

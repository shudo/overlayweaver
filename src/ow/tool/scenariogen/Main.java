/*
 * Copyright 2006,2008 National Institute of Advanced Industrial Science
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

package ow.tool.scenariogen;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import ow.tool.scenariogen.commands.ChurnCommand;
import ow.tool.scenariogen.commands.GetCommand;
import ow.tool.scenariogen.commands.HaltCommand;
import ow.tool.scenariogen.commands.HelpCommand;
import ow.tool.scenariogen.commands.JoinCommand;
import ow.tool.scenariogen.commands.MputgetCommand;
import ow.tool.scenariogen.commands.OutCommand;
import ow.tool.scenariogen.commands.PutCommand;
import ow.tool.scenariogen.commands.QuitCommand;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.MessagePrinter;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellServer;

/**
 * The main class of scenario generator.
 */
public class Main {
	private final static String COMMAND = "owscenariogen";
		// A shell/batch script provided as bin/owscenariogen
	public final static String ENCODING = "UTF-8";
	public final static int DEFAULT_TTL = 604800;
	public final static String DEFAULT_SCENARIO_FILENAME = "scenario"; 

	private final static Class/*<Command<ScenarioGeneratorContext>>*/[] COMMANDS = {
		HelpCommand.class,
		JoinCommand.class,
		PutCommand.class, GetCommand.class,
		MputgetCommand.class,
		HaltCommand.class,
		ChurnCommand.class,
		OutCommand.class,
		QuitCommand.class
	};

	private final static List<Command<ScenarioGeneratorContext>> commandList;
	private final static Map<String,Command<ScenarioGeneratorContext>> commandTable;

	static {
		commandList = ShellServer.createCommandList(COMMANDS);
		commandTable = ShellServer.createCommandTable(commandList);
	}

	public static void main(String[] args) {
		Main gen = new Main();
		try {
			gen.start(args);
		}
		catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void usage() {
		System.out.print("Usage: " + COMMAND);
		System.out.println(" <# of nodes> [<output file>]");
	}

	private BufferedWriter out;

	public void start(String[] args) throws IOException {
		if (args.length < 1) {
			usage();
			System.exit(1);
		}

		int numNodes = Integer.parseInt(args[0]);

		ScenarioGeneratorContext context = new ScenarioGeneratorContext(numNodes);

		String fileName = DEFAULT_SCENARIO_FILENAME;
		if (args.length >= 2) fileName = args[1];
		context.setWriter(
			new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), ENCODING))));

		// start a shell
		Shell<ScenarioGeneratorContext> shell =
			new Shell<ScenarioGeneratorContext>(System.in, System.out,
					commandTable, commandList,
					new ShowPromptPrinter(), new NoCommandPrinter(), null,
					context, true);
		shell.run();
	}

	public BufferedWriter getWriter() {
		return this.out;
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

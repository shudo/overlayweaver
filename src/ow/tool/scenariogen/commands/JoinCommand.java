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

package ow.tool.scenariogen.commands;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Random;

import ow.tool.scenariogen.ScenarioGeneratorContext;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class JoinCommand implements Command<ScenarioGeneratorContext> {
	private final static String[] NAMES = {"join"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "join <start time (ms)> <interval (ms)> [random_contact_with_3rd_arg]";
	}

	public boolean execute(ShellContext<ScenarioGeneratorContext> context) {
		ScenarioGeneratorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		// parse arguments
		if (args.length < 2) {
			out.println("Usage: " + this.getHelp());
			return false;
		}

		long startTime = (long)Double.parseDouble(args[0]);
		long interval = (long)Double.parseDouble(args[1]);
		int numNodes = cxt.getNumberOfNodes();
		Random rnd = null;
		if (args.length >= 3) {
			rnd = new Random();
		}

		// notify ScenarioGeneratorContext of the number of nodes
		cxt.setNumberOfNodes(numNodes);
		cxt.setJoinStatusToAllNodes(true);

		// write
		PrintWriter writer = cxt.getWriter();

		long time = startTime;
		for (int i = 1; i < numNodes; i++) {
			cxt.setJoinStatus(i, true);

			int contact = 1;
			if (rnd != null) { contact  += rnd.nextInt(i); }

			// e.g. schedule 1000 controls 2 init emu1
			writer.print("schedule ");
			writer.print(time);
			writer.print(" controls ");	// "controlserially" command
			writer.print(i + 1);
			writer.print(" init emu");
			writer.print(contact);
			writer.println();

			time += interval;
		}
		writer.flush();

		out.println("finish at " + ((double)time / 1000.0));

		return false;
	}
}

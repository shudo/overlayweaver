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

package ow.tool.scenariogen.commands;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Random;

import ow.tool.scenariogen.ScenarioGeneratorContext;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class PutCommand implements Command<ScenarioGeneratorContext> {
	private final static String[] NAMES = {"put"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "put <start time (ms)> <interval (ms)> <# of puts>";
	}

	public boolean execute(ShellContext<ScenarioGeneratorContext> context) {
		ScenarioGeneratorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		// parse arguments
		if (args.length < 3) {
			out.println("Usage: " + this.getHelp());
			return false;
		}

		long startTime = (long)Double.parseDouble(args[0]);
		long interval = (long)Double.parseDouble(args[1]);
		int repeat = Integer.parseInt(args[2]);

		// write out
		PrintWriter writer = cxt.getWriter();
		Random rnd = new Random();
		int numNodes = cxt.getNumberOfNodes();

		long time = startTime;
		for (int i = 0; i < repeat; i++) {
			// e.g. schedule 1000 control 2 put k0 v0
			StringBuilder sb = new StringBuilder();
			sb.append("schedule ");
			sb.append(time);
			sb.append(" control ");
			sb.append(rnd.nextInt(numNodes) + 1);
			sb.append(" put k");
			sb.append(i);
			sb.append(" v");
			sb.append(i);
			writer.println(sb.toString());

			time += interval;
		}
		writer.flush();

		out.println("finish at " + ((double)time / 1000.0));

		return false;
	}
}

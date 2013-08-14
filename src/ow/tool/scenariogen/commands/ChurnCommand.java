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

public final class ChurnCommand implements Command<ScenarioGeneratorContext> {
	private final static String[] NAMES = {"churn"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "churn <start time (sec)> <end time (sec)> <frequency (times/sec)>";
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

		long startTime = (long)(Double.parseDouble(args[0]) * 1000.0);
		long endTime = (long)(Double.parseDouble(args[1]) * 1000.0);
		double freq = 1000.0 / Double.parseDouble(args[2]);

		if (startTime >= endTime || freq <= 0.0) {
			out.println("Invalid arguments.");
			return false;
		}

		// write
		PrintWriter writer = cxt.getWriter();
		int numNodes = cxt.getNumberOfNodes();
		Random rnd = new Random();

		double time = (double)startTime;
		while (true) {
			double timeStep = -Math.log(1 - rnd.nextDouble());
			timeStep *= freq;
			time += timeStep;

			if (time >= endTime) break;

			int n = rnd.nextInt(numNodes - 1) + 2;	// from 2 to numNodes
			long t = (long)time;

			writer.print("schedule ");
			writer.print(t);
			writer.print(" control ");
			writer.print(n);
			writer.println(" halt");

			writer.print("schedule ");
			writer.print(t + 1);
			writer.print(" invoke ");
			writer.println(n);

			writer.print("schedule ");
			writer.print(t + 6);
			writer.print(" control ");
			writer.print(n);
			writer.println(" init emu1");
		}
		writer.flush();

		return false;
	}
}

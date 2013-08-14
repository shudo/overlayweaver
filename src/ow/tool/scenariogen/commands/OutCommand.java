/*
 * Copyright 2008 Kazuyuki Shudo, and contributors.
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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;

import ow.tool.scenariogen.Main;
import ow.tool.scenariogen.ScenarioGeneratorContext;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public class OutCommand implements Command<ScenarioGeneratorContext> {
	private final static String[] NAMES = {"out"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "out <file name>";
	}

	public boolean execute(ShellContext<ScenarioGeneratorContext> context) {
		ScenarioGeneratorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		// parse arguments
		if (args.length < 1) {
			out.println("Usage: " + this.getHelp());
			return false;
		}

		Writer old = cxt.getWriter();

		String fileName = args[0];
		try {
			cxt.setWriter(new PrintWriter(new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(fileName), Main.ENCODING))));
		}
		catch (IOException e) {
			out.println("Could not open: " + args[0]);
			return false;
		}

		try {
			old.close();
		}
		catch (IOException e) {
			out.println("Failed to close.");
		}

		return false;
	}	
}

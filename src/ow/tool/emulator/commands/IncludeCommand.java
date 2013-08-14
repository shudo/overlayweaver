/*
 * Copyright 2006-2008 Kazuyuki Shudo, and contributors.
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

package ow.tool.emulator.commands;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import ow.tool.emulator.EmulatorContext;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class IncludeCommand implements Command<EmulatorContext> {
	private final static String[] NAMES = {"include"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "include <file name>";
	}

	public boolean execute(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1) {
			out.println("include command requires an argument.");
			if (context.isInteractive())
				return false;
			else
				return true;
		}

		// open the specified file
		InputStream in = null;
		try {
			in = new FileInputStream(args[0]);
		}
		catch (IOException e) {
			out.println("Could not open file: " + args[0]);
		}

		// run a sub shell
		Shell<EmulatorContext> subShell =
			new Shell<EmulatorContext>(in, out, context.getShellServer(), cxt, false);

		subShell.run();

		return false;
	}
}

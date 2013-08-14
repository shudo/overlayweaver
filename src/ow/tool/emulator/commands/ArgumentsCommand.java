/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.tool.emulator.commands;

import java.io.PrintStream;

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorMode;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class ArgumentsCommand implements Command<EmulatorContext> {
	private final static String[] NAMES = {"arguments"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "arguments [<argument> ...]";
	}

	public boolean execute(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		cxt.setCurrentArguments(args);

		if (cxt.getEmulatorMode() != EmulatorMode.WORKER
				&& cxt.getVerboseInParsing()) {
			out.print("arguments:");
			for (String arg: args) {
				out.print(" ");
				out.print(arg);
			}
			out.println();
			out.flush();
		}

		return false;
	}
}

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
import java.util.List;

import ow.tool.emulator.EmulatorContext;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class HelpCommand implements Command<EmulatorContext> {
	private final static String[] NAMES = {"help", "?"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "help|?";
	}

	public boolean execute(ShellContext<EmulatorContext> context) {
		PrintStream out = context.getOutputStream();
		List<Command<EmulatorContext>> commandList = context.getCommandList();

		for (Command<EmulatorContext> command: commandList) {
			out.println(command.getHelp());
		}
		out.flush();

		return false;
	}
}

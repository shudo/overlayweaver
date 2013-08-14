/*
 * Copyright 2006-2008,2010 National Institute of Advanced Industrial Science
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
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class TimeoffsetCommand implements Command<EmulatorContext> {
	private final static String[] NAMES = {"timeoffset"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "timeoffset [+]<offset>";
	}

	public boolean execute(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1) {
			out.println("\"timeoffset\" command needs an argument.");

			if (context.isInteractive())
				return false;
			else
				return true;
		}

		boolean timeIsDifferential = false;

		String timeStr = args[0];
		if (timeStr.startsWith("+")) {	// +<start time>
			timeIsDifferential = true;
			timeStr = timeStr.substring(1);
		}

		long time = Long.parseLong(timeStr);

		if (timeIsDifferential)
			cxt.advanceCurrentTimeOffset(time);
		else
			cxt.setCurrentTimeOffset(time);

		return false;
	}
}

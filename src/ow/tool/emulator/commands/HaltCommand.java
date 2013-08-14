/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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
import ow.tool.emulator.EmulatorTask;
import ow.tool.emulator.SchedulableCommand;
import ow.tool.emulator.action.HaltTask;
import ow.tool.util.shellframework.ShellContext;

public final class HaltCommand implements SchedulableCommand<EmulatorContext> {
	private final static String[] NAMES = {"halt"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "halt";
	}

	public boolean execute(ShellContext<EmulatorContext> context) {
		EmulatorTask task = this.getEmulatorTask(context);
		task.run();

		return true;
	}

	public EmulatorTask getEmulatorTask(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();

		return new HaltTask(cxt, out);
			// This HaltTask instance sends "halt" command to all workers
	}
}

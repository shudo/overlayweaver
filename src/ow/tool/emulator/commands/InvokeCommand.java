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
import java.util.TimerTask;

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorMode;
import ow.tool.emulator.EmulatorTask;
import ow.tool.emulator.SchedulableCommand;
import ow.tool.emulator.action.InvocationTask;
import ow.tool.util.shellframework.ShellContext;

public final class InvokeCommand implements SchedulableCommand<EmulatorContext> {
	private final static String[] NAMES = {"invoke"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "invoke [<ID of app instace>]";
	}

	public boolean execute(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		String[] args = context.getArguments();
		PrintStream out = context.getOutputStream();

		TimerTask task = this.getEmulatorTask(context);

		if (task == null) {
			out.println("Usage: " + this.getHelp());

			if (context.isInteractive())
				return false;
			else
				return true;
		}

		if (cxt.getEmulatorMode() != EmulatorMode.WORKER
				&& cxt.getVerboseInParsing()) {
			out.print("invoke");

			Class c = cxt.getCurrentClass();
			if (c != null) {
				out.print(": " + c.getName());
			}
			out.println(".");
		}

		task.run();

		return false;
	}

	public EmulatorTask getEmulatorTask(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		String[] args = context.getArguments();

		int hostID = -1;
		if (args.length >= 1) {
			hostID = Integer.parseInt(args[0]);
		}

		return new InvocationTask(cxt, hostID,
				cxt.getCurrentClass(), cxt.getCurrentMainMethod(), cxt.getCurrentArguments(),
				cxt.getCurrentRelativePriority());
	}
}

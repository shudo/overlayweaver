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
import java.util.TimerTask;

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorMode;
import ow.tool.emulator.EmulatorTask;
import ow.tool.emulator.SchedulableCommand;
import ow.tool.emulator.action.ControlTask;
import ow.tool.util.shellframework.ShellContext;

public abstract class AbstractControlCommand implements SchedulableCommand<EmulatorContext> {
	public boolean execute(ShellContext<EmulatorContext> context) throws Exception {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

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
			out.print("control " + args[0] + ":");
			for (int i = 1; i < args.length; i++) out.print(" " + args[i]);
			out.println();

			out.flush();
		}

		task.run();

		return false;
	}

	protected abstract boolean executedConcurrently();

	public EmulatorTask getEmulatorTask(ShellContext<EmulatorContext> context) throws Exception {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 2) {
			return null;
		}

		int id;
		if (args[0].toLowerCase().equals("all")) {
			id = -1;
		}
		else {
			id = Integer.parseInt(args[0]);
		}

		StringBuilder sb = new StringBuilder();
		sb.append(args[1]);
		for (int i = 2; i < args.length; i++) {
			sb.append(" ");
			sb.append(args[i]);
		}
		String command = sb.toString();

		return new ControlTask(cxt, id, out, command, this.executedConcurrently());
	}
}

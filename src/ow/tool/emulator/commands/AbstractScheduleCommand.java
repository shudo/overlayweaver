/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorMode;
import ow.tool.emulator.EmulatorTask;
import ow.tool.emulator.SchedulableCommand;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public abstract class AbstractScheduleCommand implements Command<EmulatorContext> {
	public abstract String[] getNames();

	public abstract String getHelp();

	abstract void schedule(EmulatorContext cxt,
			long time, long interval, int times, EmulatorTask task,
			boolean timeIsDifferential);

	public boolean execute(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 2) {
			out.println("Usage: " + this.getHelp());

			if (context.isInteractive())
				return false;
			else
				return true;
		}

		// parse arguments
		long startTime = 0L, interval = 0L;
		int times = 1;
		boolean timeIsDifferential = false;

		StringTokenizer st = new StringTokenizer(args[0], ",");

		String timeStr = st.nextToken();
		if (timeStr.startsWith("+")) {	// +<start time>
			timeIsDifferential = true;
			timeStr = timeStr.substring(1);
		}
		startTime = parseLong(timeStr);

		try {
			interval = parseLong(st.nextToken());
			times = 0;	// indicates endless repeat

			times = Integer.parseInt(st.nextToken());
		}
		catch (NoSuchElementException e) {}

		// parse the another command
		Map<String,Command<EmulatorContext>> commandTable =
			context.getShellServer().getCommandTable();

		Command<EmulatorContext> command = commandTable.get(args[1]);
		if (command == null) {
			out.println("No such command: " + args[1]);
			if (context.isInteractive())
				return false;
			else
				return true;
		}

		SchedulableCommand<EmulatorContext> schedulableCommand = null;
		if (command instanceof SchedulableCommand/*<EmulatorContext>*/) {
			schedulableCommand = (SchedulableCommand<EmulatorContext>)command;
		}
		else {
			out.println("Command is not schedulable: " + args[1]);
			if (context.isInteractive())
				return false;
			else
				return true;
		}

		// schedule
		String[] taskArgs = new String[args.length - 2];
		System.arraycopy(args, 2, taskArgs, 0, taskArgs.length);
		context.setArguments(taskArgs);

		EmulatorTask task = null;
		try {
			task = schedulableCommand.getEmulatorTask(context);
		}
		catch (Exception e) {
			out.println("An exception thrown during parsing command: " + args[1]);
			if (context.isInteractive())
				return false;
			else
				return true;
		}

		this.schedule(cxt, startTime, interval, times, task, timeIsDifferential);

		if (cxt.getEmulatorMode() != EmulatorMode.WORKER
				&& cxt.getVerboseInParsing()) {
			out.print("schedule: " + schedulableCommand.getNames()[0]);
			for (int i = 0; i < taskArgs.length; i++) {
				out.print(" " + taskArgs[i]);
			}
			out.println();
		}

		return false;
	}

	private long parseLong(String str) {
		long ret;

		str = str.toLowerCase();
		if (str.startsWith("inf"))
			ret = Long.MAX_VALUE >> 1;
		else
			ret = Long.parseLong(str);

		return ret;
	}
}

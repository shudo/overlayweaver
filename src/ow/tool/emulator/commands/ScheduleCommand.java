/*
 * Copyright 2008-2009 Kazuyuki Shudo, and contributors.
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

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorTask;

public final class ScheduleCommand extends AbstractScheduleCommand {
	private final static String[] NAMES = {"schedule"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "schedule [+]<time (ms)>[,<interval>[,<times>]] <another command> [...]";
	}

	void schedule(EmulatorContext cxt,
			long relativeTime, long interval, int times, EmulatorTask task,
			boolean timeIsDifferential) {
		cxt.scheduleTask(relativeTime, interval, times, task, timeIsDifferential, false);
	}
}

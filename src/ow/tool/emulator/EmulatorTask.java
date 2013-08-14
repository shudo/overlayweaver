/*
 * Copyright 2007,2009 Kazuyuki Shudo, and contributors.
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

package ow.tool.emulator;

import java.util.TimerTask;


public abstract class EmulatorTask extends TimerTask {
	protected EmulatorContext cxt;

	protected EmulatorTask(EmulatorContext cxt) {
		this.cxt = cxt;
	}

	protected EmulatorTask(EmulatorTask task) {	// for clone()
		this.cxt = task.cxt;
	}

	public abstract void run();

	public abstract EmulatorTask cloneTask();

	public abstract boolean doesExit();

	/**
	 * This task is executed in another thread if true,
	 * and executed by the Timer thread directly if false.
	 * */
	public abstract boolean executedConcurrently();
}

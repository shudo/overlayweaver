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

package ow.tool.emulator.action;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Collection;

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorMode;
import ow.tool.emulator.EmulatorTask;
import ow.tool.util.shellframework.Shell;

public class HaltTask extends EmulatorTask {
	private PrintStream out;

	public HaltTask(EmulatorContext cxt, PrintStream out) {
		super(cxt);

		this.out = out;
	}

	public HaltTask(HaltTask task) {	// for clone()
		super(task);

		this.out = task.out;
	}

	public String toString() {
		return "halt";
	}

	public EmulatorTask cloneTask() {
		return new HaltTask(this);
	}

	public boolean doesExit() { return true; }

	public boolean executedConcurrently() { return false; }

	public void run() {
		if (this.cxt.getEmulatorMode() != EmulatorMode.WORKER) {
			this.out.print("halt" + Shell.CRLF);
		}

		// send "halt" command to all workers
		try {
			Collection<Writer> controlPipes = this.cxt.getAllControlPipes();

			for (Writer controlPipe: controlPipes) {
				controlPipe.write("halt\n");
				controlPipe.flush();
			}
		}
		catch (IOException e) {
			this.out.print("An IOException thrown while writing a command into a pipe:" + Shell.CRLF);
			e.printStackTrace(out);
		}

		// stop the Timer to exit immediately
		this.cxt.haltSchedulingTimer();

		return;
	}
}

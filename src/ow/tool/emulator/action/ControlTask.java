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
import java.util.Date;

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorMode;
import ow.tool.emulator.EmulatorTask;
import ow.tool.util.shellframework.Shell;

public class ControlTask extends EmulatorTask {
	private int id;
	private PrintStream out;
	private String command;
	private boolean executedConcurrently;

	public ControlTask(EmulatorContext cxt, int id, PrintStream out, String command, boolean executedConcurrently) {
		super(cxt);

		this.id = id;
		this.out = out;
		this.command = command;
		this.executedConcurrently = executedConcurrently;
	}

	public ControlTask(ControlTask task) {	// for clone()
		super(task);

		this.id = task.id;
		this.out = task.out;
		this.command = task.command;
		this.executedConcurrently = task.executedConcurrently;
	}

	public String getCommand() { return this.command; } // just for debug

	public String toString() {
		return "control " + (this.id < 0 ? "all" : this.id) + " " + command;
	}

	public EmulatorTask cloneTask() {
		return new ControlTask(this);
	}

	public boolean doesExit() { return false; }

	public boolean executedConcurrently() { return this.executedConcurrently; }

	public void run() {
		Date d = new Date();

		if (this.cxt.getEmulatorMode() != EmulatorMode.WORKER) {
			this.out.print("control " +
					(this.id < 0 ? "all" : this.id) +
					" (" + d.toString() + "): " + command + Shell.CRLF);
			this.out.flush();
		}

		if (this.id < 0) {	// write to all nodes
			String prefix;
			if (this.cxt.getEmulatorMode() == EmulatorMode.MASTER)
				prefix = "control all ";
			else
				prefix = "";

			try {
				Collection<Writer> controlPipes = this.cxt.getAllControlPipes();

				for (Writer controlPipe: controlPipes) {
					controlPipe.write(prefix + this.command + "\n");
					controlPipe.flush();
				}
			}
			catch (IOException e) {
				this.out.print("An IOException thrown while writing a command into a pipe:" + Shell.CRLF);
				e.printStackTrace(this.out);
				this.out.flush();
			}
		}
		else {	// write to a single node
			Writer controlPipe = this.cxt.getControlPipe(this.id);
			if (controlPipe == null) {
				this.out.println("There is no application instance with id: " + id);
				this.out.flush();
				return;
			}

			String prefix;
			if (this.cxt.getEmulatorMode() == EmulatorMode.MASTER)
				prefix = "control " + this.id + " ";
			else
				prefix = "";

			try {
				controlPipe.write(prefix + this.command + "\n");
				controlPipe.flush();
			}
			catch (IOException e) {
				this.out.print("An IOException thrown while writing a command into a pipe:" + Shell.CRLF);
				e.printStackTrace(this.out);
				this.out.flush();
			}
		}
	}
}

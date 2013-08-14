/*
 * Copyright 2006-2009,2012 National Institute of Advanced Industrial Science
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

package ow.tool.emulator;

import java.io.PrintStream;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Collection;

import ow.util.Timer;

public class EmulatorContext {
	private PrintStream out;

	protected EmulatorContext(
			PrintStream out, int initialHostID, AppInstanceTable pipeTable,
			boolean eventDrivenMode, EmulatorMode mode) {
		this.out = out;
		this.nextID = initialHostID;
		this.appInstanceTable = pipeTable;
		this.emulatorMode = mode;

		this.timer = Timer.getSingletonTimer();
//			new Timer("A Timer in Emulator");
		this.timer.setEventDrivenMode(eventDrivenMode);
	}

	public PrintStream getPrintStream() { return this.out; }

	public void establishControlPipes() {
		// establish control pipes
		if (this.emulatorMode == EmulatorMode.MASTER) {
			RemoteAppInstanceTable workerTable = (RemoteAppInstanceTable)this.appInstanceTable;
			if (!workerTable.isPipeEstablished()) {
				workerTable.establishControlPipes(
						this.getRemoteDirectory(),
						this.getRemoteJavaPath(),
						this.getRemoteJVMOption(),
						System.out);
				try {
					Thread.sleep(Main.DIST_EMU_REMOTE_INVOCATION_WAIT);
				}
				catch (InterruptedException e) { /* ignore */ }
			}
		}
	}

	//
	// Master-Worker related stuff
	//

	private EmulatorMode emulatorMode;

	public EmulatorMode getEmulatorMode() { return this.emulatorMode; }

	private int nextID;
	public synchronized int getNextHostID() { return this.nextID++; }
	public int getMaxHostID() { return this.nextID - 1; }

	//
	// for Timer
	//
	private Timer timer;
	private long controlStartTime = 0L;
	private long lastScheduledTime = 0L;

	public void haltSchedulingTimer() {
		this.timer.stop();
	}

	public void scheduleTask(long time, long interval, int times, EmulatorTask task, boolean timeIsDifferential, boolean isDaemon) {
		// calculate control start time
		synchronized (this) {
			if (this.controlStartTime <= 0L) {
				this.controlStartTime = Timer.currentTimeMillis() + Main.DEFAULT_WAIT_MILLIS;
			}
		}

		// calculate absolute time
		long absoluteTime;
		if (timeIsDifferential)
			absoluteTime = this.lastScheduledTime + time;
		else
			absoluteTime = this.controlStartTime + this.currentTimeOffset + time;

		// schedule the task
		if (times <= 0 && interval >= 0L) {	// infinity times
			this.timer.scheduleAtFixedRate(task, absoluteTime, interval,
					isDaemon, task.executedConcurrently());

			//time = Long.MAX_VALUE;	// wait...() finishes though this task is running
		}
		else {
			if (times > 0) {
				absoluteTime -= interval;

				for (int i = 0; i < times; i++) {
					absoluteTime += interval;

					this.timer.schedule(task, absoluteTime,
							isDaemon, task.executedConcurrently());
					task = task.cloneTask();
				}
			}
		}

		// record last scheduled time
		if (absoluteTime > this.lastScheduledTime) {
			this.lastScheduledTime = absoluteTime;
		}
	}

	//
	// Control channels
	//

	private AppInstanceTable appInstanceTable;

	public Writer getControlPipe(int hostID) {
		return this.appInstanceTable.getControlPipe(hostID);
	}

	public void setControlPipe(int hostID, Writer out, EmulatorControllable appInstance) {
		this.appInstanceTable.setAppInstance(hostID, out, appInstance);
	}

	public Collection<Writer> getAllControlPipes() {
		return this.appInstanceTable.getAllControlPipes();
	}

	//
	// Context while parsing
	//

	private boolean verboseInParsing = false;
	private Class currentClass;
	private Method currentMainMethod;
	private String[] currentArguments = new String[0];
	private String currentDirectory = null;
	private String currentJavaPath = null;
	private String currentJVMOption = null;
	private int currentRelativePriority = 0;
	private long currentTimeOffset = 0L;

	public boolean getVerboseInParsing() { return this.verboseInParsing; }
	public boolean setVerboseInParsing(boolean v) {
		boolean old = this.verboseInParsing;
		this.verboseInParsing = v;
		return old;
	}

	public Class getCurrentClass() { return this.currentClass; }
	public Class setCurrentClass(Class clazz) {
		Class old = this.currentClass;
		this.currentClass = clazz;
		return old;
	}

	public Method getCurrentMainMethod() { return this.currentMainMethod; }
	public Method setCurrentMainMethod(Method method) {
		Method old = this.currentMainMethod;
		this.currentMainMethod = method;
		return old;
	}

	public String[] getCurrentArguments() { return this.currentArguments; }
	public String[] setCurrentArguments(String[] args) {
		String[] old = this.currentArguments;
		this.currentArguments = args;
		return old;
	}

	public String getRemoteDirectory() { return this.currentDirectory; }
	public String setRemoteDirectory(String dir) {
		String old = this.currentDirectory;
		this.currentDirectory = dir;
		return old;
	}

	public String getRemoteJavaPath() { return this.currentJavaPath; }
	public String setRemoteJavaPath(String path) {
		String old = this.currentJavaPath;
		this.currentJavaPath = path;
		return old;
	}

	public String getRemoteJVMOption() { return this.currentJVMOption; }
	public String setRemoteJVMOption(String classpath) {
		String old = this.currentJVMOption;
		this.currentJVMOption = classpath;
		return old;
	}

	public int getCurrentRelativePriority() { return this.currentRelativePriority; }
	public int setCurrentRelativePriority(int prio) {
		int old = this.currentRelativePriority;
		this.currentRelativePriority = prio;
		return old;
	}

	public long getCurrentTimeOffset() { return this.currentTimeOffset; }
	public long setCurrentTimeOffset(long offset) {
		long old = this.currentTimeOffset;
		this.currentTimeOffset = offset;
		return old;
	}
	public long advanceCurrentTimeOffset(long offset) {
		long old = this.currentTimeOffset;
		this.currentTimeOffset = this.lastScheduledTime - this.controlStartTime + offset;
		return old;
	}
}

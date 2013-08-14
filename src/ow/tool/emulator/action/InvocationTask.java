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

package ow.tool.emulator.action;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorControllable;
import ow.tool.emulator.EmulatorMode;
import ow.tool.emulator.EmulatorTask;
import ow.util.concurrent.ExecutorBlockingMode;
import ow.util.concurrent.SingletonThreadPoolExecutors;

public class InvocationTask extends EmulatorTask {
	private int hostID;
	private final Class clazz;
	private final Method mainMethod;
	private final String[] arguments;
	private int relativePriority;

	public InvocationTask(EmulatorContext cxt,
			int hostID,
			Class clazz, Method mainMethod, String[] args,
			int relativePriority) {
		super(cxt);

		this.hostID = hostID;
		this.clazz = clazz;
		this.mainMethod = mainMethod;
		this.arguments = args;
		this.relativePriority = relativePriority;
	}

	public InvocationTask(InvocationTask task) {	// for clone()
		super(task);

		this.hostID = task.hostID;
		this.clazz = task.clazz;
		this.mainMethod = task.mainMethod;
		this.arguments = task.arguments;
		this.relativePriority = task.relativePriority;
	}

	public String toString() {
		return "invoke " + this.clazz.getName() + " " + this.arguments + " on " + this.hostID;
	}

	public EmulatorTask cloneTask() {
		return new InvocationTask(this);
	}

	public boolean doesExit() { return false; }

	public boolean executedConcurrently() { return false; }

	public void run() {
		// determine host ID
		if (this.hostID < 0) {
			this.hostID = this.cxt.getNextHostID();
		}

		if (this.cxt.getEmulatorMode() == EmulatorMode.MASTER) {
			Writer out = this.cxt.getControlPipe(hostID);

			if (out == null) {
				System.err.println("There is no output pipe associated with host id:" + hostID);
			}

			try {
				out.write("class " + this.clazz.getName() + "\n");

				out.write("arguments");
				for (String arg: this.arguments) {
					out.write(" " + arg);
				}
				out.write("\n");

				out.write("invoke " + hostID + "\n");
				out.flush();
			}
			catch (IOException e) {
				System.err.println("An IOException thrown during writing to control pipes.");
				e.printStackTrace();
			}
		}
		else {	// not MASTER
			boolean isClazzEmulatorControllable;
			try {
				this.clazz.asSubclass(EmulatorControllable.class);
				isClazzEmulatorControllable = true;
			}
			catch (ClassCastException e) {
				isClazzEmulatorControllable = false;
			}

			Runnable invoker = null;
			if (isClazzEmulatorControllable) {
				invoker = new EmuControllableInvoker(
						this.cxt,
						this.hostID,
						this.clazz,
						this.arguments,
						this.relativePriority);

				invoker.run();
				// serialize all nodes' run() executions by directly calling run()
				// as long as ow.util.Timer executes tasks directly.
			}
			else {
				invoker = new GeneralInvoker(
						this.clazz,
						this.mainMethod,
						this.arguments,
						this.relativePriority);

				ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
						ExecutorBlockingMode.UNLIMITED, false);
				ex.submit(invoker);
			}
		}
	}

	private static class GeneralInvoker implements Runnable {
		private Class clazz;
		private Method mainMethod;
		private String[] args;
		private int relativePriority;

		GeneralInvoker(Class clazz, Method mainMethod, String[] args, int relativePrio) {
			this.clazz = clazz;
			this.mainMethod = mainMethod;
			this.args = args;
			this.relativePriority = relativePrio;
		}

		public void run() {
			Thread th = Thread.currentThread();
			String origName = th.getName();
			int origPrio = th.getPriority();
			try {
				th.setName("Emulator: " + this.clazz.getName());
			}
			catch (SecurityException e) {
				System.err.println("Could not set thread name.");
			}
			try {
				th.setPriority(origPrio + this.relativePriority);
			}
			catch (Exception e) {
				System.err.println("Could not set thread priority: " + origPrio + this.relativePriority);
			}

			try {
				this.mainMethod.invoke(null, (Object)this.args);
			}
			catch (IllegalAccessException e) {
				System.err.println("Could not invoke: " + this.clazz.getName());
			}
			catch (InvocationTargetException e) {
				Throwable t = e.getCause();

				System.err.println("Exception thrown:");
				t.printStackTrace();
			}
			catch (Throwable e) {
				System.err.println("An application instance threw a Throwable:");
				e.printStackTrace(System.err);

				if (e instanceof VirtualMachineError) {
					System.err.println("A VirtualMachineError thrown and exit.");
					System.exit(1);
				}
			}

			try {
				th.setName(origName);
			}
			catch (Exception e) { /* ignore */ }
			try {
				th.setPriority(origPrio);
			}
			catch (Exception e) { /* ignore */ }
		}
	}

	private static class EmuControllableInvoker implements Runnable {
		private final EmulatorContext cxt;
		private final int hostID; 
		private final Class clazz;
		private final String[] args;
		private int relativePriority;

		EmuControllableInvoker(EmulatorContext cxt, int hostID, Class clazz, String[] args, int relativePrio) {
			this.cxt = cxt;
			this.hostID = hostID;
			this.clazz = clazz;
			this.args = args;
			this.relativePriority = relativePrio;
		}

		public void run() {
			Thread th = Thread.currentThread();
			String origName = th.getName();
			int origPrio = th.getPriority();
			try {
				th.setName("Emulator: " + this.clazz.getName());
			}
			catch (SecurityException e) {
				System.err.println("Could not set thread name.");
			}
			try {
				th.setPriority(origPrio + this.relativePriority);
			}
			catch (Exception e) {
				System.err.println("Could not set thread priority: " + origPrio + this.relativePriority);
			}

			// create an application instance
			EmulatorControllable appInstance = null;

			try {
				appInstance = (EmulatorControllable)this.clazz.newInstance();
			}
			catch (Exception e) {
				System.err.println("Exception thrown:");
				e.printStackTrace();

				return;
			}

			// start an application instance
			Writer controlPipe = null;
			try {
				appInstance.invoke(args, this.cxt.getPrintStream());
				controlPipe = appInstance.getControlPipe();
			}
			catch (Throwable e) {
				System.err.println("An application instance threw a Throwable:");
				e.printStackTrace(System.err);

				if (e instanceof VirtualMachineError) {
					System.err.println("A VirtualMachineError thrown and exit.");
					System.exit(1);
				}
			}

			if (controlPipe != null) {
				this.cxt.setControlPipe(this.hostID, controlPipe, appInstance);
			}

			try {
				th.setName(origName);
			}
			catch (Exception e) { /* ignore */ }
			try {
				th.setPriority(origPrio);
			}
			catch (Exception e) { /* ignore */ }
		}
	}
}

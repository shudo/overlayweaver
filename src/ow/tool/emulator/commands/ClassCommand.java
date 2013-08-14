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
import java.lang.reflect.Method;

import ow.tool.emulator.EmulatorContext;
import ow.tool.emulator.EmulatorMode;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class ClassCommand implements Command<EmulatorContext> {
	private final static String[] NAMES = {"class"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "class <class name>";
	}

	public boolean execute(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1) {
			out.println("class command needs an argument.");
			if (context.isInteractive())
				return false;
			else
				return true;
		}

		Class clazz;
		try {
			clazz = Class.forName(args[0]);
		}
		catch (ClassNotFoundException e) {
			out.println("No such class: " + args[0]);
			if (context.isInteractive())
				return false;
			else
				return true;
		}

		Method mainMethod = null;
		try {
			mainMethod = clazz.getMethod("main", Class.forName("[Ljava.lang.String;"));
		}
		catch (Exception e) {
			// clazz is still null
		}

		cxt.setCurrentClass(clazz);
		cxt.setCurrentMainMethod(mainMethod);

		if (cxt.getEmulatorMode() != EmulatorMode.WORKER
				&& cxt.getVerboseInParsing()) {
			out.print("class: ");
			out.println(args[0]);
		}

		return false;
	}
}

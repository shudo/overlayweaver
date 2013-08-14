/*
 * Copyright 2006-2007 Kazuyuki Shudo, and contributors.
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

import ow.tool.emulator.EmulatorContext;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class RemoteCommand implements Command<EmulatorContext> {
	private final static String[] NAMES = {"remote"};

	public String[] getNames() {
		return NAMES;
	}

	public String getHelp() {
		return "remote {connect,directory [<current dir>],javapath [<java command path>],jvmoption [<java options>]}";
	}

	public boolean execute(ShellContext<EmulatorContext> context) {
		EmulatorContext cxt = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1) {
			out.println("remote command requires an argument.");
			if (context.isInteractive())
				return false;
			else
				return true;
		}

		StringBuilder sb = new StringBuilder();
		if (args.length >= 2) {
			sb.append(args[1]);
			for (int i = 2; i < args.length; i++) {
				sb.append(" ");
				sb.append(args[i]);
			}
		}
		String option = sb.toString();

		String command = args[0];
		if (command.startsWith("c")) {
			out.println("establish connections.");
			cxt.establishControlPipes();
		}
		else if (command.startsWith("d")) {
			out.println("remote directory: " + option);
			cxt.setRemoteDirectory(option);
		}
		else if (command.startsWith("ja")) {
			out.println("remote javapath: " + option);
			cxt.setRemoteJavaPath(option);
		}
		else if (command.startsWith("jv")) {
			out.println("remote jvmoption: " + option);
			cxt.setRemoteJVMOption(option);
		}
		else {
			out.println("No such command: remote " + command);
			if (context.isInteractive())
				return false;
			else
				return true;
		}

		return false;
	}
}

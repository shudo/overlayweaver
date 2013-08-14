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

package ow.tool.msgcounter.commands;

import java.io.PrintStream;

import ow.tool.msgcounter.MessageCounter;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class StatusCommand implements Command<MessageCounter> {
	private final static String[] NAMES = {"status"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "status [-v] [-verbose]";
	}

	public boolean execute(ShellContext<MessageCounter> context) {
		MessageCounter msgCounter = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		boolean verbose = false;
		if (args.length > 0) {
			if (args[0].startsWith("-v")) verbose = true;
		}

		String countString = msgCounter.getCountsString(verbose);
		countString = countString.replaceAll("\n", Shell.CRLF);

		out.print(countString);
		out.flush();

		return false;
	}
}

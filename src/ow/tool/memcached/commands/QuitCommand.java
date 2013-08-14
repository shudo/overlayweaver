/*
 * Copyright 2008 Kazuyuki Shudo, and contributors.
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

package ow.tool.memcached.commands;

import java.io.PrintStream;

import ow.dht.memcached.Memcached;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class QuitCommand implements Command<Memcached> {
	private final static String[] NAMES = {"quit"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "quit";
	}

	public boolean execute(ShellContext<Memcached> context) {
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length > 0) {
			out.print("ERROR" + Shell.CRLF);
			out.flush();

			return false;
		}

		return true;
	}
}

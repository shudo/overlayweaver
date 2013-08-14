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

public final class FlushAllCommand implements Command<Memcached> {
	private final static String[] NAMES = {"flush_all"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "flush_all";
	}

	public boolean execute(ShellContext<Memcached> context) {
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length > 2) {
			out.print("ERROR" + Shell.CRLF);
			out.flush();

			return false;
		}

		boolean noreply = false;
		if (args.length > 1) {
			if (args[1].toLowerCase().equals("noreply")) noreply = true;
		}

		// TODO implement

		if (!noreply) {
			// always print "ERROR" because the flush_all process is not impelemnted
			out.print("ERROR" + Shell.CRLF);
			out.flush();
		}

		return false;
	}
}

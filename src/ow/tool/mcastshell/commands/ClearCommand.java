/*
 * Copyright 2007 Kazuyuki Shudo, and contributors.
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

package ow.tool.mcastshell.commands;

import java.io.PrintStream;
import java.net.UnknownHostException;

import ow.mcast.Mcast;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class ClearCommand implements Command<Mcast> {
	private final static String[] NAMES = {"clear"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "clear {routingtable,mcast}";
	}

	private void usage(PrintStream out) {
		out.print("usage: " + getHelp() + Shell.CRLF);
		out.flush();
	}

	public boolean execute(ShellContext<Mcast> context)
			throws UnknownHostException {
		Mcast mcast = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1) {
			usage(out);
			return false;
		}

		char firstChar = args[0].charAt(0);
		switch (firstChar) {
		case 'r':
			mcast.clearRoutingTable();
			out.print("routing table cleared." + Shell.CRLF);
			break;
		case 'm':
			mcast.clearMcastState();
			out.print("multicast states (topology) cleared." + Shell.CRLF);
			break;
		default:
			usage(out);
		}

		return false;
	}
}

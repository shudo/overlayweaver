/*
 * Copyright 2008-2009 Kazuyuki Shudo, and contributors.
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

package ow.tool.dhtshell.commands;

import java.io.PrintStream;

import ow.dht.DHT;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public class SetTTLCommand implements Command<DHT<String>> {
	private final static String[] NAMES = {"setttl"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "setttl <ttl>";
	}

	public boolean execute(ShellContext<DHT<String>> context) {
		DHT<String> dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1) {
			out.print("usage: " + getHelp() + Shell.CRLF);
			out.flush();

			return false;
		}

		int ttl;
		try {
			ttl = Integer.parseInt(args[0]) * 1000;
		}
		catch (NumberFormatException e) {
			out.print("TTL should be an integer: " + args[0] + Shell.CRLF);
			return false;
		}

		dht.setTTLForPut(ttl);

		return false;
	}
}

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
import java.util.Set;

import ow.dht.ValueInfo;
import ow.dht.memcached.Item;
import ow.dht.memcached.Memcached;
import ow.id.ID;
import ow.routing.RoutingException;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class DeleteCommand implements Command<Memcached> {
	private final static String[] NAMES = {"delete"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "delete <key> [<time>] [noreply]";
	}

	public boolean execute(ShellContext<Memcached> context) {
		Memcached dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1 || args.length > 3) {
			out.print("ERROR" + Shell.CRLF);
			out.flush();

			return false;
		}

		// parse arguments
		ID key = ID.parseID(args[0], dht.getRoutingAlgorithmConfiguration().getIDSizeInByte());
//		long time = 0L;
		boolean noreply = false;
//		if (args.length >= 2) {
//			try {
//				time = Long.parseLong(args[1]);
//			}
//			catch (NumberFormatException e) {
//				out.print("CLIENT_ERROR bad command line format" + Shell.CRLF);
//				out.flush();

//				return false;
//			}

			if (args.length >= 3) {
				if (args[2].toLowerCase().equals("noreply")) noreply = true;
			}
//		}

		// delete
		// TODO consider <time> argument
		Set<ValueInfo<Item>> existedValues = null;

		try {
			existedValues = dht.remove(key);
		}
		catch (RoutingException e) {
			out.print("SERVER_ERROR put failed" + Shell.CRLF);
			out.flush();

			return false;
		}

		if (!noreply) {
			if (existedValues != null)
				out.print("DELETED" + Shell.CRLF);
			else
				out.print("NOT_FOUND" + Shell.CRLF);
			out.flush();
		}

		return false;
	}
}

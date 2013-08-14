/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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

package ow.tool.mcastshell.commands;

import java.io.PrintStream;

import ow.id.ID;
import ow.mcast.Mcast;
import ow.routing.RoutingException;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class MulticastCommand implements Command<Mcast> {
	private final static String[] NAMES = {"multicast"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "multicast <group name> <message>";
	}

	public boolean execute(ShellContext<Mcast> context) {
		Mcast mcast = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 2) {
			out.print("usage: " + getHelp() + Shell.CRLF);
			out.flush();

			return false;
		}

		String msg;
		if (args.length <= 2)
			msg = args[1];
		else {
			StringBuilder sb = new StringBuilder();
			sb.append(args[1]);
			for (int i = 2; i < args.length; i++) {
				sb.append(" ").append(args[i]);
			}

			msg = sb.toString();
		}

		ID groupID = ID.parseID(args[0], mcast.getRoutingAlgorithmConfiguration().getIDSizeInByte());

		try {
			mcast.multicast(groupID, msg);

			out.print("sent." + Shell.CRLF);
		}
		catch (RoutingException e) {
			out.print("routing failed: " + e + Shell.CRLF);
		}

		out.flush();

		return false;
	}
}

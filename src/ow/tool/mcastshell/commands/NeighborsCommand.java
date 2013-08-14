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

package ow.tool.mcastshell.commands;

import java.io.PrintStream;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.mcast.Mcast;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class NeighborsCommand implements Command<Mcast> {
	private final static String[] NAMES = {"neighbors"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "neighbors";
	}

	public boolean execute(ShellContext<Mcast> context) {
		Mcast mcast = context.getOpaqueData();
		PrintStream out = context.getOutputStream();

		StringBuilder sb = new StringBuilder();

		ID[] groups = mcast.getGroupsWithSpanningTree();
		if (groups != null) {
			for (ID groupID: groups) {
				IDAddressPair parent = mcast.getParent(groupID);
				IDAddressPair[] children = mcast.getChildren(groupID);

				sb.append("group ID: ");
				sb.append(groupID);

				sb.append(Shell.CRLF + " parent:");
				if (parent != null) {
					sb.append(" ");
					sb.append(parent.getAddress());
				}

				sb.append(Shell.CRLF + " children:");
				if (children != null) {
					for (IDAddressPair child: children) {
						sb.append(Shell.CRLF + "  ");
						sb.append(child.getAddress());
					}
				}

				sb.append(Shell.CRLF);
			}
		}

		out.print(sb);
		out.flush();

		return false;
	}
}

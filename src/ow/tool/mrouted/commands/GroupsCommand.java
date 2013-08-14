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

package ow.tool.mrouted.commands;

import java.io.PrintStream;

import ow.id.ID;
import ow.ipmulticast.Group;
import ow.ipmulticast.Host;
import ow.tool.mrouted.ApplicationLevelMulticastRouter;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class GroupsCommand implements Command<ApplicationLevelMulticastRouter> {
	private final static String[] NAMES = {"groups"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "groups";
	}

	public boolean execute(ShellContext<ApplicationLevelMulticastRouter> context) {
		ApplicationLevelMulticastRouter almRouter = context.getOpaqueData();
		PrintStream out = context.getOutputStream();

		Group[] groups = almRouter.getJoinedMulticastGroups();

		StringBuilder sb = new StringBuilder();
		sb.append("groups:");
		if (groups != null) {
			for (Group g: groups) {
				sb.append("\n ");
				sb.append(g.getGroupAddress());
				sb.append("\t");
				sb.append(ID.getHashcodeBasedID(g.getGroupAddress(),
						almRouter.getRoutingAlgorithmConfiguration().getIDSizeInByte()));

				if (g.numOfHosts() > 0) {
					sb.append("\n  ");
				}
				for (Host h: g.getAllHost()) {
					sb.append(" ");
					sb.append(h.getAddress());
				}
			}
		}
		sb.append("\n");

		out.print(sb);
		out.flush();

		return false;
	}
}

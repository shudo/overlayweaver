/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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

package ow.tool.dhtshell.commands;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.CommandUtil;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class GetCommand implements Command<DHT<String>> {
	private final static String[] NAMES = {"get"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "get [-status] <key> [<key> ...]";
	}

	public boolean execute(ShellContext<DHT<String>> context) {
		DHT<String> dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();
		boolean showStatus = false;
		int argIndex = 0;

		if (argIndex < args.length && args[argIndex].startsWith("-")) {
			showStatus = true;
			argIndex++;
		}

		if (argIndex >= args.length) {
			out.print("usage: " + getHelp() + Shell.CRLF);
			out.flush();

			return false;
		}

		Queue<ID> requestQueue = new ConcurrentLinkedQueue<ID>();

		// parse the command line and queue get requests
		List<String> keyList = new ArrayList<String>();

		for (; argIndex < args.length; argIndex++) {
			ID key = ID.parseID(args[argIndex], dht.getRoutingAlgorithmConfiguration().getIDSizeInByte());
			keyList.add(args[argIndex]);

			requestQueue.offer(key);
		}

		// process get requests
		ID[] keys = new ID[requestQueue.size()];
		for (int i = 0; i < keys.length; i++)
			keys[i] = requestQueue.poll();

		Set<ValueInfo<String>>[] values;
		values = dht.get(keys);

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < keys.length; i++) {
			ID key = keys[i];

			sb.append("key:   ").append(key).append(Shell.CRLF);

			if (values[i] != null) {
				if (!values[i].isEmpty()) {
					for (ValueInfo<String> v: values[i]) {
						sb.append("value: ").append(v.getValue()).append(" ").append(v.getTTL() / 1000);

						ByteArray secret = v.getHashedSecret();
						if (secret != null) {
							sb.append(" ").append(secret);
						}

						sb.append(Shell.CRLF);
					}
				}
				else {
					sb.append("value:").append(Shell.CRLF);
				}
			}
			else {
				sb.append("routing failed: ").append(keyList.get(i)).append(Shell.CRLF);
			}
		}

		if (showStatus) {
			sb.append(CommandUtil.buildStatusMessage(context.getOpaqueData(), -1));
		}

		out.print(sb);
		out.flush();

		return false;
	}
}

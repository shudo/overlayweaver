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

package ow.tool.dhtshell.commands;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.CommandUtil;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class PutCommand implements Command<DHT<String>> {
	private final static String[] NAMES = {"put"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "put [-status] <key> <value> [<value> ...] [- <key> <value> [<value> ...] ...]";
	}

	public boolean execute(ShellContext<DHT<String>> context) {
		DHT<String> dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();
		boolean showStatus = false;

		Map<ID,Set<String>> valueMap = new HashMap<ID,Set<String>>();

		// parse the command line and queue put requests
		int argIndex = 0;

		if (argIndex < args.length && args[argIndex].startsWith("-")) {
			showStatus = true;
			argIndex++;
		}

		List<String> keyList = new ArrayList<String>();

		do {
			int nargs = args.length - argIndex;
			if (nargs >= 3) {
				for (int j = 2; j < nargs; j++) {
					if ("-".equals(args[argIndex + j])) {
						nargs = j;
						break;
					}
				}
			}

			if (nargs < 2) {
				out.print("usage: " + getHelp() + Shell.CRLF);
				out.flush();

				return false;
			}

			ID key = ID.parseID(args[argIndex], dht.getRoutingAlgorithmConfiguration().getIDSizeInByte());
			keyList.add(args[argIndex]);

			Set<String> s = valueMap.get(key);
			if (s == null) {
				s = new HashSet<String>();
				valueMap.put(key, s);
			}
			for (int j = 1; j < nargs; j++) {
				s.add(args[argIndex + j]);
			}

			// update i
			argIndex += nargs;
			while (argIndex < args.length && "-".equals(args[argIndex])) argIndex++;
		} while (argIndex < args.length);

		// summarize
		DHT.PutRequest<String>[] requests =
			new DHT.PutRequest/*<String>*/[valueMap.keySet().size()];

		argIndex = 0;
		for (ID k: valueMap.keySet()) {
			Set<String> valueSet = valueMap.get(k);
			String[] values = new String[valueSet.size()];

			int j = 0;
			for (String v: valueSet) {
				values[j++] = v;
			}

			requests[argIndex++] = new DHT.PutRequest<String>(k, values);
		}

		// process put requests
		Set<ValueInfo<String>>[] values = null;
		try {
			values = dht.put(requests);

			for (argIndex = 0; argIndex < requests.length; argIndex++) {
				if (values[argIndex] == null) {
					out.print("routing failed: " + keyList.get(argIndex) + Shell.CRLF);
				}
			}

			if (showStatus) {
				out.print(CommandUtil.buildStatusMessage(context.getOpaqueData(), -1));
				out.flush();
			}
		}
		catch (Exception e) {
			out.print("An exception thrown:" + Shell.CRLF);
			e.printStackTrace(out);
			out.flush();
		}

		return false;
	}
}

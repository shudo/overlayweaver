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
import java.io.UnsupportedEncodingException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.tool.dhtshell.Main;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.CommandUtil;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class RemoveCommand implements Command<DHT<String>> {
	private final static String[] NAMES = {"remove", "delete"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "remove|delete [-status] <secret> <key> [<value> ...] [- <key> [<value> ...] ...]";
	}

	public boolean execute(ShellContext<DHT<String>> context) {
		DHT<String> dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();
		boolean showStatus = false;

		Queue<DHT.RemoveRequest<String>> requestQueue =
			new ConcurrentLinkedQueue<DHT.RemoveRequest<String>>();

		// parse the command line and queue remove requests
		int argIndex = 0;

		if (argIndex < args.length && args[argIndex].startsWith("-")) {
			showStatus = true;
			argIndex++;
		}

		if (args.length < argIndex + 2) {
			out.print("usage: " + getHelp() + Shell.CRLF);
			out.flush();

			return false;
		}

		ByteArray secret = null;
		try {
			secret = ByteArray.valueOf(args[argIndex], Main.ENCODING);
		}
		catch (UnsupportedEncodingException e) {
			// NOTREACHED
		}
		argIndex++;
		secret = secret.hashWithSHA1();

		do {
			int nargs = args.length - argIndex;
			for (int j = 1; j < nargs; j++) {
				if ("-".equals(args[argIndex + j])) nargs = j;
			}

			ID key = ID.parseID(args[argIndex], dht.getRoutingAlgorithmConfiguration().getIDSizeInByte());

			int nValue = nargs - 1;
			if (nValue > 0) {
				String[] values = new String[nValue];
				for (int j = 0; j < nValue; j++)
					values[j] = args[argIndex + 1 + j];

				requestQueue.offer(new DHT.RemoveRequest<String>(key, values));
			}
			else {
				requestQueue.offer(new DHT.RemoveRequest<String>(key));
			}

			// update i
			argIndex += nargs;
			while (argIndex < args.length && "-".equals(args[argIndex])) argIndex++;
		} while (argIndex < args.length);

		// process remove requests
		DHT.RemoveRequest<String>[] requests = new DHT.RemoveRequest/*<String>*/[requestQueue.size()];
		for (argIndex = 0; argIndex < requests.length; argIndex++)
			requests[argIndex] = requestQueue.poll();

		Set<ValueInfo<String>>[] values = dht.remove(requests, secret);

		StringBuilder sb = new StringBuilder();

		for (argIndex = 0; argIndex < requests.length; argIndex++) {
			sb.append("key:   ").append(requests[argIndex].getKey()).append(Shell.CRLF);

			if (values[argIndex] != null) {
				for (ValueInfo<String> v: values[argIndex]) {
					sb.append("value: ").append(v.getValue()).append(" ").append(v.getTTL() / 1000);

					secret = v.getHashedSecret();
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

		if (showStatus) {
			sb.append(CommandUtil.buildStatusMessage(context.getOpaqueData(), -1));
		}

		out.print(sb);
		out.flush();

		return false;
	}
}

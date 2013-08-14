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

package ow.tool.dhtshell.commands;

import java.io.PrintStream;
import java.util.Set;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class LocaldataCommand implements Command<DHT<String>> {
	private final static String[] NAMES = { "localdata" };

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "localdata";
	}

	public boolean execute(ShellContext<DHT<String>> context) {
		DHT<String> dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		Set<ID> keySet;
		Set<ValueInfo<String>> values;

		StringBuilder sb = new StringBuilder();

		// local directory
		keySet = dht.getLocalKeys();
		if (keySet != null && !keySet.isEmpty()) {
			sb.append("Local directory (entries which this node has put):").append(Shell.CRLF);

			for (ID key: keySet) {
				values = dht.getLocalValues(key);

				sb.append("  key:   ").append(key).append(Shell.CRLF);
				if (values != null) {
					for (ValueInfo<String> v: values) {
						sb.append("  value: ").append(v.getValue());

						ByteArray secret = v.getHashedSecret();
						if (secret != null) {
							sb.append(" ").append(secret);
						}
						sb.append(Shell.CRLF);
					}
				}
				else {
					sb.append("  value: (NULL)").append(Shell.CRLF);
				}
			}
		}

		// global directory
		keySet = dht.getGlobalKeys();
		if (!keySet.isEmpty()) {
			sb.append("Global directory (local part of the DHT):").append(Shell.CRLF);

			for (ID key: keySet) {
				values = dht.getGlobalValues(key);

				sb.append("  key:   ").append(key).append(Shell.CRLF);
				if (values != null) {
					for (ValueInfo<String> v: values) {
						sb.append("  value: ").append(v.getValue()).append(" ").append(v.getTTL() / 1000);

						ByteArray secret = v.getHashedSecret();
						if (secret != null) {
							sb.append(" ").append(secret);
						}
						sb.append(Shell.CRLF);
					}
				}
				else {
					sb.append(" value: (NULL)").append(Shell.CRLF);
				}
			}
		}

		out.print(sb);
		out.flush();

		return false;
	}
}

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
import java.io.UnsupportedEncodingException;
import java.util.Set;

import ow.dht.ValueInfo;
import ow.dht.memcached.Item;
import ow.dht.memcached.Memcached;
import ow.id.ID;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

public final class LocaldataCommand implements Command<Memcached> {
	private final static String[] NAMES = {"localdata"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "localdata";
	}

	public boolean execute(ShellContext<Memcached> context) {
		Memcached dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		Set<ID> keySet;
		Set<ValueInfo<Item>> values;

		StringBuilder sb = new StringBuilder();

		// local directory
		keySet = dht.getLocalKeys();
		if (keySet != null && !keySet.isEmpty()) {
			sb.append("# Local directory (entries which this node has put):").append(Shell.CRLF);

			for (ID key: keySet) {
				values = dht.getLocalValues(key);

				for (ValueInfo<Item> v: values) {
					Item item = v.getValue();
					sb.append("VALUE ").append(key);
					sb.append(" ").append(item.getFlag());
					sb.append(" ").append(item.getData().length);
					sb.append(Shell.CRLF);

					try {
						sb.append(new String(item.getData(), "ASCII"));
					}
					catch (UnsupportedEncodingException e) { /* ignore */ }
					sb.append(Shell.CRLF);

					// TODO print secret
				}
			}
		}

		// global directory
		keySet = dht.getGlobalKeys();
		if (!keySet.isEmpty()) {
			sb.append("# Global directory (local part of the DHT):").append(Shell.CRLF);

			for (ID key: keySet) {
				values = dht.getGlobalValues(key);

				for (ValueInfo<Item> v: values) {
					Item item = v.getValue();
					sb.append("VALUE ").append(key);
					sb.append(" ").append(item.getFlag());
					sb.append(" ").append(item.getData().length);
					sb.append(Shell.CRLF);

					try {
						sb.append(new String(item.getData(), "ASCII"));
					}
					catch (UnsupportedEncodingException e) { /* ignore */ }
					sb.append(Shell.CRLF);

					// TODO print secret
				}
			}
		}

		out.print(sb);
		out.flush();

		return false;
	}
}

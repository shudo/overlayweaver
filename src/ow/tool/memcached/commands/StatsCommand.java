/*
 * Copyright 2008,2009 Kazuyuki Shudo, and contributors.
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
import ow.tool.memcached.Main;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;
import ow.util.Timer;

public final class StatsCommand implements Command<Memcached> {
	private final static String[] NAMES = {"stats"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "stats";
	}

	public boolean execute(ShellContext<Memcached> context) {
		Memcached dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		StringBuilder sb = new StringBuilder();

		if (args.length > 1) {
			sb.append("ERROR").append(Shell.CRLF);
		}
		else if (args.length <= 0) {
			sb.append("STAT time ").append(Timer.currentTimeMillis());
			sb.append("STAT version ").append(Main.VERSION).append(Shell.CRLF);
			sb.append("STAT curr_items ").append(dht.getGlobalKeys().size());
			// TODO implement more items
			sb.append("END").append(Shell.CRLF);
		}
		else {
			String arg = args[0].toLowerCase();
			if (arg.equals("items")) {
				// TODO implement
				sb.append("END").append(Shell.CRLF);
			}
			else if (arg.equals("sizes")) {
				int itemSize = 0, count = 0;

				Set<ID> keySet = dht.getGlobalKeys();
				if (!keySet.isEmpty()) {
					for (ID key: keySet) {
						Set<ValueInfo<Item>> values = dht.getGlobalValues(key);

						for (ValueInfo<Item> v: values) {
							Item item = v.getValue();
							// note: very rough estimation
							itemSize += 3;	// object header (not accurate)
							itemSize += (item.getData().length + 3) / 4;
							itemSize += 3;	// flag and cachedHashCode
							count++;
						}
					}

					itemSize = (itemSize + 1) / count;

					sb.append(itemSize * 4).append(" ").append(count).append(Shell.CRLF);
				}
				sb.append("END").append(Shell.CRLF);
			}
			else if (arg.equals("slabs")) {
				// TODO implement
				sb.append("END").append(Shell.CRLF);
			}
			else {
				sb.append("ERROR").append(Shell.CRLF);
			}
		}

		out.print(sb);
		out.flush();

		return false;
	}
}

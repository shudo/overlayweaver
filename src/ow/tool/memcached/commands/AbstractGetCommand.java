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

abstract class AbstractGetCommand implements Command<Memcached> {
	protected boolean get(ShellContext<Memcached> context, boolean returnCasUnique) {
		Memcached dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 1) {
			out.print("ERROR" + Shell.CRLF);
			out.flush();

			return false;
		}

		ID[] keys = new ID[args.length];
		for (int i = 0; i < args.length; i++) {
			keys[i] = ID.parseID(args[i], dht.getRoutingAlgorithmConfiguration().getIDSizeInByte());
		}

		Set<ValueInfo<Item>>[] values;
		values = dht.get(keys);

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			if (values[i] != null) {
				if (!values[i].isEmpty()) {
					for (ValueInfo<Item> v: values[i]) {
						Item item = v.getValue();
						sb.append("VALUE ").append(args[i]);
						sb.append(" ").append(item.getFlag());
						sb.append(" ").append(item.getData().length);
						if (returnCasUnique) {
							long casUnique = item.hashCode();
							if (casUnique < 0L) casUnique += (1L << 32);
							sb.append(" ").append(casUnique);	// print 32 bit unsigned integer
						}
						sb.append(Shell.CRLF);

						try {
							sb.append(new String(item.getData(), "ASCII"));
						}
						catch (UnsupportedEncodingException e) { /* ignore */ }
						sb.append(Shell.CRLF);
					}
				}
			}
			else { /* routing failed */ }
		}

		sb.append("END").append(Shell.CRLF);

		out.print(sb);
		out.flush();

		return false;
	}
}

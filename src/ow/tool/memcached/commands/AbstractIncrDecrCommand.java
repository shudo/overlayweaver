/*
 * Copyright 2008-2009 Kazuyuki Shudo, and contributors.
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
import java.math.BigInteger;
import java.util.Set;

import ow.dht.ValueInfo;
import ow.dht.memcached.Item;
import ow.dht.memcached.Memcached;
import ow.id.ID;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

abstract class AbstractIncrDecrCommand implements Command<Memcached> {
	protected boolean incrOrDecr(ShellContext<Memcached> context, boolean increment) {
		Memcached dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		if (args.length < 2 || args.length > 3) {
			out.print("ERROR" + Shell.CRLF);
			out.flush();

			return false;
		}

		ID key = ID.parseID(args[0], dht.getRoutingAlgorithmConfiguration().getIDSizeInByte());
		String value = args[1];
		boolean noreply = false;
		if (args.length >= 3) {
			if (args[2].toLowerCase().equals("noreply")) noreply = true;
		}

		// check value
		BigInteger valueBigInteger = new BigInteger(args[1]);
		if (valueBigInteger.compareTo(BigInteger.ZERO) < 0) {
			valueBigInteger = valueBigInteger.negate();
			value = valueBigInteger.toString();
			increment = !increment;
		}
		if (valueBigInteger.compareTo(Memcached.UINT64_MAX) > 0) {
			out.print("CLIENT_ERROR bad command line format" + Shell.CRLF);
			out.flush();

			return false;
		}

		// store
		long exptime = dht.getConfiguration().getDefaultTTL();
		dht.setTTLForPut(exptime);

		byte[] b = null;
		try {
			b = value.getBytes(Memcached.ENCODING);
		}
		catch (UnsupportedEncodingException e) { /* ignore */ }

		Item item = new Item(b, 0L);
		Memcached.Condition cond = (increment ? Memcached.Condition.INCREMENT : Memcached.Condition.DECREMENT);

		Set<ValueInfo<Item>> existedValue = null;
		try {
			existedValue = dht.put(key, item, cond);
		}
		catch (Exception e) {
			out.print("SERVER_ERROR put failed" + Shell.CRLF);
			out.flush();

			return false;
		}

		if (!noreply) {
			StringBuilder sb = new StringBuilder();

			if (existedValue != null) {
				for (ValueInfo<Item> v: existedValue) {
					String str = null;
					try {
						str = new String(v.getValue().getData(), Memcached.ENCODING);
					}
					catch (UnsupportedEncodingException e) { /* NOTREACHED */ }

					sb.append(str).append(Shell.CRLF);
				}
			}
			else
				sb.append("NOT_FOUND").append(Shell.CRLF);

			out.print(sb);
			out.flush();
		}

		return false;
	}
}

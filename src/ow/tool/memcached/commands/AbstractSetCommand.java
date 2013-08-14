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

import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

import ow.dht.ValueInfo;
import ow.dht.memcached.Item;
import ow.dht.memcached.Memcached;
import ow.id.ID;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.Shell;
import ow.tool.util.shellframework.ShellContext;

abstract class AbstractSetCommand implements Command<Memcached> {
	public boolean set(ShellContext<Memcached> context, Memcached.Condition cond) {
		Memcached dht = context.getOpaqueData();
		PrintStream out = context.getOutputStream();
		String[] args = context.getArguments();

		boolean isCas = cond.equals(Memcached.Condition.CAS);

		if ((isCas && (args.length < 5 || args.length > 6))
				|| (args.length < 4 || args.length > 5)) {
			out.print("ERROR" + Shell.CRLF);
			out.flush();

			return false;
		}

		// parse arguments
		ID key = ID.parseID(args[0], dht.getRoutingAlgorithmConfiguration().getIDSizeInByte());
		int flags; int bytes; long exptime;
		try {
			flags = (int)Long.parseLong(args[1]);
			exptime = Long.parseLong(args[2]);
			bytes = Integer.parseInt(args[3]);
		}
		catch (NumberFormatException e) {
			out.print("CLIENT_ERROR bad command line format" + Shell.CRLF);
			out.flush();

			return false;
		}

		// TODO Is key length and `bytes' to be checked whether it's too large?

		int argIndex = 4;

		int casUnique = 0;
		if (isCas) {
			casUnique = (int)Long.parseLong(args[argIndex++]);	// assume 32 bit unsigned integer
		}

		boolean noreply = false;
		if (args.length > argIndex) {
			if (args[argIndex].toLowerCase().equals("noreply")) noreply = true;
		}

		byte[] dataBlock;
		try {
			dataBlock = context.getShell().readLine(bytes);	// read <bytes> byte at least
		}
		catch (IOException e) { return true; }

		if (dataBlock == null || dataBlock.length != bytes) {
			out.print("CLIENT_ERROR bad data chunk" + Shell.CRLF);
			out.flush();

			return false;
		}

		// store
		exptime *= 1000;	// sec -> msec
		if (exptime <= 0) { exptime = dht.getConfiguration().getDefaultTTL(); }
		dht.setTTLForPut(exptime);

		Item item = new Item(dataBlock, flags);

		Set<ValueInfo<Item>> existedValue = null;
		try {
			if (isCas)
				existedValue = dht.put(key, item, casUnique);
			else
				existedValue = dht.put(key, item, cond);
		}
		catch (Exception e) {
			out.print("SERVER_ERROR put failed" + Shell.CRLF);
			out.flush();

			return false;
		}

		if (!noreply) {
			if (isCas) {
				if (existedValue != null) {
					if (existedValue.isEmpty())
						out.print("EXISTS" + Shell.CRLF);
					else
						out.print("STORED" + Shell.CRLF);
				}
				else
					out.print("NOT_FOUND" + Shell.CRLF);
			}
			else {
				if (existedValue != null)
					out.print("STORED" + Shell.CRLF);
				else
					out.print("NOT_STORED" + Shell.CRLF);
			}
			out.flush();
		}

		return false;
	}
}

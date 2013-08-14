/*
 * Copyright 2007-2008 Kazuyuki Shudo, and contributors.
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

package ow.messaging.util;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * An instance of this class checks if the source of an access is allowed to access.
 * A line is {allow,deny} [{<hostname>,<IP address>}[/<netmask>]]
 */
public class AccessController {
	private List<AccessControlEntry> aclist = new ArrayList<AccessControlEntry>();

	public AccessController(String filename) throws IOException {
		this.parse(filename);
	}
	public AccessController(Reader in) throws IOException {
		this.parse(in);
	}
	public AccessController(char[] acList) throws IOException {
		this(new CharArrayReader(acList));
	}
	public AccessController() {}

	public void parse(String filename) throws IOException {
		FileReader in = new FileReader(filename);
		this.parse(in);
	}

	public void parse(Reader rawIn) throws IOException {
		BufferedReader in = new BufferedReader(rawIn);

		String line;
		while ((line = in.readLine()) != null) {
			if (line.startsWith("#") || line.startsWith(";") || line.startsWith("//")) {
				// comment
				continue;
			}

			StringTokenizer st = new StringTokenizer(line, " ,\t\n\r");

			int nTokens = st.countTokens();
			if (nTokens <= 0) continue;
			String[] args = new String[nTokens];

			int i = 0;
			while (st.hasMoreTokens()) {
				args[i++] = st.nextToken();
			}

			// parse a line
			boolean allow;
			if ("allow".equalsIgnoreCase(args[0])) {
				allow = true;
			}
			else if ("deny".equalsIgnoreCase(args[0])) {
				allow = false;
			}
			else {
				throw new IOException("1st arg of a line has to be \"allow\" or \"deny\": " + args[0]);
			}

			InetAddress address = null;
			int netmask = -1;
			if (args.length >= 2) {
				String host = args[1];
				int slashIndex = host.indexOf('/');
				if (slashIndex >= 0) {
					netmask = Integer.parseInt(host.substring(slashIndex + 1));
					host = host.substring(0,slashIndex);
				}

				address = InetAddress.getByName(host);
			}

			AccessControlEntry entry =
					new AccessControlEntry(allow, address, netmask);
			this.aclist.add(entry);
		}
	}

	public boolean allow(InetAddress remoteAddress) {
		for (AccessControlEntry entry: this.aclist) {
			if (entry.match(remoteAddress)) {
				return entry.allow();
			}
		}

		return false;
	}

	private static class AccessControlEntry {
		private boolean allow;
		private InetAddress address;
		private byte[] addressBytes;
		private int netmask;

		AccessControlEntry(boolean allow, InetAddress address, int netmask) {
			this.allow = allow;
			this.address = address;
			this.netmask = netmask;

			if (address != null) {
				this.addressBytes = address.getAddress();

				// limits netmask to length of address
				int maskMaxLen = this.addressBytes.length << 3;
				if (this.netmask > maskMaxLen || this.netmask < 0) {
					this.netmask = maskMaxLen; 
				}
			}
		}

		AccessControlEntry(boolean allow, InetAddress address) {
			this(allow, address, -1);
			this.netmask = this.addressBytes.length << 3;
		}

		AccessControlEntry(boolean allow) {
			this(allow, null, -1);
		}

		public boolean match(InetAddress remoteAddress) {
			if (this.address == null) return true;

			int maskByteLen = this.netmask >> 3;
			int maskBitLen = this.netmask & 7;

			byte[] sourceBytes = remoteAddress.getAddress();
			int i = 0;
			for (; i < maskByteLen; i++) {
				if (this.addressBytes[i] != sourceBytes[i]) {
					return false;
				}
			}
			if (maskBitLen > 0) {
				if (((int)this.addressBytes[i] & 0xff) >>> (8-maskBitLen)
						!= ((int)sourceBytes[i] & 0xff) >>> (8-maskBitLen)) {
					return false;
				}
			}

			return true;
		}

		public boolean allow() { return this.allow; }
	}
}

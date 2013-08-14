/*
 * Copyright 2007 Kazuyuki Shudo, and contributors.
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

package ow.util;

import ow.messaging.MessagingAddress;
import ow.tool.dhtshell.Main;

public final class HTMLUtil {
	private final static String[][] conversionTable = {
		{"&", "&amp;"},
		{"<", "&lt;"},
		{">", "&gt;"},
		{"\"", "&quot;"},
		{"'", "&#39;"}
	};

	public static String stringInHTML(String str) {
		String ret = str;
		for (String[] convEntry: conversionTable) {
			ret = ret.replaceAll(convEntry[0], convEntry[1]);
		}

		return ret;
	}

	public static String convertMessagingAddressToURL(MessagingAddress addr) {
		return convertMessagingAddressToURL(addr, addr.getPort() + Main.XMLRPC_PORT_DIFF);
	}

	public static String convertMessagingAddressToURL(MessagingAddress addr, int port) {
		return "http://" + addr.getHostnameOrHostAddress() + ":" + port + "/";
	}
}

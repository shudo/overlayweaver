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

package ow.messaging.distemulator;

import ow.messaging.emulator.EmuMessagingConfiguration;
import ow.tool.emulator.RemoteAppInstanceTable;

public final class DEmuMessagingConfiguration extends EmuMessagingConfiguration {
	public final static String DEFAULT_REMOTE_MESSAGING_TRANSPORT = "UDP";
//	public final static String DEFAULT_REMOTE_MESSAGING_TRANSPORT = "TCP";
		// "UDP" or "TCP"
	public final static int DEFAULT_NET_PORT = 3997;
	public final static int DEFAULT_NET_PORT_RANGE = 1;

	private String remoteMessagingTransport = DEFAULT_REMOTE_MESSAGING_TRANSPORT;
	public String getRemoteMessagingTransport() { return this.remoteMessagingTransport; }
	public String setRemoteMessagingTransport(String type) {
		String old = this.remoteMessagingTransport;
		this.remoteMessagingTransport = type;
		return old;
	}

	private int netPort = DEFAULT_NET_PORT;
	public int getNetPort() { return this.netPort; }
	public int setNetPort(int port) {
		int old = this.netPort;
		this.netPort = port;
		return old;
	}

	private int netPortRange = DEFAULT_NET_PORT_RANGE;
	public int getNetPortRange() { return this.netPortRange; }
	public int setNetPortRange(int range) {
		int old = this.netPortRange;
		this.netPortRange = range;
		return old;
	}

	private RemoteAppInstanceTable hostTable = null;
	public RemoteAppInstanceTable getHostTable() { return this.hostTable; }
	public RemoteAppInstanceTable setHostTable(RemoteAppInstanceTable table) {
		RemoteAppInstanceTable old = this.hostTable;
		this.hostTable = table;
		return old;
	}
}

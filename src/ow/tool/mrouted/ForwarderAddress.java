/*
 * Copyright 2006,2010 National Institute of Advanced Industrial Science
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

package ow.tool.mrouted;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class ForwarderAddress {
	private Inet4Address address;
	private int port;

	public ForwarderAddress(String hostname, int port) throws UnknownHostException {
		InetAddress addr = Inet4Address.getByName(hostname);

		if (!(addr instanceof Inet4Address)) {
			throw new UnknownHostException("Hostname is not for IPv4: " + hostname);
		}

		this.address = (Inet4Address)addr;
		this.port = port;
	}

	public ForwarderAddress(Inet4Address address, int port) {
		this.address = address;
		this.port = port;
	}

	public Inet4Address getAddress() { return this.address; }
	public int getPort() { return this.port; }

	public boolean equals(Object obj) {
		if (!(obj instanceof ForwarderAddress)) return false;

		ForwarderAddress other = (ForwarderAddress)obj;
		if (!this.address.equals(other.address))
			return false;
		if (this.port != other.port)
			return false;

		return true;
	}

	public int hashCode() {
		return this.address.hashCode() ^ (this.port << 16);
	}
}

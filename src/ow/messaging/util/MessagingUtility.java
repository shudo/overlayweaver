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

package ow.messaging.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class MessagingUtility {
	/**
	 * Return an InetSocketAddress instance by the specified hostname and port number.
	 * They can be specified in the following format: [<hostname>][[:|/]<port>].
	 * If a port number is not specified in the 1st argument, this method throws an IllegalArgumentException.
	 */
	public static HostAndPort parseHostnameAndPort(String hostAndPort) {
		return parseHostnameAndPort(hostAndPort, -1);
	}

	/**
	 * Return an InetSocketAddress instance by the specified hostname and port number.
	 * They can be specified in the following format: [<hostname>][[:|/]<port>].
	 * If a port number is not specified in the 1st argument, the 2nd argument is used as the port number.
	 */
	public static HostAndPort parseHostnameAndPort(String hostAndPort, int defaultPort) {
		int index = hostAndPort.indexOf(':');
		if (index < 0)
			index = hostAndPort.indexOf('/');

		String host;
		int port;

		if (index >= 0) {	// ':' or '/' found
			try {
				port = Integer.parseInt(hostAndPort.substring(index + 1, hostAndPort.length()));
			}
			catch (NumberFormatException e) {
				port = defaultPort;
			}

			if (index > 0) {
				// hostname:port
				host = hostAndPort.substring(0, index);
			}
			else {			
				// :port
				host = null;
			}
		}
		else {
			try {
				// port
				port = Integer.parseInt(hostAndPort);	// throws NumberFormatException
				host = null;
			}
			catch (NumberFormatException e) {
				// hostname
				host = hostAndPort;
				port = defaultPort;
			}
		}

		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException(
					"port number is not specified or invalid: " + hostAndPort + ", " + port);
		}

		return new HostAndPort(host, port);
	}

	public static class HostAndPort {
		String hostname;
		int port;
		InetAddress cachedHostAddress = null;

		public HostAndPort(String hostname, int port) {
			this.hostname = hostname;
			this.port = port;
		}

		public String getHostName() { return this.hostname; }
		public int getPort() { return this.port; }

		public synchronized InetAddress getHostAddress() throws UnknownHostException {
			if (this.cachedHostAddress == null) {
				if (this.hostname == null) {
					this.cachedHostAddress = InetAddress.getLocalHost();
				}
				else {
					this.cachedHostAddress = InetAddress.getByName(this.hostname);
					// TODO: name resolution should be performed in a separated thread?
				}
			}

			return this.cachedHostAddress;
		}

		public InetSocketAddress getInetSocketAddress()
				throws UnknownHostException {
			return new InetSocketAddress(this.getHostAddress(), this.port);
		}

		public int hashCode() {
			return this.hostname.hashCode() ^ (this.port << 16);
		}

		public boolean equals(Object o) {
			if (o instanceof HostAndPort) {
				HostAndPort hp = (HostAndPort)o;
				if (this.hostname.equals(hp.hostname)
						&& this.port == hp.port)
					return true;
			}

			return false;
		}

		public String toString() {
			return hostname + ":" + port;
		}
	}
}

/*
 * Copyright 2006-2011 Kazuyuki Shudo, and contributors.
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

package ow.messaging;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import ow.messaging.util.MessagingUtility;
import ow.util.concurrent.ExecutorBlockingMode;
import ow.util.concurrent.SingletonThreadPoolExecutors;

/**
 * A MessagingAddress for UDP and TCP Messaging services.
 * Please do not instantiate this class directly.
 * Instead call {@link MessagingProvider MessagingProvider}.getMessagingAddress() to get an instance.
 */
public final class InetMessagingAddress implements MessagingAddress, java.io.Externalizable {
	private InetSocketAddress addr;
	private volatile String cachedHostname = null;

	public InetMessagingAddress(int port) {
		this.addr = new InetSocketAddress(port);
		this.init();
	}
	public InetMessagingAddress(InetAddress addr, int port) {
		this.addr = new InetSocketAddress(addr, port);
		this.init();
	}
	public InetMessagingAddress(InetSocketAddress addr) {
		assert(addr != null);

		this.addr = addr;
		this.init();
	}
	public InetMessagingAddress(String hostAndPort, int defaultPort) throws UnknownHostException {
		this.addr = MessagingUtility.parseHostnameAndPort(hostAndPort, defaultPort).getInetSocketAddress();
		this.init();
	}
	public InetMessagingAddress(String hostAndPort) throws UnknownHostException {
		this.addr = MessagingUtility.parseHostnameAndPort(hostAndPort).getInetSocketAddress();
		this.init();
	}

	private void init() {
		if (!MessagingConfiguration.DO_HOSTNAME_LOOKUP) return;

		Runnable r = new Runnable() {
			public void run() {
				Thread th = Thread.currentThread();
				String origName = th.getName();

				InetAddress inetAddr = addr.getAddress();

				th.setName("Resolving hostname for " + inetAddr.getHostAddress());

				try {
					String hostname = inetAddr.getHostName();	// can take much time up to several seconds

					if (hostname != null && hostname.length() > 0) {
						cachedHostname = hostname;
					}
				}
				finally {
					th.setName(origName);
				}
			}
		};

		SingletonThreadPoolExecutors.getThreadPool(
				ExecutorBlockingMode.CONCURRENT_NON_BLOCKING, true).submit(r);
	}

	public String getHostAddress() {
		return this.addr.getAddress().getHostAddress();
	}

	public String getHostname() {
		String hostname = this.cachedHostname;

		if (hostname != null)
			return hostname;
		else
			return "(not resolved)";
	}

	public String getHostnameOrHostAddress() {
		String hostname = this.cachedHostname;

		if (hostname != null)
			return hostname;
		else
			return this.getHostAddress();
	}

	public int getPort() {
		return this.addr.getPort();
	}

	public MessagingAddress getMessagingAddress() { return this; }

	public void copyFrom(MessagingAddress addr) {
		if (addr instanceof InetMessagingAddress) {
			InetMessagingAddress inetAddr = (InetMessagingAddress)addr;
			this.addr = inetAddr.addr;
			this.cachedHostname = inetAddr.cachedHostname;
		}
	}

	public void setInetAddress(InetAddress addr) {
		this.cachedHostname = null;

		int port = this.addr.getPort();
		this.addr = new InetSocketAddress(addr, port);

		this.init();
	}

	public InetSocketAddress getInetSocketAddress() {
		return this.addr;
	}

	public InetAddress getInetAddress() {
		return this.addr.getAddress();
	}

	public int hashCode() {
		return this.addr.hashCode();
	}

	public boolean equals(Object o) {
		if (o == null || !(o instanceof InetMessagingAddress)) return false;

		InetMessagingAddress other = (InetMessagingAddress)o;

		try {
			return this.addr.equals(other.addr);
		}
		catch (NullPointerException e) { return false; }
	}

	public String toString() {
		return this.toString(0);
	}

	public String toString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append(this.getHostname());
		sb.append("/");
		sb.append(this.getHostAddress());
		if (verboseLevel >= 0) {
			sb.append(":");
			sb.append(this.addr.getPort());
		}

		return sb.toString();
	}

	//
	// for object serialization
	//

	/**
	 * A public constructor with no argument required to implement Externalizable interface.
	 */
	public InetMessagingAddress() {}

	public void writeExternal(java.io.ObjectOutput out)
			throws java.io.IOException {
		byte[] address = this.addr.getAddress().getAddress();
		int port = this.addr.getPort();
		int len;

		len = address.length;	// 4 (IPv4) or 16 (IPv6)
		if (len < 255) {
			out.writeByte(len);
		}
		else {
			out.writeByte(0xff);
			out.writeInt(len);
		}

		out.write(address);
		out.writeChar(port);
	}

	public void readExternal(java.io.ObjectInput in)
			throws java.io.IOException, ClassNotFoundException {
		int len;
		byte[] address;
		int port;

		len = in.readByte() & 0xff;	// 4 (IPv4) or 16 (IPv6)
		if (len == 0xff) {
			len = in.readInt();
		}

		address = new byte[len];

		in.readFully(address);
		port = in.readChar();

		// initialize this instance
		InetAddress inetAddr = InetAddress.getByAddress(address);
		this.addr = new InetSocketAddress(inetAddr, port);

		this.init();
	}
}

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

package ow.messaging.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * {@link java.net.ServerSocket ServerSocket} class with an access control list (ACL). 
 */
public final class AccessControlledServerSocket extends ServerSocket {
	private AccessController ac = null;

	public AccessControlledServerSocket(AccessController ac) throws IOException {
		super();
		this.ac = ac;
	}
	public AccessControlledServerSocket(AccessController ac, int port) throws IOException {
		super(port);
		this.ac = ac;
	}
	public AccessControlledServerSocket(AccessController ac, int port, int backlog) throws IOException {
		super(port, backlog);
		this.ac = ac;
	}
	public AccessControlledServerSocket(AccessController ac, int port, int backlog, InetAddress bindAddr) throws IOException {
		super(port, backlog, bindAddr);
		this.ac = ac;
	}
	public AccessControlledServerSocket() throws IOException {
		super();
	}
	public AccessControlledServerSocket(int port) throws IOException {
		super(port);
	}
	public AccessControlledServerSocket(int port, int backlog) throws IOException {
		super(port, backlog);
	}
	public AccessControlledServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
		super(port, backlog, bindAddr);
	}

	/**
	 * Sets the specified access controller to this instance.
	 */
	public void setAccessController(AccessController ac) {
		synchronized (this) {
			this.ac = ac;
		}
	}

	/**
	 * Access-controlled accept().
	 */
	public Socket accept() throws IOException {
		Socket sock;

		while (true) {
			sock = super.accept();

			AccessController localAC;
			synchronized (this) {
				localAC = this.ac;
			}

			if (localAC != null) {
				if (!localAC.allow(sock.getInetAddress())) {
					sock.close();
				}
			}

			break;
		}

		return sock;
	}
}

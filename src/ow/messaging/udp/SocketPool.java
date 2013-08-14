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

package ow.messaging.udp;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pool of {@link java.nio.channels.DatagramChannel DatagramChannel} instances.
 * {@link ow.messaging.udp.UDPMessageSender UDPMessageSender} gets an instance
 * for each context to avoid cross-talk.
 */
final class SocketPool {
	private final static Logger logger = Logger.getLogger("messaging");

	private int capacity;
	private Stack<DatagramChannel> sockStack;

	SocketPool(int capacity) {
		this.capacity = capacity;
		this.sockStack = new Stack<DatagramChannel>();
	}

	public DatagramChannel get() {
		DatagramChannel sock = null;

		synchronized (this.sockStack) {
			try {
				sock = this.sockStack.pop();
			}
			catch (EmptyStackException e) {
				try {
					sock = DatagramChannel.open();
				}
				catch (IOException e0) {
					// NOTREACHED
					logger.log(Level.WARNING, "Cound not instantiate a DatagramSocket.");
				}
			}
		}

		return sock;
	}

	public void put(DatagramChannel sock) {
		synchronized (this.sockStack) {
			if (this.sockStack.size() < this.capacity) {
				this.sockStack.push(sock);
			}
		}
	}
}

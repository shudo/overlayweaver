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

package ow.messaging.tcp;

import ow.messaging.MessagingConfiguration;

public final class TCPMessagingConfiguration extends MessagingConfiguration {
	public final static int DEFAULT_CONNECTION_POOL_SIZE = 3;
		// Connection pool is disabled if 0 or a negative value is specified.
	public final static long DEFAULT_RECEIVER_KEEP_ALIVE_TIME = 20 * 1000L;	// 20 sec
	public final static long DEFAULT_SENDER_KEEP_ALIVE_TIME = 15 * 1000L;	// 15 sec
		// SENDER_KEEP_ALIVE_TIME should be less than RECEIVER_KEEP_ALIVE_TIME.
		// If not, after a receiver closes a socket first,
		// a receiver is in FIN_WAIT2 state, a sender is in CLOSE_WAIT state,
		// and the sender can send a message and it is not received by the receiver.

	private int connectionPoolSize = DEFAULT_CONNECTION_POOL_SIZE;
	public int getConnectionPoolSize() { return this.connectionPoolSize; }
	public int setConnectionPoolSize(int size) {
		int old = this.connectionPoolSize;
		this.connectionPoolSize = size;
		return old;
	}

	private long receiverKeepAliveTime = DEFAULT_RECEIVER_KEEP_ALIVE_TIME;
	public long getReceiverKeepAliveTime() { return this.receiverKeepAliveTime; }
	public long setReceiverKeepAliveTime(long time) {
		long old = this.receiverKeepAliveTime;
		this.receiverKeepAliveTime = time;
		return old;
	}

	private long senderKeepAliveTime = DEFAULT_SENDER_KEEP_ALIVE_TIME;
	public long getSenderKeepAliveTime() { return this.senderKeepAliveTime; }
	public long setSenderKeepAliveTime(long time) {
		long old = this.senderKeepAliveTime;
		this.senderKeepAliveTime = time;
		return old;
	}
}

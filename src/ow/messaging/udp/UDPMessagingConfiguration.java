/*
 * Copyright 2006 National Institute of Advanced Industrial Science
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

import ow.messaging.MessagingConfiguration;

public final class UDPMessagingConfiguration extends MessagingConfiguration {
	public final static boolean DEFAULT_DO_UDP_HOLE_PUNCHING = true;
	public final static int DEFAULT_PUNCHING_RETRY = 3;
	public final static long DEFAULT_PUNCHING_INTERVAL = 120 * 1000L;	// 2 minutes
	public final static long DEFAULT_PUNCHING_REP_TIMEOUT = 3 * 1000L;
	public final static long DEFAULT_PUNCHING_CHECK_INTERVAL = 30 * 1000L;
	public final static int DEFAULT_SOCKET_POOL_SIZE = 3;

	private boolean doUDPHolePunching = DEFAULT_DO_UDP_HOLE_PUNCHING;
	public boolean getDoUDPHolePunching() { return this.doUDPHolePunching; }
	public boolean setDoUDPHolePunching(boolean punch) {
		boolean old = this.doUDPHolePunching;
		this.doUDPHolePunching = punch;
		return old;
	}

	private int punchingRetry = DEFAULT_PUNCHING_RETRY;
	public int getPunchingRetry() { return this.punchingRetry; }
	public int setPunchingRetry(int retry) {
		int old = this.punchingRetry;
		this.punchingRetry = retry;
		return old;
	}

	private long punchingInterval = DEFAULT_PUNCHING_INTERVAL;
	public long getPunchingInterval() { return this.punchingInterval; }
	public long setPunchingInterval(long interval) {
		long old = this.punchingInterval;
		this.punchingInterval = interval;
		return old;
	}

	private long punchingRepTimeout = DEFAULT_PUNCHING_REP_TIMEOUT;
	public long getPunchingRepTimeout() { return this.punchingRepTimeout; }
	public long setPunchingRepTimeout(long timeout) {
		long old = this.punchingRepTimeout;
		this.punchingRepTimeout = timeout;
		return old;
	}

	private long punchingCheckInterval = DEFAULT_PUNCHING_CHECK_INTERVAL;
	public long getPunchingCheckInterval() { return this.punchingCheckInterval; }
	public long setPunchingCheckInterval(long interval) {
		long old = this.punchingCheckInterval;
		this.punchingCheckInterval = interval;
		return old;
	}

	private int socketPoolSize = DEFAULT_SOCKET_POOL_SIZE;
	public int getSocketPoolSize() { return this.socketPoolSize; }
	public int setSocketPoolSize(int size) {
		int old = this.socketPoolSize;
		this.socketPoolSize = size;
		return old;
	}
}

/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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

package ow.messaging;

public class MessagingConfiguration {
	public final static boolean DEFAULT_DO_UPNP_NAT_TRAVERSAL = true;
	public final static long DEFAULT_UPNP_TIMEOUT = 90 * 1000L;	// msec
	public final static boolean DEFAULT_DO_TIMEOUT_CALCULATION = true;
	public final static int DEFAULT_TIMEOUT_MIN = 2 * 1000;		// msec
	public final static int DEFAULT_TIMEOUT_MAX = 120 * 1000;	// msec
	public final static int DEFAULT_RTT_KEEPING_PERIOD = 5;
	public final static int DEFAULT_STATIC_TIMEOUT = 5 * 1000;	// msec
	public final static int DEFAULT_RTT_TABLE_SIZE = 100;
	public final static boolean DEFAULT_USE_THREAD_POOL = true;
	public final static int DEFAULT_RECEIVER_THREAD_PRIORITY = 1;

	public final static boolean DO_HOSTNAME_LOOKUP = true;

	private boolean doUPnPNATTraversal = DEFAULT_DO_UPNP_NAT_TRAVERSAL;
	public boolean getDoUPnPNATTraversal() { return this.doUPnPNATTraversal; }
	public boolean setDoUPnPNATTraversal(boolean flag) {
		boolean old = this.doUPnPNATTraversal;
		this.doUPnPNATTraversal = flag;
		return old;
	}

	private long upnpTimeout = DEFAULT_UPNP_TIMEOUT;
	public long getUPnPTimeout() { return this.upnpTimeout; }
	public long setUPnPTimeout(long timeout) {
		long old = this.upnpTimeout;
		this.upnpTimeout = timeout;
		return old;
	}

	private boolean doTimeoutCalculation = DEFAULT_DO_TIMEOUT_CALCULATION;
	public boolean getDoTimeoutCalculation() { return this.doTimeoutCalculation; }
	public boolean setDoTimeoutCalculation(boolean flag) {
		boolean old = this.doTimeoutCalculation;
		this.doTimeoutCalculation = flag;
		return old;
	}

	private int timeoutMin = DEFAULT_TIMEOUT_MIN;
	public int getTimeoutMin() { return this.timeoutMin; }
	public int setTimeoutMin(int timeout) {
		int old = this.timeoutMin;
		this.timeoutMin = timeout;
		return old;
	}

	private int timeoutMax = DEFAULT_TIMEOUT_MAX;
	public int getTimeoutMax() { return this.timeoutMax; }
	public int setTimeoutMax(int timeout) {
		int old = this.timeoutMax;
		this.timeoutMax = timeout;
		return old;
	}

	private int rttKeepingPeriod = DEFAULT_RTT_KEEPING_PERIOD;
	public int getRTTKeepingPeriod() { return this.rttKeepingPeriod; }
	public int setRTTKeepingPeriod(int period) {
		int old = this.rttKeepingPeriod;
		this.rttKeepingPeriod = period;
		return old;
	}

	private int staticTimeout = DEFAULT_STATIC_TIMEOUT;
	/**
	 * A Messaging Service causes a time-out when it could not receive a reply in this period.
	 */
	public int getStaticTimeout() { return this.staticTimeout; }
	public int setStaticTimeout(int timeout) {
		int old = this.staticTimeout;
		this.staticTimeout = timeout;
		return old;
	}

	private int rttTableSize = DEFAULT_RTT_TABLE_SIZE;
	public int getRTTTableSize() { return this.rttTableSize; }
	public int setRTTTableSize(int size) {
		int old = this.rttTableSize;
		this.rttTableSize = size;
		return old;
	}

	private boolean useThreadPool = DEFAULT_USE_THREAD_POOL;
	public boolean getUseThreadPool() { return this.useThreadPool; }
	public boolean setUseThreadPool(boolean use) {
		boolean old = this.useThreadPool;
		this.useThreadPool = use;
		return old;
	}

	private int receiverThreadPriority = DEFAULT_RECEIVER_THREAD_PRIORITY;
	public int getReceiverThreadPriority() { return this.receiverThreadPriority; }
	public int setReceiverThreadPriority(int prio) {
		int old = this.receiverThreadPriority;
		this.receiverThreadPriority = prio;
		return old;
	}
}

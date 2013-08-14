/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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

package ow.mcast;

import ow.util.HighLevelServiceConfiguration;

public final class McastConfiguration extends HighLevelServiceConfiguration {
	// for Mcast
	public final static long DEFAULT_REFRESH_INTERVAL = 8 * 1000L;
	public final static long DEFAULT_NEIGHBOR_EXPIRATION = 33 * 1000L;
	public final static long DEFAULT_NEIGHBOR_EXP_CHECK_INTERVAL = 5 * 1000L; 
	public final static long DEFAULT_CONNECT_REFUSE_DURATION = 13 * 1000L;
		// a bit longer than refresh interval
	public final static int DEFAULT_MULTICAST_TTL = 127;
	public final static boolean DEFAULT_USE_TIMER_INSTEAD_OF_THREAD = true;


	protected McastConfiguration() {}
		// prohibits instantiation directly by other classes


	private long refreshInterval = DEFAULT_REFRESH_INTERVAL;
	public long getRefreshInterval() { return this.refreshInterval; }
	public long setRefreshInterval(long interval) {
		long old = this.refreshInterval;
		this.refreshInterval = interval;
		return old;
	}

	private long neighborExpiration = DEFAULT_NEIGHBOR_EXPIRATION;
	public long getNeighborExpiration() { return this.neighborExpiration; }
	public long setNeighborExpiration(long expire) {
		long old = this.neighborExpiration;
		this.neighborExpiration = expire;
		return old;
	}

	private long neighborExpireCheckInterval = DEFAULT_NEIGHBOR_EXP_CHECK_INTERVAL;
	public long getNeighborExpireCheckInterval() { return this.neighborExpireCheckInterval; }
	public long setNeighborExpireCheckInterval(long interval) {
		long old = this.neighborExpireCheckInterval;
		this.neighborExpireCheckInterval = interval;
		return old;
	}

	private long connectRefuseDuration = DEFAULT_CONNECT_REFUSE_DURATION;
	public long getConnectRefuseDuration() { return this.connectRefuseDuration; }
	public long setConnectRefuseDuration(long duration) {
		long old = this.connectRefuseDuration;
		this.neighborExpireCheckInterval = duration;
		return old;
	}

	private int multicastTTL = DEFAULT_MULTICAST_TTL;
	public int getMulticastTTL() { return this.multicastTTL; }
	public int setMulticastTTL(int ttl) {
		int old = this.multicastTTL;
		this.multicastTTL = ttl;
		return old;
	}

	private boolean useTimer = DEFAULT_USE_TIMER_INSTEAD_OF_THREAD;
	public boolean getUseTimerInsteadOfThread() { return this.useTimer; }
	public boolean setUseTimerInsteadOfThread(boolean flag) {
		boolean old = this.useTimer;
		this.useTimer = flag;
		return old;
	}
}

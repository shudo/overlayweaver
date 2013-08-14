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

package ow.tool.mrouted;

public final class ApplicationLevelMulticastRouterConfiguration {
	public final static int DEFAULT_PORT_DIFF_FROM_MCAST = 2;
	public final static int DEFAULT_TTL_ON_OVERLAY = 128;
	public final static int DEFAULT_DATAGRAM_BUFFER_SIZE = 16384;
	public final static int DEFAULT_LOCAL_MULTICAST_TTL = 1;
	public final static long DEFAULT_SELF_MEMBERSHIP_EXPIRATION = 260 * 1000L;	// 260 sec
	public final static long DEFAULT_SELF_MEMBERSHIP_EXP_CHECK_INTERVAL = 10 * 1000L;

	private int portDiffFromMcast = DEFAULT_PORT_DIFF_FROM_MCAST;
	public int getPortDiffFromMcast() { return this.portDiffFromMcast; }
	public int setPortDiffFromMcast(int diff) {
		int old = this.portDiffFromMcast;
		this.portDiffFromMcast = diff;
		return old;
	}

	private int ttlOnOverlay = DEFAULT_TTL_ON_OVERLAY;
	public int getTTLOnOverlay() { return this.ttlOnOverlay; }
	public int setTTLOnOverlay(int ttl) {
		int old = this.ttlOnOverlay;
		this.ttlOnOverlay = ttl;
		return old;
	}

	private int datagramBufferSize = DEFAULT_DATAGRAM_BUFFER_SIZE;
	public int getDatagramBufferSize() { return this.datagramBufferSize; }
	public int setDatagramBufferSize(int size) {
		int old = this.datagramBufferSize;
		this.datagramBufferSize = size;
		return old;
	}

	private int localMcastTTL = DEFAULT_LOCAL_MULTICAST_TTL;
	public int getLocalMulticastTTL() { return this.localMcastTTL; }
	public int setLocalMulticastTTL(int ttl) {
		int old = this.localMcastTTL;
		this.localMcastTTL = ttl;
		return old;
	}

	private long selfMembershipExpiration = DEFAULT_SELF_MEMBERSHIP_EXPIRATION;
	public long getSelfMembershipExpiration() { return this.selfMembershipExpiration; }
	public long setSelfMembershipExpiration(long expiration) {
		long old = this.selfMembershipExpiration;
		this.selfMembershipExpiration = expiration;
		return old;
	}

	private long selfMembershipExpCheckInterval = DEFAULT_SELF_MEMBERSHIP_EXP_CHECK_INTERVAL;
	public long getSelfMembershipExpCheckInterval() { return this.selfMembershipExpCheckInterval; }
	public long setSelfMembershipExpCheckInterval(long interval) {
		long old = this.selfMembershipExpCheckInterval;
		this.selfMembershipExpCheckInterval = interval;
		return old;
	}
}

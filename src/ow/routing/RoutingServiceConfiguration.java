/*
 * Copyright 2006-2007,2010 National Institute of Advanced Industrial Science
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

package ow.routing;

public class RoutingServiceConfiguration {
	public final static int DEFAULT_TTL = 160;
	public final static int DEFAULT_NUM_OF_NEXT_HOP_CANDIDATES_REQUESTED = 8;
	public final static int DEFAULT_NUM_OF_RESPONSIBLE_NODE_CANDIDATES_REQUESTED_WHEN_JOINING = 4;
	public final static int DEFAULT_NUM_OF_NODES_MAINTAINED = 20;
//	public final static int DEFAULT_QUERY_CONCURRENCY = 3;	// 3 in the Kademlia paper
	public final static boolean DEFAULT_USE_THREAD_POOL = true;
	public final static long DEFAULT_ROUTING_TIMEOUT = 30 * 1000L;

	private int ttl = DEFAULT_TTL;
	public int getTTL() { return this.ttl; }
	public int setTTL(int ttl) {
		int old = this.ttl;
		this.ttl = ttl;
		return old;
	}

	private int numOfNextHopCandidatesRequested = DEFAULT_NUM_OF_NEXT_HOP_CANDIDATES_REQUESTED;
	public int getNumOfNextHopCandidatesRequested() { return this.numOfNextHopCandidatesRequested; }
	public int setNumOfNextHopCandidatesRequested(int num) {
		int old = this.numOfNextHopCandidatesRequested;
		this.numOfNextHopCandidatesRequested = num;
		return old;
	}

	private int numOfRespCandRequestedWhenJoining = DEFAULT_NUM_OF_RESPONSIBLE_NODE_CANDIDATES_REQUESTED_WHEN_JOINING;
	public int getNumOfResponsibleNodeCandidatesRequestedWhenJoining() { return this.numOfRespCandRequestedWhenJoining; }
	public int setNumOfResponsibleNodeCandidatesRequestedWhenJoining(int num) {
		int old = this.numOfRespCandRequestedWhenJoining;
		this.numOfRespCandRequestedWhenJoining = num;
		return old;
	}

	private int numOfNodesMaintained = DEFAULT_NUM_OF_NODES_MAINTAINED;
	/**
	 * Number of nodes a routing driver has as its current contancts.
	 * This property is provided mainly for
	 * {@link ow.routing.impl.IterativeRoutingDriver IterativeRoutingDriver}.
	 * 20 for Kademlia.
	 */
	public int getNumOfNodesMaintained() { return this.numOfNodesMaintained; }
	public int setNumOfNodesMaintained(int num) {
		int old = this.numOfNodesMaintained;
		this.numOfNodesMaintained = num;
		return old;
	}

//	private int queryConcurrency = DEFAULT_QUERY_CONCURRENCY;
//	/**
//	 * Concurrency of simultaneous queries.
//	 * This property is valid only for combinations of
//	 * {@link ow.routing.impl.IterativeRoutingDriver IterativeRoutingDriver}
//	 * and a routing algorithm which query to all contacts
//	 * (e.g. {@link ow.routing.kademlia.Kademlia Kademlia}).
//	 */
//	public int getQueryConcurrency() { return this.queryConcurrency; }
//	public int setQueryConcurrency(int concurrency) {
//		int old = this.queryConcurrency;
//		this.queryConcurrency = concurrency;
//		return old;
//	}

	private boolean useThreadPool = DEFAULT_USE_THREAD_POOL;
	public boolean getUseThreadPool() { return this.useThreadPool; }
	public boolean setUseThreadPool(boolean use) {
		boolean old = this.useThreadPool;
		this.useThreadPool = use;
		return old;
	}

	private long routingTimeout = DEFAULT_ROUTING_TIMEOUT;
	/**
	 * A Routing Service gives up a routing when it could not receive a results of the routing.
	 * Note that only recursive one causes a time-out.
	 */
	public long getRoutingTimeout() { return this.routingTimeout; }
	public long setRoutingTimeout(long timeout) {
		long old = this.routingTimeout;
		this.routingTimeout = timeout;
		return old;
	}
}

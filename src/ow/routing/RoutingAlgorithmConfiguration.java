/*
 * Copyright 2006-2007,2010,2012 National Institute of Advanced Industrial Science
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

/**
 * An instance of this class holds a configuration of a routing algorithm.
 */
public class RoutingAlgorithmConfiguration {	// not an abstract class for ow.dht.impl.CHTImpl
	public final static int DEFAULT_ID_SIZE = 20;	// byte
	public final static int DEFAULT_NUM_OF_FAILURES_BEFORE_FORGET_NODE = 1;
	public final static long DEFAULT_FAILURE_EXPIRATION = 10 * 1000L;
	public final static boolean DEFAULT_USE_TIMER_INSTEAD_OF_THREAD = true;

	private int idSize = DEFAULT_ID_SIZE;
	public int getIDSizeInByte() { return this.idSize; }
	public int setIDSizeInByte(int size) {
		int old = this.idSize;
		this.idSize = size;
		return old;
	}

	private int numOfFailuresBeforeForgetNode = DEFAULT_NUM_OF_FAILURES_BEFORE_FORGET_NODE;
	public int getNumOfFailuresBeforeForgetNode() { return this.numOfFailuresBeforeForgetNode; }
	public int setNumOfFailuresBeforeForgetNode(int num) {
		int old = this.numOfFailuresBeforeForgetNode;
		this.numOfFailuresBeforeForgetNode = num;
		return old;
	}

	private long failureExpiration = DEFAULT_FAILURE_EXPIRATION;
	public long getFailureExpiration() { return this.failureExpiration; }
	public long setFailureExpiration(long expiration) {
		long old = failureExpiration;
		this.failureExpiration = expiration;
		return old;
	}

	private boolean useTimer = DEFAULT_USE_TIMER_INSTEAD_OF_THREAD;
	public boolean getUseTimerInsteadOfThread() { return this.useTimer; }
	public boolean setUseTimerInsteadOfThread(boolean flag) {
		boolean old = this.useTimer;
		this.useTimer = flag;
		return old;
	}

	/**
	 * Whether if a routing driver asks to all contacts it is maintaining.
	 * This property should be true for {@link ow.routing.kademlia.KademliaConfiguration Kademlia}.
	 */
	public boolean queryToAllContacts() { return false; }

	/**
	 * Whether if a joining node is inserted into routing tables of existing nodes.
	 * This property should be true for {@link ow.routing.kademlia.KademliaConfiguration Kademlia},
	 * false for {@link ow.routing.frtchord.FRTChordConfiguration FRT-Chord},
	 * and either for other algorithms.
	 */
	public boolean insertJoiningNodeIntoRoutingTables() { return false; }
}

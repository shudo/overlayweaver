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

package ow.routing.linearwalker;

import ow.routing.RoutingAlgorithmConfiguration;

public class LinearWalkerConfiguration extends RoutingAlgorithmConfiguration {
	public final static int DEFAULT_SUCCESSOR_LIST_LENGTH = 8;	// infinity if the value <= 0
	public final static boolean DEFAULT_AGGRESSIVE_JOINING_MODE = false;
	public final static boolean DEFAULT_UPDATE_ROUTING_TABLE_BY_ALL_COMMUNICATIONS = false;

	public final static long DEFAULT_STABILIZE_MIN_INTERVAL = 125L;
	public final static long DEFAULT_STABILIZE_MAX_INTERVAL = 128 * 1000L;
	public final static double DEFAULT_STABILIZE_INTERVAL_PLAY_RATIO = 0.3;
	public final static int DEFAULT_UPDATE_PREDECESSOR_FREQ = 10;

	protected LinearWalkerConfiguration() {}

	private int successorListLength = DEFAULT_SUCCESSOR_LIST_LENGTH;
	public int getSuccessorListLength() { return this.successorListLength; }
	public int setSuccessorListLength(int len) {
		int old = this.successorListLength;
		this.successorListLength = len;
		return old;
	}

	private boolean aggressiveJoining = DEFAULT_AGGRESSIVE_JOINING_MODE;
	public boolean getAggressiveJoiningMode() { return this.aggressiveJoining; }
	public boolean setAggressiveJoiningMode(boolean flag) {
		boolean old = this.aggressiveJoining;
		this.aggressiveJoining = flag;
		return old;
	}

	private boolean updateRoutingTableByAllCommunications = DEFAULT_UPDATE_ROUTING_TABLE_BY_ALL_COMMUNICATIONS;
	public boolean getUpdateRoutingTableByAllCommunications() { return this.updateRoutingTableByAllCommunications; }
	public boolean setUpdateRoutingTableByAllCommunications(boolean flag) {
		boolean old = this.updateRoutingTableByAllCommunications;
		this.updateRoutingTableByAllCommunications = flag;
		return old;
	}

	private long stabilizeMinInterval = DEFAULT_STABILIZE_MIN_INTERVAL;
	public long getStabilizeMinInterval() { return this.stabilizeMinInterval; }
	public long setStabilizeMinInterval(long interval) {
		long old = this.stabilizeMinInterval;
		this.stabilizeMinInterval = interval;
		return old;
	}

	private long stabilizeMaxInterval = DEFAULT_STABILIZE_MAX_INTERVAL;
	public long getStabilizeMaxInterval() { return this.stabilizeMaxInterval; }
	public long setStabilizeMaxInterval(long interval) {
		long old = this.stabilizeMaxInterval;
		this.stabilizeMaxInterval = interval;
		return old;
	}

	private double stabilizeIntervalPlayRatio = DEFAULT_STABILIZE_INTERVAL_PLAY_RATIO;
	public double getStabilizeIntervalPlayRatio() { return this.stabilizeIntervalPlayRatio; }
	public double setStabilizeIntervalPlayRatio(double play) {
		double old = this.stabilizeIntervalPlayRatio;
		this.stabilizeIntervalPlayRatio = play;
		return old;
	}

	private int updatePredecessorFreq = DEFAULT_UPDATE_PREDECESSOR_FREQ;
	public int getUpdatePredecessorFreq() { return this.updatePredecessorFreq; }
	public int setUpdatePredecessorFreq(int freq) {
		int old = this.updatePredecessorFreq;
		this.updatePredecessorFreq = freq;
		return old;
	}
}

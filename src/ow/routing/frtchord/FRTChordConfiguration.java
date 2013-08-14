/*
 * Copyright 2010-2011 Kazuyuki Shudo, and contributors.
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

package ow.routing.frtchord;

import ow.routing.RoutingAlgorithmConfiguration;

/**
 * A class holding configuration for FRT-Chord.
 */
public final class FRTChordConfiguration extends RoutingAlgorithmConfiguration {
	public final static int DEFAULT_ROUTING_TABLE_SIZE = 16;
	public final static int DEFAULT_NUM_STICKY_NODES = 4;

	public final static boolean DEFAULT_DO_STABILIZATION = true;
	public final static long DEFAULT_STABILIZE_MIN_INTERVAL = 125L;
	public final static long DEFAULT_STABILIZE_MAX_INTERVAL = 128 * 1000L;
	public final static double DEFAULT_STABILIZE_INTERVAL_PLAY_RATIO = 0.3;
	public final static int DEFAULT_UPDATE_FARTHER_STICKY_NODE_FREQ = 10;

	public final static boolean DEFAULT_ONE_HOP_DHT = false;

	protected FRTChordConfiguration() {}

	private int routingTableSize = DEFAULT_ROUTING_TABLE_SIZE;
	/**
	 * The size of routing table.
	 * Default value is 10.
	 */
	public int getRoutingTableSize() { return this.routingTableSize; }
	public int setRoutingTableSize(int size) {
		int old = this.routingTableSize;
		this.routingTableSize = size;
		return old;
	}

	private int numStickyNodes = DEFAULT_NUM_STICKY_NODES;
	/**
	 * The number of sticky nodes, which are not removed though the routing table overflows.
	 * They correspond to nodes in a successor list in Chord.
	 */
	public int getNumStickyNodes() { return this.numStickyNodes; }
	public int setNumStickyNodes(int num) {
		int old = this.numStickyNodes;
		this.numStickyNodes = num;
		return old;
	}

	private boolean doStabilization = DEFAULT_DO_STABILIZATION;
	/**
	 * If true, a node performs stabilization.
	 */
	public boolean getDoStabilization() { return this.doStabilization; }
	public boolean setDoStabilization(boolean flag) {
		boolean old = this.doStabilization;
		this.doStabilization = flag;
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

	private int updateFartherStickyNodeFreq = DEFAULT_UPDATE_FARTHER_STICKY_NODE_FREQ;
	public int getUpdateFartherStickyNodeFreq() { return this.updateFartherStickyNodeFreq; }
	public int setUpdateFartherStickyNodeFreq(int freq) {
		int old = this.updateFartherStickyNodeFreq;
		this.updateFartherStickyNodeFreq = freq;
		return old;
	}

	private boolean oneHopDHT = DEFAULT_ONE_HOP_DHT;
	/**
	 * If true, FRT-Chord works as an one-hop DHT.
	 */
	public boolean getOneHopDHT() { return this.oneHopDHT; }
	public boolean setOneHopDHT(boolean flag) {
		boolean old = this.oneHopDHT;
		this.oneHopDHT = flag;
		return old;
	}
}

/*
 * Copyright 2006,2009 National Institute of Advanced Industrial Science
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

package ow.routing.chord;

import ow.routing.linearwalker.LinearWalkerConfiguration;

/**
 * A class holding configuration for Chord.
 */
public final class ChordConfiguration extends LinearWalkerConfiguration {
	public final static boolean DEFAULT_DO_FIX_FINGERS = true;
	public final static long DEFAULT_FIX_FINGERS_INITIAL_INTERVAL = 1 * 1000L;
	public final static long DEFAULT_FIX_FINGERS_MIN_INTERVAL = 30 * 1000L;
	public final static long DEFAULT_FIX_FINGERS_MAX_INTERVAL = 240 * 1000L;
	public final static double DEFAULT_FIX_FINGERS_INTERVAL_PLAY_RATIO = 0.3;
	public final static double DEFAULT_PROB_PROPORTIONAL_TO_ID_SPACE = 0.7;

	protected ChordConfiguration() {}

	private boolean doFixFingers = DEFAULT_DO_FIX_FINGERS;
	public boolean getDoFixFingers() { return this.doFixFingers; }
	public boolean setDoFixFingers(boolean flag) {
		boolean old = this.doFixFingers;
		this.doFixFingers = flag;
		return old;
	}

	private long fixFingersInitialInterval = DEFAULT_FIX_FINGERS_INITIAL_INTERVAL;
	public long getFixFingersInitialInterval() { return this.fixFingersInitialInterval; }
	public long setFiFingersInitialInterval(long interval) {
		long old = this.fixFingersInitialInterval;
		this.fixFingersInitialInterval = interval;
		return old;
	}

	private long fixFingersMinInterval = DEFAULT_FIX_FINGERS_MIN_INTERVAL;
	public long getFixFingersMinInterval() { return this.fixFingersMinInterval; }
	public long setFiFingersMinInterval(long interval) {
		long old = this.fixFingersMinInterval;
		this.fixFingersMinInterval = interval;
		return old;
	}

	private long fixFingersMaxInterval = DEFAULT_FIX_FINGERS_MAX_INTERVAL;
	public long getFixFingersMaxInterval() { return this.fixFingersMaxInterval; }
	public long setFiFingersMaxInterval(long interval) {
		long old = this.fixFingersMaxInterval;
		this.fixFingersMaxInterval = interval;
		return old;
	}

	private double fixFingersIntervalPlayRatio = DEFAULT_FIX_FINGERS_INTERVAL_PLAY_RATIO;
	public double getFixFingersIntervalPlayRatio() { return this.fixFingersIntervalPlayRatio; }
	public double setFiFingersIntervalPlayRatio(double ratio) {
		double old = this.fixFingersIntervalPlayRatio;
		this.fixFingersIntervalPlayRatio = ratio;
		return old;
	}

	private double probProportional = DEFAULT_PROB_PROPORTIONAL_TO_ID_SPACE;
	public double getProbProportionalToIDSpace() { return this.probProportional; }
	public double setProbProportionalToIDSpace(double prob) {
		double old = this.probProportional;
		this.probProportional = prob;
		return old;
	}
}

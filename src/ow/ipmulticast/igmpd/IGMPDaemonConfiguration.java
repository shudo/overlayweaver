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

package ow.ipmulticast.igmpd;

public final class IGMPDaemonConfiguration {
	public final static boolean DEFAULT_KEEP_BEING_QUERIER = true;
	public final static int DEFAULT_UPDATE_INTERVAL = 5000;			// in units of 1/1000 sec

	public final static int DEFAULT_INITIAL_ROBUSTNESS_VARIABLE = 2;
	public final static int DEFAULT_INITIAL_QUERY_INTERVAL = 125;	// in sec
	public final static int DEFAULT_QUERY_RESPONSE_INTERVAL = 100;	// in units of 1/10 sec

	private boolean keepBeginQuerier = DEFAULT_KEEP_BEING_QUERIER;
	public boolean getKeepBeingQuerier() { return this.keepBeginQuerier; }
	public boolean setKeepBeingQuerier(boolean flag) {
		boolean old = this.keepBeginQuerier;
		this.keepBeginQuerier = flag;
		return old;
	}

	private int updateInterval = DEFAULT_UPDATE_INTERVAL;
	public int getUpdateInterval() { return this.updateInterval; }
	public int setUpdateInterval(int interval) {
		int old = this.updateInterval;
		this.updateInterval = interval;
		return old;
	}

	private int initialRobustnessVariable = DEFAULT_INITIAL_ROBUSTNESS_VARIABLE;
	public int getInitialRobustnessVariable() { return this.initialRobustnessVariable; }
	public int setInitialRobustnessVariable(int v) {
		int old = this.initialRobustnessVariable;
		this.initialRobustnessVariable = v;
		return old;
	}

	private int initialQueryInterval = DEFAULT_INITIAL_QUERY_INTERVAL;
	public int getInitialQueryInterval() { return this.initialQueryInterval; }
	public int setInitialQueryInterval(int interval) {
		int old = this.initialQueryInterval;
		this.initialQueryInterval = interval;
		return old;
	}

	private int queryResponseInterval = DEFAULT_QUERY_RESPONSE_INTERVAL;
	public int getQueryResponseInterval() { return this.queryResponseInterval; }
	public int setQueryResponseInterval(int interval) {
		int old = this.queryResponseInterval;
		this.queryResponseInterval = interval;
		return old;
	}
}

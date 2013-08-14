/*
 * Copyright 2006-2010 National Institute of Advanced Industrial Science
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

package ow.dht;

import ow.util.HighLevelServiceConfiguration;

public class DHTConfiguration extends HighLevelServiceConfiguration {
	public final static String DEFAULT_IMPL_NAME = "ChurnTolerantDHT";
//	public final static String DEFAULT_IMPL_NAME = "BasicDHT";
//	public final static String DEFAULT_IMPL_NAME = "CHT";	// Centralized Hash Table

	// for Directory
	public final static String DEFAULT_DIRECTORY_TYPE = "VolatileMap";
		// "BerkeleyDB", "PersistentMap" or "VolatileMap"
	public final static Class DEFAULT_VALUE_CLASS = String.class;
	public final static String DEFAULT_WORKING_DIR = ".";

	public final static boolean DEFAULT_DO_EXPIRE = true;
	public final static long DEFAULT_MAXIMUM_TTL = 7 * 24 * 60 * 60 * 1000;	// 7 days
	public final static long DEFAULT_DEFAULT_TTL = 3 * 60 * 60 * 1000;			// 3 hours

	// for DHT
	public final static boolean DEFAULT_MULTIPLE_VALUES_FOR_A_SINGLE_KEY = true;
	public final static int DEFAULT_NUM_SPARE_RESPONSIBLE_NODE_CANDIDATES = 3;

	public final static int DEFAULT_NUM_REPLICA = 1;
	public final static boolean DEFAULT_RESPONSIBLE_NODE_DOES_REPLICATION = true;
	public final static int DEFAULT_NUM_NODES_ASKED_TO_TRANSFER = 0;
	public final static int DEFAULT_NUM_TIMES_GETS = 1;

	public final static boolean DEFAULT_DO_REPUT_ON_REPLICAS = false;
	public final static boolean DEFAULT_DO_REPUT_ON_REQUESTER = false;
	public final static int[] DEFAULT_REPUT_PARAMS =
		{ 3 * 1000 /*msec*/, 10 /*keys*/, 10 /*intervals*/};
	public final static double DEFAULT_REPUT_INTERVAL_PLAY_RATIO = 0.2;
	public final static boolean DEFAULT_USE_TIMER_INSTEAD_OF_THREAD = true;

	public final static String DEFAULT_VALUE_ENCODING = "UTF-8";


	private String implName = DEFAULT_IMPL_NAME;
	public String getImplementationName() { return this.implName; }
	public String setImplementationName(String name) {
		String old = this.implName;
		this.implName = name;
		return old;
	}

	private String directoryType = DEFAULT_DIRECTORY_TYPE;
	public String getDirectoryType() { return this.directoryType; }
	public String setDirectoryType(String type) {
		String old = this.directoryType;
		this.directoryType = type;
		return old;
	}

	private Class valueClass = DEFAULT_VALUE_CLASS;
	public Class getValueClass() { return this.valueClass; }
	public Class setValueClass(Class clz) {
		Class old = this.valueClass;
		this.valueClass = clz;
		return old;
	}

	private String workingDirectory = DEFAULT_WORKING_DIR;
	public String getWorkingDirectory() { return this.workingDirectory; }
	public String setWorkingDirectory(String dir) {
		String old = this.workingDirectory;
		this.workingDirectory = dir;
		return old;
	}

	private boolean doExpire = DEFAULT_DO_EXPIRE;
	public boolean getDoExpire() { return this.doExpire; }
	public boolean setDoExpire(boolean flag) {
		boolean old = this.doExpire;
		this.doExpire = flag;
		return old;
	}

	private long maximumTTL = DEFAULT_MAXIMUM_TTL;
	public long getMaximumTTL() { return this.maximumTTL; }
	public long setMaximumTTL(long ttl) {
		long old = this.maximumTTL;
		this.maximumTTL = ttl;
		return old;
	}

	private long defaultTTL = DEFAULT_DEFAULT_TTL;
	public long getDefaultTTL() { return this.defaultTTL; }
	public long setDefaultTTL(long ttl) {
		long old = this.defaultTTL;
		this.defaultTTL = ttl;
		return old;
	}

	private boolean multipleValuesForASingleKey = DEFAULT_MULTIPLE_VALUES_FOR_A_SINGLE_KEY;
	public boolean getMultipleValuesForASingleKey() { return this.multipleValuesForASingleKey; }
	public boolean setMultipleValuesForASingleKey(boolean flag) {
		boolean old = this.multipleValuesForASingleKey;
		this.multipleValuesForASingleKey = flag;
		return old;
	}

	private int numSpareRespCands = DEFAULT_NUM_SPARE_RESPONSIBLE_NODE_CANDIDATES;
	public int getNumSpareResponsibleNodeCandidates() { return this.numSpareRespCands; }
	public int setNumSpareResponsibleNodeCandidates(int num) {
		int old = this.numSpareRespCands;
		this.numSpareRespCands = num;
		return old;
	}

	private int numReplica = DEFAULT_NUM_REPLICA;
	public int getNumReplica() { return this.numReplica; }
	public int setNumReplica(int num) {
		int old = this.numReplica;
		this.numReplica = num;
		return old;
	}

	private boolean respNodeDoesReplication = DEFAULT_RESPONSIBLE_NODE_DOES_REPLICATION;
	public boolean getResponsibleNodeDoesReplication() { return this.respNodeDoesReplication; }
	public boolean setResponsibleNodeDoesReplication(boolean flag) {
		boolean old = this.respNodeDoesReplication;
		this.respNodeDoesReplication = flag;
		return old;
	}

	private int numNodesAskedToTransfer = DEFAULT_NUM_NODES_ASKED_TO_TRANSFER;
	public int getNumNodesAskedToTransfer() { return this.numNodesAskedToTransfer; }
	public int setNumNodesAskedToTransfer(int num) {
		int old = this.numNodesAskedToTransfer;
		this.numNodesAskedToTransfer = num;
		return old;
	}

	private int numTimesGets = DEFAULT_NUM_TIMES_GETS;
	public int getNumTimesGets() { return this.numTimesGets; }
	public int setNumTimesGets(int num) {
		int old = this.numTimesGets;
		this.numTimesGets = num;
		return old;
	}

	// Note:
	// Replication does not work correctly with "memcached" if reputOnReplica is false,
	// because local cache does not support "memcached".
	private boolean doReputOnReplicas = DEFAULT_DO_REPUT_ON_REPLICAS;
	public boolean getDoReputOnReplicas() { return this.doReputOnReplicas; }
	public boolean setDoReputOnReplicas(boolean flag) {
		boolean old = this.doReputOnReplicas;
		this.doReputOnReplicas = flag;
		return old;
	}

	private boolean doReputOnRequester = DEFAULT_DO_REPUT_ON_REQUESTER;
	public boolean getDoReputOnRequester() { return this.doReputOnRequester; }
	public boolean setDoReputOnRequester(boolean flag) {
		boolean old = this.doReputOnRequester;
		this.doReputOnRequester = flag;
		return old;
	}

	private int[] reputParameters = DEFAULT_REPUT_PARAMS;

	/**
	 * Get parameters about reput.
	 */
	public int[] getReputParameters() { return this.reputParameters; }

	/**
	 * Set parameters about reput.
	 *
	 * @param params interval (msec), number of keys to be reput once, and number of intervals to restart.
	 */
	public int[] setReputParameters(int[] params) {
		int[] old = this.reputParameters;
		this.reputParameters = params;
		return old;
	}

	private double reputIntervalPlayRatio = DEFAULT_REPUT_INTERVAL_PLAY_RATIO;
	/**
	 * Get the play of interval between reputs.
	 */
	public double getReputIntervalPlayRatio() { return this.reputIntervalPlayRatio; }
	/**
	 * Set the play of interval between reputs.
	 *
	 * @param ratio play of interval in msec.
	 */
	public double setReputIntervalPlayRatio(double ratio) {
		double old = this.reputIntervalPlayRatio;
		this.reputIntervalPlayRatio = ratio;
		return old;
	}

	private boolean useTimer = DEFAULT_USE_TIMER_INSTEAD_OF_THREAD;
	public boolean getUseTimerInsteadOfThread() { return this.useTimer; }
	public boolean setUseTimerInsteadOfThread(boolean flag) {
		boolean old = this.useTimer;
		this.useTimer = flag;
		return old;
	}

	private String valueEncoding = DEFAULT_VALUE_ENCODING;
	public String getValueEncoding() { return this.valueEncoding; }
	public String setValueEncoding(String encoding) {
		String old = this.valueEncoding;
		this.valueEncoding = encoding;
		return old;
	}
}

/*
 * Copyright 2006,2008-2010 National Institute of Advanced Industrial Science
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

package ow.routing.pastry;

import ow.routing.plaxton.PlaxtonConfiguration;

/**
 * A class holding configuration for Pastry.
 */
public final class PastryConfiguration extends PlaxtonConfiguration {
	private final static int DEFAULT_ID_SIZE = 16;	// byte
	private final static boolean DEFAULT_USE_LEAF_SET = true;
	private final static int DEFAULT_LEAF_SET_ONE_SIDE_SIZE = 4;

	private final static boolean DEFAULT_DO_PERIODIC_ROUTING_TABLE_MAINTENANCE = true;
	private final static long DEFAULT_ROUTING_TABLE_MAINTENANCE_INTERVAL = 20 * 1000L;
	private final static double DEFAULT_ROUTING_TABLE_MAINTENANCE_INTERVAL_PLAY_RATIO = 0.5;
	public final static int DEFAULT_UPDATE_LEAF_SET_FREQ = 3;

	protected PastryConfiguration() {}

	private int idSize = DEFAULT_ID_SIZE;
	public int getIDSizeInByte() { return this.idSize; }
	public int setIDSizeInByte(int size) {
		int old = this.idSize;
		this.idSize = size;
		return old;
	}

	private boolean useLeafSet = DEFAULT_USE_LEAF_SET;
	public boolean getUseLeafSet() { return this.useLeafSet; }
	public boolean setUseLeafSet(boolean use) {
		boolean old = this.useLeafSet;
		this.useLeafSet = use;
		return old;
	}

	private int leafSetOneSideSize = DEFAULT_LEAF_SET_ONE_SIDE_SIZE;
	public int getLeafSetOneSideSize() { return this.leafSetOneSideSize; }
	public int setLeafSetOneSideSize(int size) {
		int old = this.leafSetOneSideSize;
		this.leafSetOneSideSize = size;
		return old;
	}

	private boolean doMaintenance = DEFAULT_DO_PERIODIC_ROUTING_TABLE_MAINTENANCE;
	public boolean getDoPeriodicRoutingTableMaintenance() { return this.doMaintenance; }
	public boolean setDoPeriodicRoutingTableMaintenance(boolean doMaintenance) {
		boolean old = this.doMaintenance;
		this.doMaintenance = doMaintenance;
		return old;
	}

	private long maintenanceInterval = DEFAULT_ROUTING_TABLE_MAINTENANCE_INTERVAL;
	public long getRoutingTableMaintenanceInterval() { return this.maintenanceInterval; }
	public long setRoutingTableMaintenanceInterval(long interval) {
		long old = this.maintenanceInterval;
		this.maintenanceInterval = interval;
		return old;
	}

	private double maintenanceIntervalPlayRatio = DEFAULT_ROUTING_TABLE_MAINTENANCE_INTERVAL_PLAY_RATIO;
	public double getRoutingTableMaintenanceIntervalPlayRatio() { return this.maintenanceIntervalPlayRatio; }
	public double setRoutingTableMaintenanceIntervalPlayRatio(int ratio) {
		double old = this.maintenanceIntervalPlayRatio;
		this.maintenanceIntervalPlayRatio = ratio;
		return old;
	}

	private int updateLeafSetFreq = DEFAULT_UPDATE_LEAF_SET_FREQ;
	public int getUpdateLeafSetFreq() { return this.updateLeafSetFreq; }
	public int setUpdateLeafSetFreq(int freq) {
		int old = this.updateLeafSetFreq;
		this.updateLeafSetFreq = freq;
		return old;
	}
}

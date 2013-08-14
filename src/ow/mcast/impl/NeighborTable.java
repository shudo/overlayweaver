/*
 * Copyright 2006,2009,2011 National Institute of Advanced Industrial Science
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

package ow.mcast.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.stat.MessagingReporter;
import ow.util.Timer;

class NeighborTable {
	private final Map<ID,Neighbor> parentMap =
		Collections.synchronizedMap(new HashMap<ID,Neighbor>());
	private final Map<ID,Set<Neighbor>> childrenMap =
		Collections.synchronizedMap(new HashMap<ID,Set<Neighbor>>());
	private final Map<ID,IDAddressPair[]> cachedChildrenMap =
		Collections.synchronizedMap(new HashMap<ID,IDAddressPair[]>());

	private final McastImpl mcast;
	private final MessagingReporter msgReporter;
	private final long expiration;
	private final Timer timer = Timer.getSingletonTimer();
	private TimerTask expiringTask = null;

	NeighborTable(McastImpl mcast, MessagingReporter msgReporter, long expiration) {
		this.mcast = mcast;
		this.msgReporter = msgReporter;
		this.expiration = expiration;
	}

	public Set<ID> getGroupsWithSpanningTree() {
		Set<ID> result = new HashSet<ID>();

		result.addAll(this.parentMap.keySet());
		result.addAll(this.childrenMap.keySet());

		return result;
	}

	public IDAddressPair getParent(ID groupID) {
		Neighbor parent = this.parentMap.get(groupID);
		if (parent != null)
			return parent.getIDAddressPair();
		else
			return null;
	}

	public IDAddressPair[] getChildren(ID groupID) {
		return this.cachedChildrenMap.get(groupID);
	}

	public boolean hasParent(ID groupID) {
		return this.parentMap.containsKey(groupID);
	}

	public boolean hasChild(ID groupID) {
		Set<Neighbor> childrenSet = this.childrenMap.get(groupID);
		return childrenSet != null;
	}

	public boolean registerParent(ID groupID, IDAddressPair parent) {
		boolean parentChanged = true;

		synchronized (this) {
			Neighbor oldParent = this.parentMap.remove(groupID);
			if (oldParent != null && parent.equals(oldParent.getIDAddressPair())) {
				parentChanged = false;
			}

			// refresh or add
			Neighbor neighbor = new Neighbor(parent);
			this.parentMap.remove(groupID);
			this.parentMap.put(groupID, neighbor);
		}

		this.initExpiringTask(Timer.currentTimeMillis() + this.expiration + 100L);

		return parentChanged;
	}

	public boolean registerChild(ID groupID, IDAddressPair child) {
		boolean added = false;

		synchronized (this) {
			Set<Neighbor> childrenSet = this.childrenMap.get(groupID);
			if (childrenSet == null) {
				childrenSet = new HashSet<Neighbor>();
				this.childrenMap.put(groupID, childrenSet);
			}

			// refresh or add
			Neighbor neighbor = new Neighbor(child);
			added = !childrenSet.remove(neighbor);
			childrenSet.add(neighbor);

			// update cache
			IDAddressPair[] cachedChildren = new IDAddressPair[childrenSet.size()];
			int i = 0;
			for (Neighbor n: childrenSet) cachedChildren[i++] = n.getIDAddressPair();
			this.cachedChildrenMap.put(groupID, cachedChildren);
		}

		this.initExpiringTask(Timer.currentTimeMillis() + this.expiration + 100L);

		return added;
	}

	public void removeParent(ID groupID, IDAddressPair parent) {
		synchronized (this) {
			Neighbor currentNeighbor = this.parentMap.get(groupID);
			if (currentNeighbor != null) {
				IDAddressPair currentParent = currentNeighbor.getIDAddressPair();
				if (parent.equals(currentParent)) {
					this.parentMap.remove(groupID);
				}
			}

			this.checkAndStopExpiringTask();
		}
	}

	public Set<ID> removeParent(IDAddressPair parent) {
		Set<ID> changedGroups = new HashSet<ID>();

		synchronized (this) {
			Set<ID> keySet = this.parentMap.keySet();
			ID[] keySetArray = new ID[keySet.size()];
			keySet.toArray(keySetArray);

			for (ID groupID: keySetArray) {
				Neighbor neighbor = this.parentMap.get(groupID);

				if (parent.equals(neighbor.getIDAddressPair())) {
					this.parentMap.remove(groupID);

					changedGroups.add(groupID);
				}
			}

			this.checkAndStopExpiringTask();
		}

		return changedGroups;
	}

	public boolean removeChild(ID groupID, IDAddressPair child) {
		boolean removed = false;
		boolean noChild = false;

		synchronized (this) {
			Set<Neighbor> childrenSet = this.childrenMap.get(groupID);
			if (childrenSet != null) {
				childrenSet.remove(new Neighbor(child));
				removed = true;

				if (childrenSet.isEmpty()) {
					this.childrenMap.remove(groupID);
					noChild = true;

					// update cache
					this.cachedChildrenMap.remove(groupID);
				}
				else {
					// update cache
					IDAddressPair[] cachedChildren = new IDAddressPair[childrenSet.size()];
					int i = 0;
					for (Neighbor n: childrenSet) cachedChildren[i++] = n.getIDAddressPair();
					this.cachedChildrenMap.put(groupID, cachedChildren);
				}
			}

			if (noChild && !mcast.joinedGroupSet.contains(groupID)) {
				mcast.disconnectParent(groupID);
			}

			this.checkAndStopExpiringTask();
		}	// synchronized (this)

		return removed;
	}

	public Set<ID> removeChild(IDAddressPair child) {
		Set<ID> noChildGroupSet = new HashSet<ID>();
		Set<ID> changedGroups = new HashSet<ID>();

		synchronized (this) {
			for (ID groupID: this.childrenMap.keySet()) {
				Set<Neighbor> childrenSet = this.childrenMap.get(groupID);

				if (childrenSet.remove(new Neighbor(child))) {
					changedGroups.add(groupID);
				}

				if (childrenSet.isEmpty()) {
					noChildGroupSet.add(groupID);

					// update cache
					this.cachedChildrenMap.remove(groupID);
				}
				else {
					// update cache
					IDAddressPair[] cachedChildren = new IDAddressPair[childrenSet.size()];
					int i = 0;
					for (Neighbor n: childrenSet) cachedChildren[i++] = n.getIDAddressPair();
					this.cachedChildrenMap.put(groupID, cachedChildren);
				}
			}

			for (ID noChildGroup: noChildGroupSet) {
				this.childrenMap.remove(noChildGroup);

				if (!mcast.joinedGroupSet.contains(noChildGroup)) {
					mcast.disconnectParent(noChildGroup);
				}
			}

			this.checkAndStopExpiringTask();
		}	// synchronized (this)

		return changedGroups;
	}

	public void clear() {
		synchronized (this) {
			this.parentMap.clear();
			this.childrenMap.clear();
			this.cachedChildrenMap.clear();

			this.stopExpiringTask();
		}
	}

	protected void initExpiringTask(long expiringTime) {
		synchronized (this) {
			if (this.expiringTask != null) {
				long scheduledTime = timer.getScheduledTime(this.expiringTask);
				if (expiringTime < scheduledTime) {
					// reschedule
//System.out.println("[ExpiringTask rescheduled: " + (expiringTime - Timer.currentTimeMillis()) + "]");
					timer.cancel(this.expiringTask);
					timer.schedule(this.expiringTask, expiringTime, true /*isDaemon*/);
				}
			}
			else {
				// create an ExpiringTask and schedule it
//System.out.println("[ExpiringTask created: " + (expiringTime - Timer.currentTimeMillis()) + "]");
				this.expiringTask = new ExpiringTask();
				timer.schedule(this.expiringTask, expiringTime, true /*isDaemon*/);
			}
		}
	}

	private synchronized void checkAndStopExpiringTask() {
		if (!this.parentMap.isEmpty() || !this.childrenMap.isEmpty()) return;

		this.stopExpiringTask();
	}

	protected synchronized void stopExpiringTask() {
		if (this.expiringTask != null) {
//System.out.println("[ExpiringTask stopped]");
			timer.cancel(this.expiringTask);
			this.expiringTask = null;
		}
	}

	private class ExpiringTask extends TimerTask {
		public void run() {
//System.out.println("[ExpiringTask started]");
			long currentTime = Timer.currentTimeMillis();
			long oldestUpdatedTime = Long.MAX_VALUE;
			long threshold = currentTime - NeighborTable.this.expiration;

			// children
			Set<ID> changedGroups = new HashSet<ID>();
			Set<ID> noChildGroupSet = new HashSet<ID>();

			synchronized (NeighborTable.this) {
				for (ID groupID: NeighborTable.this.childrenMap.keySet()) {
					Set<Neighbor> childrenSet = NeighborTable.this.childrenMap.get(groupID);
					boolean cacheToBeUpdated = false;

					Set<Neighbor> expiredChildrenSet = new HashSet<Neighbor>();

					for (Neighbor child: childrenSet) {
						long updatedTime = child.getUpdatedTime();
						if (threshold >= updatedTime) {
							// expire
							expiredChildrenSet.add(child);
							changedGroups.add(groupID);

							cacheToBeUpdated = true;

							NeighborTable.this.msgReporter.notifyStatCollectorOfDisconnectNodes(
									child.getIDAddressPair().getID(), mcast.getSelfIDAddressPair().getID(),
									groupID.hashCode());
						}
						else if (updatedTime < oldestUpdatedTime) {
							oldestUpdatedTime = updatedTime;
						}
					}

					childrenSet.removeAll(expiredChildrenSet);

					if (childrenSet.isEmpty()) {
						noChildGroupSet.add(groupID);
					}
					else {
						// update cache
						if (cacheToBeUpdated) {
							int nChildren = childrenSet.size();
							if (nChildren > 0) {
								IDAddressPair[] cachedChildren = new IDAddressPair[nChildren];
								int i = 0;
								for (Neighbor n: childrenSet) cachedChildren[i++] = n.getIDAddressPair();
								NeighborTable.this.cachedChildrenMap.put(groupID, cachedChildren);
							}
						}
					}
				}

				for (ID noChildGroup: noChildGroupSet) {
					NeighborTable.this.childrenMap.remove(noChildGroup);

					// update cache
					NeighborTable.this.cachedChildrenMap.remove(noChildGroup);

					mcast.disconnectParent(noChildGroup);
				}

				// parents
				Set<ID> keySet = NeighborTable.this.parentMap.keySet();
				ID[] keySetArray = new ID[keySet.size()];
				keySet.toArray(keySetArray);

				for (ID groupID: keySetArray) {
					Neighbor parent = NeighborTable.this.parentMap.get(groupID);

					if (parent != null) {
						long updatedTime = parent.getUpdatedTime();

						if (threshold >= updatedTime) {
							// expire
							mcast.disconnectParent(groupID, parent.getIDAddressPair());
						}
						else if (updatedTime < oldestUpdatedTime) {
							oldestUpdatedTime = updatedTime;
						}
					}
				}
			}	// synchronized (NeighborTable.this)

			for (ID groupID: changedGroups) {
				NeighborTable.this.mcast.invokeSpanningTreeChangedCallbacks(groupID);
			}

			// reschedule
			if (oldestUpdatedTime < Long.MAX_VALUE) {
//System.out.println("[ExpiringTask rescheduled: " + (oldestUpdatedTime + NeighborTable.this.expiration - Timer.currentTimeMillis()) + "]");
				// reschedule
				timer.schedule(this, oldestUpdatedTime + NeighborTable.this.expiration, true /*isDaemon*/);
			}
			else {
//System.out.println("[ExpiringTask finished]");
				// stop
				synchronized (NeighborTable.this) {
					NeighborTable.this.expiringTask = null;
				}
			}
		}	// public void run()
	}
}

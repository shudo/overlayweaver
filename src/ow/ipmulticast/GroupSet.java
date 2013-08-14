/*
 * Copyright 2006 National Institute of Advanced Industrial Science
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

package ow.ipmulticast;

import java.net.Inet4Address;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class GroupSet {
	private Map<Inet4Address,Group> groupMap = new HashMap<Inet4Address,Group>();

	private final VirtualInterface vif;

	GroupSet(VirtualInterface vif) {
		this.vif = vif;
	}

	public Group getGroup(Inet4Address address) {
		Group group = null;

		synchronized (this) {
			group = this.groupMap.get(address);
		}

		return group;	// can return null
	}

	public Group registerGroup(Inet4Address groupAddress) {
		Group group = null;

		// find or create a Group
		synchronized (this) {
			group = this.groupMap.get(groupAddress);

			if (group == null) {
				group = new Group(groupAddress, this.vif);
				this.groupMap.put(groupAddress, group);
			}
		}

		return group;	// does not return null
	}

	public Set<Group> unregisterHost(Inet4Address groupAddress, Inet4Address hostAddress) {
		Group group = null;
		Set<Group> removedGroupSet = new HashSet<Group>();

		synchronized (this) {
			group = this.groupMap.get(groupAddress);

			if (group == null) {
				return removedGroupSet;
			}

			group.unregisterHost(hostAddress);

			if (group.numOfHosts() <= 0) {
				this.groupMap.remove(groupAddress);
				removedGroupSet.add(group);
			}
		}

		return removedGroupSet;
	}

	public Collection<Group> getGroups() {
		synchronized (this) {
			return this.groupMap.values();
		}
	}

	public int getLowestIGMPVersion() {
		int lowestVer = IGMP.DEFAULT_IGMP_VERSION;

		synchronized (this) {
			for (Map.Entry<Inet4Address,Group> entry: this.groupMap.entrySet()) {
				int ver = entry.getValue().getLowestIGMPVersion();
				if (ver < lowestVer) {
					lowestVer = ver;
				}
			}
		}

		return lowestVer;
	}

	/**
	 * Constructs and returns a filter.
	 */
	public Filter getFilter() {
		boolean excludeExists = false;
		boolean includeExists = false;
		Filter filter;

		synchronized (this) {
			for (Group group: this.groupMap.values()) {
				for (Host host: group.getAllHost()) {
					FilterMode filterMode = host.getFilterMode();
					if (filterMode == FilterMode.EXCLUDE) {
						excludeExists = true;
					}
					else {	// INCLUDE
						includeExists = true;
					}
				}
			}

			FilterMode m = ((includeExists && !excludeExists) ? FilterMode.INCLUDE : FilterMode.EXCLUDE);
			filter = new Filter(m);
			Set<Inet4Address> sourceSet = new HashSet<Inet4Address>();

			if (m == FilterMode.EXCLUDE) {
				for (Group group: this.groupMap.values()) {
					for (Host host: group.getAllHost()) {
						if (host.getFilterMode() == FilterMode.EXCLUDE) {
							Set<Inet4Address> s = host.getSourceSet();
							synchronized (s) {
								sourceSet.addAll(s);
							}
						}
					}
				}

				for (Group group: this.groupMap.values()) {
					for (Host host: group.getAllHost()) {
						if (host.getFilterMode() == FilterMode.INCLUDE) {
							Set<Inet4Address> s = host.getSourceSet();
							synchronized (s) {
								sourceSet.removeAll(s);
							}
						}
					}
				}
			}
			else {	// INCLUDE
				for (Group group: this.groupMap.values()) {
					for (Host host: group.getAllHost()) {
						Set<Inet4Address> s = host.getSourceSet();
						synchronized (s) {
							sourceSet.addAll(s);
						}
					}
				}
			}

			filter.setSourceSet(sourceSet);
		}

		return filter;
	}

	public Set<Group> expire(long expiration) {
		Set<Group> removedGroupSet = new HashSet<Group>();
		boolean changed = false;

//System.out.println();
		synchronized (this) {
			for (Inet4Address address: this.groupMap.keySet()) {
				Group group = this.groupMap.get(address);

//System.out.println("group: " + group.getGroupAddress());
				if (group.expire(expiration))
					changed = true;

				if (group.numOfHosts() <= 0) {
					removedGroupSet.add(group);
				}
			}

			for (Group g: removedGroupSet) {
				this.groupMap.remove(g.getGroupAddress());
//System.out.println("removed group: " + g.getGroupAddress());
			}
		}

		return removedGroupSet;
	}

	public String toString(String indent) {
		StringBuilder sb = new StringBuilder();

		sb.append(indent);
		sb.append("groups [");
		for (Group g: this.groupMap.values()) {
			sb.append("\n");
			sb.append(indent);
			sb.append(g.toString("  "));
		}
		sb.append("\n");
		sb.append(indent);
		sb.append("]");

		return sb.toString();
	}
}

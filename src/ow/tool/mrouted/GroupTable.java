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

package ow.tool.mrouted;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ow.id.ID;
import ow.ipmulticast.Group;
import ow.ipmulticast.IPMulticast;

/**
 * A table which manages pairs of an IP multicast group address and an ID.
 */
public final class GroupTable {
	private final int idSizeInByte;
	private final DatagramSocket sockForOverlay;
	private final ApplicationLevelMulticastRouterConfiguration config;
	private final IPMulticast mcast;

	private final Map<ID,Inet4Address> idToAddressTable;
	private final Map<Inet4Address,ID> addressToIDTable;
	private final Map<ID,OverlayTrafficForwarder> idToForwarderTable;
	private Set<Group> joinedGroupSet;

	public GroupTable(ApplicationLevelMulticastRouterConfiguration config,
			int idSizeInByte, DatagramSocket sockForOverlay, IPMulticast mcast) {
		this.config = config;
		this.idSizeInByte = idSizeInByte;
		this.sockForOverlay = sockForOverlay;
		this.mcast = mcast;

		this.addressToIDTable = new HashMap<Inet4Address,ID>();
		this.idToAddressTable = new HashMap<ID,Inet4Address>();
		this.idToForwarderTable = new HashMap<ID,OverlayTrafficForwarder>();
		this.joinedGroupSet = new HashSet<Group>();
	}

	/**
	 * Registers a multicast group.
	 */
	public synchronized ID registerMulticastGroup(Group group) {
		Inet4Address groupAddress  = group.getGroupAddress();
		ID id = ID.getHashcodeBasedID(groupAddress, this.idSizeInByte);

		this.joinedGroupSet.add(group);

		// register to tables
		this.addressToIDTable.put(groupAddress, id);
		this.idToAddressTable.put(id, groupAddress);

		// set the group address to a Forwarder
		synchronized (this.idToForwarderTable) {
			OverlayTrafficForwarder forwarder;

			forwarder = this.registerOverlayTrafficForwarder(id);
			forwarder.setGroupAddress(group.getGroupAddress());
		}

		return id;
	}

	/**
	 * Unregisters a multicast group.
	 */
	public synchronized ID unregisterMulticastGroup(Group group) {
		Inet4Address groupAddress  = group.getGroupAddress();
		ID id = ID.getHashcodeBasedID(groupAddress, this.idSizeInByte);

		this.joinedGroupSet.remove(group);

		// unregister from tables
		this.addressToIDTable.remove(groupAddress);
		this.idToAddressTable.remove(id);

		return id;
	}

	/**
	 * Returns a multicast group by the specified group ID associated with the group.
	 *
	 * @return the group address. null if the group has not been registered.
	 */
	public Inet4Address getMulticastGroupByID(ID id) {
		Inet4Address group;

		synchronized (this) {
			group = this.idToAddressTable.get(id);
		}

		return group;	// can be null
	}

	/**
	 * Returns a {@link OverlayTrafficForwarder Forwarder} associated with the specified group ID.
	 * Instantiates it if it does not exist.
	 *
	 * @param id a group ID.
	 * @return a Forwarder, which is instantiated if it has not been instantiated.
	 */
	public OverlayTrafficForwarder registerOverlayTrafficForwarder(ID id) {
		OverlayTrafficForwarder forwarder = null;

		synchronized (this.idToForwarderTable) {
			forwarder = this.idToForwarderTable.get(id);

			if (forwarder == null) {
				// instantiate and register a new Forwarder
				forwarder = new OverlayTrafficForwarder(this.config, this, this.sockForOverlay, this.mcast);
				this.idToForwarderTable.put(id, forwarder);
			}

			forwarder.reset();

			forwarder.start();
		}

		return forwarder;
	}

	/**
	 * Returns an {@link OverlayTrafficForwarder OverlayTraffixForwarder} associated with the specified group ID.
	 *
	 * @param id a group ID
	 * @return a Forwarder. null if not exist.
	 */
	public OverlayTrafficForwarder getOverlayTrafficForwarder(ID id) {
		synchronized (this.idToForwarderTable) {
			return this.idToForwarderTable.get(id);
		}
	}

	/**
	 * Remove an {@link OverlayTrafficForwarder OverlayTrafficForwarder}, which has been registered.
	 *
	 * @param id a group ID.
	 * @return null if a Forwarder associated with the specified ID has not been registered.
	 */
	public OverlayTrafficForwarder unregisterOverlayTrafficForwarder(ID id) {
		OverlayTrafficForwarder forwarder;

		synchronized (this.idToForwarderTable) {
			forwarder = this.idToForwarderTable.remove(id);

			if (forwarder != null) forwarder.stop();
		}

		return forwarder;
	}

	public Group getJoinedGroup(Inet4Address groupAddress) {
		synchronized (this.joinedGroupSet) {
			for (Group g: this.joinedGroupSet) {
				if (groupAddress.equals(g.getGroupAddress())) {
					return g;
				}
			}
		}

		return null;
	}

	public Group[] getJoinedGroupSet() {
		synchronized (this.joinedGroupSet) {
			int size = this.joinedGroupSet.size();
			if (size > 0) {
				Group[] groups = new Group[size];
				this.joinedGroupSet.toArray(groups);
				return groups;
			}
		}

		return null;
	}
}

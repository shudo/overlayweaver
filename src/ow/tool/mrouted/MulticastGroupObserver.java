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

package ow.tool.mrouted;

import java.io.IOException;
import java.net.Inet4Address;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.ipmulticast.Group;
import ow.ipmulticast.Host;
import ow.ipmulticast.IPMulticast;
import ow.ipmulticast.VirtualInterface;
import ow.ipmulticast.igmpd.GroupChangeCallback;
import ow.ipmulticast.igmpd.IGMPDaemon;
import ow.mcast.Mcast;
import ow.routing.RoutingException;
import ow.util.Timer;

public final class MulticastGroupObserver implements GroupChangeCallback {
	private final static Logger logger = Logger.getLogger("mrouted");

	private ApplicationLevelMulticastRouterConfiguration config;
	private Mcast mcast;
	private GroupTable groupTable;
	private final IGMPDaemon igmpd;
	private final IPMulticast ipmcast;
	private final int idSizeInByte;

	MulticastGroupObserver(ApplicationLevelMulticastRouterConfiguration config,
			Mcast gm, GroupTable groupTable, IGMPDaemon igmpd, IPMulticast mcast, int idSizeInByte) throws IOException {
		this.config = config;
		this.mcast = gm;
		this.groupTable = groupTable;
		this.igmpd = igmpd;
		this.ipmcast = mcast;
		this.idSizeInByte = idSizeInByte;

		// invoke a self membership expiring daemon
		Runnable r = new SelfMembershipExpiringDaemon();
		Thread t = new Thread(r);
		t.setName("SelfMembershipExpiringDaemon");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Implements
	 * {@link GroupChangeCallback#included(Set, VirtualInterface) GroupEventCallback#included()}.
	 */
	public void included(Set<Group> includedGroupSet, VirtualInterface vif) {
		// register to the table & notify the overlay
		for (Group group: includedGroupSet) {
			ID id;

			id = this.groupTable.registerMulticastGroup(group);
			try {
				mcast.joinGroup(id);
			}
			catch (RoutingException e) {
				logger.log(Level.WARNING, "Routing failed.", e);
			}
		}

		// join the groups
		for (Group group: includedGroupSet) {
			Inet4Address addr = group.getGroupAddress();

			if (addr.isMulticastAddress()) {
				this.ipmcast.joinGroup(addr, vif);
			}
		}

		// log groups
		this.logGroups("Multicast groups (possibly increased):");
	}

	/**
	 * Implements
	 * {@link GroupChangeCallback#excluded(Set, VirtualInterface) GroupEventCallback#excluded()}.
	 */
	public void excluded(Set<Group> excludedGroupSet, VirtualInterface vif) {
		// unregister from the table & notify the overlay
		for (Group group: excludedGroupSet) {
			ID id;

			id = this.groupTable.unregisterMulticastGroup(group);
			mcast.leaveGroup(id);
		}

		// leave the groups
		for (Group group: excludedGroupSet) {
			Inet4Address addr = group.getGroupAddress();

			if (addr.isMulticastAddress()) {
				this.ipmcast.leaveGroup(addr, vif);
			}
		}

		// log groups
		this.logGroups("Multicast groups (possibly decreased):");
	}

	private void logGroups(String header) {
		StringBuilder sb = new StringBuilder();
		sb.append(header);

		Group[] groups = this.groupTable.getJoinedGroupSet();
		if (groups == null) {
			sb.append(" NONE");
		}
		else {
			for (Group g: groups) {
				sb.append(" ");
				sb.append(g.getGroupAddress());
			}
		}
	}

	/**
	 * A daemon which evacuates the ALM router itself
	 * from a multicast group with only a host.
	 *
	 * An algorithm should be improved:
	 * now the router leaves a group if no traffic observed for a specific term,
	 * but there may be other processes (on the same computer) in the group.
	 */
	private final class SelfMembershipExpiringDaemon implements Runnable {
		public void run() {
			while (true) {
				try {
					Thread.sleep(config.getSelfMembershipExpCheckInterval());
				}
				catch (InterruptedException e) {
					logger.log(Level.INFO, "SelfMembershipExpiringDaemon interrupted.", e);
					break;
				}

				Group[] groups = groupTable.getJoinedGroupSet();
				for (Group group: groups) {
					if (group.numOfHosts() != 1) continue;

					Inet4Address groupAddress = group.getGroupAddress();
					long groupMembershipInterval = igmpd.getGroupMembershipInterval();

					for (Host h: group.getAllHost()) {	// h is the only host in group g
						if (!h.isSelf()) continue;
						// h is this node itself

						OverlayTrafficForwarder forwarder =
							groupTable.getOverlayTrafficForwarder(ID.getHashcodeBasedID(group.getGroupAddress(), idSizeInByte));
						if (forwarder == null) continue;

						long lastReceived = forwarder.getLoopbackMessageReceivedTime();
						long now = Timer.currentTimeMillis();

						if (now > lastReceived + groupMembershipInterval) {
							// expire

							// leave a group on an overlay
							ID id = groupTable.unregisterMulticastGroup(group);
							mcast.leaveGroup(id);

							// leave a multicast group
							ipmcast.leaveGroup(groupAddress, group.getVirtualInterface());
						}
					}
				}
			}
		}
	}
}

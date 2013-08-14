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

package ow.tool.mrouted;

import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.mcast.SpanningTreeChangedCallback;
import ow.messaging.MessagingAddress;

final class OverlayGroupObserver implements SpanningTreeChangedCallback {
	private static Logger logger = Logger.getLogger("mrouted");

	private final ApplicationLevelMulticastRouterConfiguration config;
	private final GroupTable groupTable;

	OverlayGroupObserver(ApplicationLevelMulticastRouterConfiguration config, GroupTable groupTable) {
		this.config = config;
		this.groupTable = groupTable;
	}

	/**
	 * Implements
	 * {@link SpanningTreeChangedCallback#topologyChanged(ID, IDAddressPair, IDAddressPair[])
	 * SpanningTreeChangedCallback#topology()}.
	 */
	public void topologyChanged(ID groupID, IDAddressPair parent, IDAddressPair[] children) {
		OverlayTrafficForwarder forwarder;
/*
System.out.println("topology changed:");
System.out.println("  group: " + groupID);
if (parent != null) {
	if (parent != null) {
		System.out.println("  parent: " + parent);
	}
}
if (children != null) {
	System.out.print("  children:");
	for (int i = 0; i < children.length; i++) {
		System.out.print(" ");
		System.out.print(children[i]);
	}
	System.out.println();
}
*/
		if (parent == null
				&& (children == null || children.length <= 0)) {
			// unregister the Forwarder
			forwarder = this.groupTable.unregisterOverlayTrafficForwarder(groupID);
		}
		else {
			// make ForwarderAddress instances based on the specified IDAddressPair instances
			MessagingAddress msgAddr;
			ForwarderAddress parentAddr = null;
			ForwarderAddress[] childrenAddr = null;

			if (parent != null) {
				msgAddr = parent.getAddress();
				try {
					parentAddr = new ForwarderAddress(
							msgAddr.getHostAddress(),
							msgAddr.getPort() + config.getPortDiffFromMcast());
				}
				catch (UnknownHostException e) {
					// NOTREACHED
					logger.log(Level.WARNING, "Could not resolve: " + msgAddr.getHostAddress(), e);
				}
			}

			if (children != null) {
				childrenAddr = new ForwarderAddress[children.length];

				for (int i = 0; i < children.length; i++) {
					msgAddr = children[i].getAddress();
					try {
						childrenAddr[i] = new ForwarderAddress(
								msgAddr.getHostAddress(),
								msgAddr.getPort() + config.getPortDiffFromMcast());
					}
					catch (UnknownHostException e) {
						// NOTREACHED
						logger.log(Level.WARNING, "Could not resolve: " + msgAddr.getHostAddress(), e);
					}
				}
			}

			// register a Forwarder
			forwarder = this.groupTable.registerOverlayTrafficForwarder(groupID);

			forwarder.setParent(parentAddr);
			forwarder.setChildren(childrenAddr);
		}
	}
}

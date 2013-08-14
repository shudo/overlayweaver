/*
 * Copyright 2006,2008-2009 National Institute of Advanced Industrial Science
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

package ow.mcast;

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.routing.RoutingException;
import ow.util.HighLevelService;

/**
 * An application-level multicast (ALM) interface.
 */
public interface Mcast extends HighLevelService {
	//
	// Multicast management
	//

	/**
	 * Clears multicast-related states, e.g. multicast topology.
	 */
	void clearMcastState();

	//
	// Multicast-related operations
	//

	/**
	 * Joins a group specified by the parameter groupID.
	 */
	void joinGroup(ID groupID)
		throws RoutingException;

	/**
	 * Leaves from a group specified by the parameter groupID.
	 *
	 * @return false if this node has not joined the specified group. 
	 */
	boolean leaveGroup(ID groupID);

	/**
	 * Leaves from all groups this node has joined.
	 */
	void leaveAllGroups();

	/**
	 * Registers a callback to be notified of a multicast topology changed.
	 */
	void addSpanningTreeChangedCallback(SpanningTreeChangedCallback callback);

	/**
	 * Registers a callback to receive a message sent with {@link #multicast(ID, Serializable) multicast()}.
	 */
	void addMulticastCallback(McastCallback callback);

	/**
	 * Sends a message to a group.
	 */
	void multicast(ID groupID, Serializable content)
		throws RoutingException;

	//
	// Utilities
	//

	// specific to Mcast
	/**
	 * Return groups which this node belongs to.
	 */
	ID[] getJoinedGroups();
	ID[] getGroupsWithSpanningTree();
	IDAddressPair getParent(ID groupID);
	IDAddressPair[] getChildren(ID groupID);

	// common to DHT and Mcast
	McastConfiguration getConfiguration();
}

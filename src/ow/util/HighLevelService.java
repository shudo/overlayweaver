/*
 * Copyright 2009 Kazuyuki Shudo, and contributors.
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

package ow.util;

import java.net.UnknownHostException;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.MessagingAddress;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingException;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;

/**
 * A super interface common to {@link ow.dht.DHT DHT} and {@link ow.mcast.Mcast Mcast}.
 */
public interface HighLevelService {
	//
	// Overlay management 
	//

	/**
	 * Joins a routing network by contacting the specified host.
	 * An application program has to call this method explicitly.
	 *
	 * @return MessagingAddress of the contact.
	 */
	MessagingAddress joinOverlay(String hostAndPort, int defaultPort)
		throws UnknownHostException, RoutingException;

	/**
	 * Joins a routing network by contacting the specified host.
	 * An application program has to call this method explicitly.
	 *
	 * @return MessagingAddress of the contact.
	 */
	MessagingAddress joinOverlay(String hostAndPort)
		throws UnknownHostException, RoutingException;

	/**
	 * Leaves the routing network by clearing the routing table.
	 */
	void clearRoutingTable();

	//
	// Node management
	//

	void stop();
	void suspend();
	void resume();

	//
	// Utilities
	//

	// common to DHT and Mcast
	HighLevelServiceConfiguration getConfiguration();
	RoutingService getRoutingService();
	RoutingAlgorithmConfiguration getRoutingAlgorithmConfiguration();
	IDAddressPair getSelfIDAddressPair();
	void setStatCollectorAddress(String host, int port) throws UnknownHostException;

	// for debug and tools
	ID[] getLastKeys();
	RoutingResult[] getLastRoutingResults();
	String getRoutingTableString(int verboseLevel);
}

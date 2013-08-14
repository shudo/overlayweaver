/*
 * Copyright 2006,2008,2011 National Institute of Advanced Industrial Science
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

package ow.routing;

import java.io.IOException;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageSender;

/**
 * An interface through which a routing algorithm (like Kademlia) works on a routing runtime,
 * which provides facilities like communication.
 */
public interface RoutingRuntime {
	//
	// Messaging
	//
	MessageSender getMessageSender();
	boolean ping(MessageSender sender, IDAddressPair target) throws IOException;

	void addMessageHandler(Class<? extends Message> messageClass, MessageHandler handler);

	//
	// Routing
	//
	RoutingResult route(ID target, int numNeighbors)
			throws RoutingException;

	RoutingResult route(ID target, RoutingContext initialRoutingContext, int numNeighbors)
			throws RoutingException;

	RoutingResult[] route(ID[] targets, int numResponsibleNodeCandidates);

	RoutingResult[] route(ID[] targets, RoutingContext[] initialRoutingContexts, int numResponsibleNodeCandidates);

	/**
	 * Return the ID and address pair of this node.
	 */
	IDAddressPair getSelfIDAddressPair();

	/**
	 * Returns the configuration on which this routing service is based.
	 */
	RoutingServiceConfiguration getConfiguration();
}

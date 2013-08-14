/*
 * Copyright 2006,2008-2011 National Institute of Advanced Industrial Science
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

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.stat.MessagingReporter;

/**
 * An interface through which an application (e.g. DHT) works on routing facilities.
 */
public interface RoutingService {
	/**
	 * Joins an overlay.
	 *
	 * @param initialContact the node which this call contacts initially
	 * @return a route and neighbors to myself. null if joining failed.
	 */
	RoutingResult join(MessagingAddress initialContact)
			throws RoutingException;

	/**
	 * Leaves the overlay.
	 * An instance of the routing algorithm is reset.
	 */
	void leave();

	/**
	 * Returns a route to the responsible node for the target.
	 *
	 * @param target target ID of routing
	 * @param numResponsibleNodeCandidates number of candidates for the responsible node to be returned
	 * @return a route to / neighbors of the responsible node. The route includes the starting node itself.
	 */
	RoutingResult route(ID target, int numResponsibleNodeCandidates)
			throws RoutingException;

	RoutingResult route(ID target, RoutingContext initialRoutingContext, int numNeighbors)
			throws RoutingException;

	/*
	 * Performs multiple routing for the multiple targets,
	 * and returns routes to the responsible nodes for the targets.
	 *
	 * Note that an element of the returned array is null if routing to a target corresponding the array element failed.
	 */
	RoutingResult[] route(ID[] target, int numResponsibleNodeCandidates);

	RoutingResult[] route(ID[] targets, RoutingContext[] initialRoutingContexts, int numResponsibleNodeCandidates);

	/**
	 * Invokes callback method on nodes on the route for the specified target.
	 * On each node on the route callback are invoked with the specified arguments.
	 * This method returns a result of a callback on the responsible node. 
	 *
	 * @param target target ID of routing
	 * @param numResponsibleNodeCandidates number of candidates for the responsible node which this call request
	 * @param returnedValueContainer
	 * 		a value returned from a callback is stored at the head ([0]) of this array.
	 * 		null if no value found.
	 * @param filter a filter which judges a value returned from a callback is passed back to the caller.
	 * @param tag tag passed to callback
	 * @param args arguments passed to callback
	 * @return a route to / neighbors of the responsible node. it includes this starting node itself.
	 */
	RoutingResult invokeCallbacksOnRoute(ID target, int numResponsibleNodeCandidates,
			Serializable[] returnedValueContainer,
			int tag, Serializable[] args)
				throws RoutingException;

	/**
	 * Performs multiple routing for the multiple targets,
	 * and invokes callback method on nodes on the routes for the specified target.
	 *
	 * Note that an element of the returned array is null if routing to a target corresponding the array element failed.
	 */
	RoutingResult[] invokeCallbacksOnRoute(ID[] target, int numResponsibleNodeCandidates,
			Serializable[][] returnedValueContainer,
			int tag, Serializable[][] args);

	/**
	 * Registers a callback, which can be invoked on every node along the resolved route.
	 */
	void addCallbackOnRoute(CallbackOnRoute callback);

	/**
	 * Registers a callback, which is invoked in case of neighbor node failure.
	 */
	void addCallbackOnNodeFailure(CallbackOnNodeFailure callback);

	/**
	 * Registers a MessageHandler associated with the specified tag.
	 */
	void addMessageHandler(Class<? extends Message> messageClass, MessageHandler handler);

	//
	// Management
	//

	/**
	 * Stops the service.
	 */
	void stop();
	void suspend();
	void resume();

	//
	// Utility methods
	//

	/**
	 * Returns the IDAddressPair of this service itself.
	 */
	IDAddressPair getSelfIDAddressPair();

	/**
	 * Returns the MessagingProvider which this routing service uses.
	 */
	MessagingProvider getMessagingProvider();

	/**
	 * Returns the MessageSender which this routing service uses.
	 */
	MessageReceiver getMessageReceiver();

	/**
	 * Returns a MessageSender, which is different from one this routing service uses.
	 */
	MessageSender getMessageSender();

	/**
	 * Returns the MessagingReporter which this routing service uses.
	 */
	MessagingReporter getMessagingReporter();

	/**
	 * Returns the RoutingAlgorithm which this routing service uses.
	 */
	RoutingAlgorithm getRoutingAlgorithm();

	/**
	 * Sets the address of a statistics collector to which communication status is reported.
	 */
	void setStatCollectorAddress(MessagingAddress address);

	/**
	 * Returns a String representation of a route.
	 */
	String routeToString(RoutingHop[] route);
}

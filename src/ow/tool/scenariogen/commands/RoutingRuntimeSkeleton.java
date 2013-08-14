/*
 * Copyright 2009-2011 Kazuyuki Shudo, and contributors.
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

package ow.tool.scenariogen.commands;

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.routing.CallbackOnNodeFailure;
import ow.routing.CallbackOnRoute;
import ow.routing.RoutingAlgorithm;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingRuntime;
import ow.routing.RoutingService;
import ow.routing.RoutingServiceConfiguration;
import ow.stat.MessagingReporter;

/**
 * A dummy RoutingService / RoutingRuntime class just to instantiate a RoutingAlgorithm.
 */
class RoutingRuntimeSkeleton implements RoutingService, RoutingRuntime {
	private final IDAddressPair selfIDAddress;

	RoutingRuntimeSkeleton(RoutingAlgorithmConfiguration algoConf) {
		this.selfIDAddress = IDAddressPair.getIDAddressPair(
				algoConf.getIDSizeInByte(), new MessagingAddressSkeleton());
	}

	//
	// for RoutingRuntime
	//
	public void addMessageHandler(Class<? extends Message> messageClass, MessageHandler handler) {}
	public RoutingServiceConfiguration getConfiguration() { return null; }
	public MessageReceiver getMessageReceiver() { return null; }
	public MessageSender getMessageSender() { return null; }
	public IDAddressPair getSelfIDAddressPair() { return this.selfIDAddress; }
	public boolean ping(MessageSender sender, IDAddressPair target) { return true; }
	public RoutingResult route(ID target, int numNeighbors) throws RoutingException { return null; }
	public RoutingResult route(ID target, RoutingContext initialContext, int numNeighbors) throws RoutingException { return null; }
	public RoutingResult[] route(ID[] targets, int numResponsibleNodeCandidates) { return null; }
	public RoutingResult[] route(ID[] targets, RoutingContext[] initialContexts, int numResponsibleNodeCandidates) { return null; }

	//
	// only for RoutingService
	// 
	public void addCallbackOnNodeFailure(CallbackOnNodeFailure callback) {}
	public void addCallbackOnRoute(CallbackOnRoute callback) {}
	public MessagingProvider getMessagingProvider() { return null; }
	public MessagingReporter getMessagingReporter() { return null; }
	public RoutingAlgorithm getRoutingAlgorithm() { return null; }
	public RoutingResult invokeCallbacksOnRoute(ID target,
			int numResponsibleNodeCandidates, Serializable[] returnedValueContainer,
			int tag, Serializable[] args)
			throws RoutingException { return null; }
	public RoutingResult[] invokeCallbacksOnRoute(ID[] target,
			int numResponsibleNodeCandidates, Serializable[][] returnedValueContainer,
			int tag, Serializable[][] args) { return null; }
	public RoutingResult join(MessagingAddress initialContact)
			throws RoutingException { return null; }
	public void leave() {}
	public void resume() {}
	public String routeToString(RoutingHop[] route) { return null; }
	public void setStatCollectorAddress(MessagingAddress address) {}
	public void stop() {}
	public void suspend() {}

	private class MessagingAddressSkeleton implements MessagingAddress {
		public String getHostAddress() { return null; }
		public String getHostname() { return null; }
		public String getHostnameOrHostAddress() { return null; }
		public int getPort() { return 0; }
		public MessagingAddress getMessagingAddress() { return null; }
		public void copyFrom(MessagingAddress newAddress) {}
		public String toString(int verboseLevel) { return null; }
	}
}

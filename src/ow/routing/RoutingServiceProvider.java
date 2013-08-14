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

package ow.routing;

import java.io.IOException;

import ow.id.ID;
import ow.messaging.MessagingConfiguration;
import ow.messaging.MessagingProvider;

public interface RoutingServiceProvider {
	/**
	 * Return the name of an algorithm that this provider supports. 
	 */
	String getName();

	/**
	 * Return the default configuration for a routing service.
	 */
	RoutingServiceConfiguration getDefaultConfiguration();

	/**
	 * Return a routing service.
	 *
	 * @param config a configuration which getConfiguration() returned.
	 * @param selfID ID of this node, which cannot be null.
	 */
	RoutingService getService(
			RoutingServiceConfiguration config,
			MessagingProvider provider, MessagingConfiguration msgConfig, int port, int portRange,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConfig,
			ID selfID)
				throws IOException;

	/**
	 * Return a routing service.
	 * The ID of this node is determined based on the address of this node itself
	 *
	 * @param config a configuration which getConfiguration() returned.
	 * @param algoConfig the length of an ID is derived by
	 * {@link RoutingAlgorithmConfiguration#getIDSizeInByte() getIDSizeInByte()} method of this instance.
	 */
	RoutingService getService(
			RoutingServiceConfiguration config,
			MessagingProvider provider, MessagingConfiguration msgConfig, int port, int portRange,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConfig)
				throws IOException;
}

/*
 * Copyright 2006,2009,2011-2012 National Institute of Advanced Industrial Science
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

package ow.routing.impl;

import java.io.IOException;

import ow.id.ID;
import ow.messaging.MessagingConfiguration;
import ow.messaging.MessagingProvider;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingAlgorithmProvider;
import ow.routing.RoutingService;
import ow.routing.RoutingServiceConfiguration;
import ow.routing.RoutingServiceProvider;

public class IterativeRoutingDriverProvider implements RoutingServiceProvider {
	private final static String RUNTIME_NAME = "Iterative";

	public String getName() {
		return RUNTIME_NAME;
	}

	public RoutingServiceConfiguration getDefaultConfiguration() {
		return new RoutingServiceConfiguration();
	}

	public RoutingService getService(
			RoutingServiceConfiguration config,
			MessagingProvider msgProvider, MessagingConfiguration msgConfig, int port, int portRange,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConfig,
			ID selfID)
				throws IOException {
		return new IterativeRoutingDriver(config, msgProvider, msgConfig, port, portRange,
				algoProvider, algoConfig, selfID);
	}

	public RoutingService getService(
			RoutingServiceConfiguration config,
			MessagingProvider msgProvider, MessagingConfiguration msgConfig, int port, int portRange,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConfig)
				throws IOException {
		return new IterativeRoutingDriver(config, msgProvider, msgConfig, port, portRange,
				algoProvider, algoConfig, null);
	}
}

/*
 * Copyright 2006 Kazuyuki Shudo, and contributors.
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

package ow.routing.koorde;

import java.security.InvalidAlgorithmParameterException;

import ow.routing.RoutingAlgorithm;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingAlgorithmProvider;
import ow.routing.RoutingService;

public class KoordeProvider implements RoutingAlgorithmProvider {
	private final static String ALGORITHM_NAME = "Koorde";

	public String getName() {
		return ALGORITHM_NAME;
	}

	public RoutingAlgorithmConfiguration getDefaultConfiguration() {
		return new KoordeConfiguration();
	}

	public RoutingAlgorithm initializeAlgorithmInstance(
			RoutingAlgorithmConfiguration conf, RoutingService routingSvc)
				throws InvalidAlgorithmParameterException {
		return new Koorde(conf, routingSvc);
	}
}

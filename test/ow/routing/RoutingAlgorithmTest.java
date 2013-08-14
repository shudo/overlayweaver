/*
 * Copyright 2006 National Institute of Advanced Industrial Science
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

import java.math.BigInteger;

import ow.id.ID;

public class RoutingAlgorithmTest {
	public final static String ALGORITHM = "Pastry";

	public final static void main(String[] args) throws Exception {
		RoutingAlgorithmProvider provider = RoutingAlgorithmFactory.getProvider(ALGORITHM);
		RoutingAlgorithmConfiguration conf = provider.getDefaultConfiguration();
		RoutingAlgorithm algo = provider.initializeAlgorithmInstance(conf, null);

		ID a = ID.getID("01020304050607080910111213141516", 16);
		ID b = ID.getID("01020304050607080910111213141517", 16);
		ID c = ID.getID("00000000000000000000000000000000", 16);

		BigInteger distance = algo.distance(a, b);

		System.out.println("a: " + a);
		System.out.println("b: " + b);
		System.out.println("   " + distance.toString(16));
	}
}

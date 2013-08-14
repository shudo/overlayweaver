/*
 * Copyright 2006,2012 National Institute of Advanced Industrial Science
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

package ow.routing.kademlia;

import ow.routing.RoutingAlgorithmConfiguration;

/**
 * A class holding configuration for Kademlia.
 */
public final class KademliaConfiguration extends RoutingAlgorithmConfiguration {
	public final static int DEFAULT_K_BUCKET_LENGTH = 20;

	protected KademliaConfiguration() {}

	/**
	 * Whether if a routing driver asks to all contacts it is maintaining.
	 */
	public boolean queryToAllContacts() { return true; }

	/**
	 * Whether if a joining node is inserted into routing tables of existing nodes.
	 */
	public boolean insertJoiningNodeIntoRoutingTables() { return true; }

	private int kBucketLength = DEFAULT_K_BUCKET_LENGTH;
	/**
	 * Length of k-bucket.
	 * Default value is 20.
	 */
	public int getKBucketLength() { return this.kBucketLength; }
	public int setKBucketLength(int len) {
		int old = this.kBucketLength;
		this.kBucketLength = len;
		return old;
	}
}

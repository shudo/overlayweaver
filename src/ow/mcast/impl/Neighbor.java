/*
 * Copyright 2006,2009 National Institute of Advanced Industrial Science
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

package ow.mcast.impl;

import ow.id.IDAddressPair;
import ow.util.Timer;

/**
 * Neighbor represents a parent or a child on a spanning tree.
 */
class Neighbor {
	private IDAddressPair idAddress;
	private long updatedTime;

	Neighbor(IDAddressPair idAddress) {
		this.idAddress = idAddress;
		this.updatedTime = Timer.currentTimeMillis();
	}

	IDAddressPair getIDAddressPair() { return this.idAddress; }

	long getUpdatedTime() { return this.updatedTime; }

	// The following methods override corresponding methods of Object.

	public int hashCode() {
		return this.idAddress.hashCode();
	}

	public boolean equals(Object o) {	// check only the member address
		if (o instanceof Neighbor) {
			if (this.idAddress.equals(((Neighbor)o).getIDAddressPair()))
				return true;
		}
		return false;
	}
}

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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import ow.id.ID;

public class GroupSet {
	private final Set<ID> groups =
		Collections.synchronizedSet(new HashSet<ID>());

	public boolean contains(ID groupID) {
		return this.groups.contains(groupID);
	}

	public void add(ID groupID) {
		this.groups.add(groupID);
	}

	public boolean remove(ID groupID) {
		return this.groups.remove(groupID);
	}

	public void clear() {
		this.groups.clear();
	}

	public int size() {
		return this.groups.size();
	}

	public ID[] toArray() {
		ID[] ret = null;

		int n = this.groups.size();
		if (n > 0) {
			ret = new ID[n];
			this.groups.toArray(ret);
		}

		return ret;
	}
}

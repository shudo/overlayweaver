/*
 * Copyright 2006,2008 National Institute of Advanced Industrial Science
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

import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedTowardTargetIDAddrComparator;
import ow.routing.RoutingAlgorithm;

public final class SortedContactList extends AbstractContactList {
	private SortedSet<IDAddressPair> nodeList;

	public SortedContactList(ID target, RoutingAlgorithm algorithm) {
		this(target, algorithm, -1);
	}

	public SortedContactList(ID target, RoutingAlgorithm algorithm, int maxNodes) {
		super(maxNodes);

		this.nodeList = new TreeSet<IDAddressPair>(
				new AlgoBasedTowardTargetIDAddrComparator(algorithm, target));
	}

	public synchronized int size() {
		return this.nodeList.size();
	}

	public void clear() {
		this.nodeList.clear();
	}

	public synchronized boolean add(IDAddressPair contact) {
		IDAddressPair lastFirstContact = this.first(false);

		this.nodeList.add(contact);

		if (maxNodes > 0) {
			while (this.nodeList.size() > maxNodes) {
				// trim
				IDAddressPair p = this.nodeList.last();
				this.nodeList.remove(p);
			}
		}

		return !this.first(false).equals(lastFirstContact);
	}

	public synchronized boolean remove(IDAddressPair contact) {
		return this.nodeList.remove(contact);
	}

	protected synchronized IDAddressPair first(boolean registerToContactedSet) {
		IDAddressPair ret = null;
		try {
			ret = this.nodeList.first();

			if (registerToContactedSet)
				this.contactedSet.add(ret);
		}
		catch (NoSuchElementException e) {
			// do nothing
		}

		return ret;
	}

	protected synchronized IDAddressPair firstExceptContactedNode(boolean registerToContactedSet) {
		for (IDAddressPair p: this.nodeList) {
			if (!this.contactedSet.contains(p)) {
				if (registerToContactedSet)
					this.contactedSet.add(p);

				return p;
			}
		}

		return null;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("{nodes:");
		for (IDAddressPair p: this.nodeList) {
			sb.append(" ").append(p.getAddress());
		}

		sb.append(", contacted:");
		for (IDAddressPair p: this.contactedSet) {
			sb.append(" ").append(p.getAddress());
		}

		sb.append("}");

		return sb.toString();
	}
}

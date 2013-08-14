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

import java.util.HashSet;
import java.util.Set;

import ow.id.IDAddressPair;

public abstract class AbstractContactList implements ContactList {
	protected int maxNodes;
	protected Set<IDAddressPair> contactedSet;

	public AbstractContactList(int maxNodes) {
		this.maxNodes = maxNodes;
		this.contactedSet = new HashSet<IDAddressPair>();
	}

	protected abstract IDAddressPair first(boolean registerToContactedSet);
	protected abstract IDAddressPair firstExceptContactedNode(boolean registerToContactedSet);

	public IDAddressPair first() { return this.first(true); }
	public IDAddressPair inspect() { return this.first(false); }
	public IDAddressPair firstExceptContactedNode() { return this.firstExceptContactedNode(true); }
	public IDAddressPair inspectExceptContactedNode() { return this.firstExceptContactedNode(false); }

	public synchronized int numOfContactedNodes() {
		return this.contactedSet.size();
	}

	public synchronized boolean addAsContacted(IDAddressPair contact) {
		boolean ret = this.add(contact);
		this.contactedSet.add(contact);
		return ret;
	}

	public synchronized boolean isContactedNode(IDAddressPair node) {
		return this.contactedSet.contains(node);
	}
}

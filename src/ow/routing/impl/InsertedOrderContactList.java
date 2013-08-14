/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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

import java.util.LinkedList;
import java.util.NoSuchElementException;

import ow.id.IDAddressPair;

public final class InsertedOrderContactList extends AbstractContactList {
//	private List<IDAddressPair> nodeList;	// for List
	private LinkedList<IDAddressPair> nodeList;	// for LinkedList

	public InsertedOrderContactList() {
		this(-1);
	}

	public InsertedOrderContactList(int maxNodes) {
		super(maxNodes);

//		this.nodeList = new ArrayList<IDAddressPair>();	// for ArrayList
		this.nodeList = new LinkedList<IDAddressPair>();	// for LinkedList
	}

	public synchronized int size() {
		return this.nodeList.size();
	}

	public void clear() {
		this.nodeList.clear();
	}

	public synchronized boolean add(IDAddressPair contact) {
		IDAddressPair lastFirstContact = this.first(false);

		if (!this.nodeList.contains(contact)
				&& (maxNodes <= 0 || this.nodeList.size() < maxNodes)) {
			this.nodeList.add(contact);
		}

		return !this.first(false).equals(lastFirstContact);
	}

	public synchronized boolean remove(IDAddressPair contact) {
		return this.nodeList.remove(contact);
	}

	protected synchronized IDAddressPair first(boolean registerToContactedSet) {
		IDAddressPair ret = null;
		try {
//			ret = this.nodeList.get(0);		// for List
			ret = this.nodeList.getFirst();	// for LinkedList

			if (registerToContactedSet)
				this.contactedSet.add(ret);
		}
//		catch (IndexOutOfBoundsException e) {	// for List
		catch (NoSuchElementException e) {		// for LinkedList
			// do nothing
		}

		return ret;
	}

	protected synchronized IDAddressPair firstExceptContactedNode(boolean registerToContectedSet) {
		for (IDAddressPair p: this.nodeList) {
			if (!this.contactedSet.contains(p)) {
				if (registerToContectedSet)
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

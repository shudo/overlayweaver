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

package ow.routing.kademlia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import ow.id.IDAddressPair;
import ow.routing.RoutingAlgorithm;

/**
 * k-bucket for Kademlia.
 * Nearer to the tail is fresh and the head is the least-recently seen node.
 */
final class KBucket implements Iterable<IDAddressPair> {
	private final Kademlia algorithm;
	private final List<IDAddressPair> internalList = new ArrayList<IDAddressPair>();

	KBucket(RoutingAlgorithm algorithm) {
		this.algorithm = (Kademlia)algorithm;
	}

	synchronized void clear() {
		this.internalList.clear();
	}

	public synchronized int size() {
		return this.internalList.size();
	}

	/**
	 * Removes an entry from the k-bucket.
	 *
	 * @return true if this k-bucket contained the specified element.
	 */
	public synchronized boolean remove(IDAddressPair elem) {
		return this.internalList.remove(elem);
	}

	/**
	 * Returns the least-recently seen node at the head of this k-bucket.
	 */
	private IDAddressPair peekHead() {
		IDAddressPair ret = null;
		try {
			ret = this.internalList.get(0);
		}
		catch (IndexOutOfBoundsException e) {} // list is empty

		return ret;
	}

	private IDAddressPair removeHead() {
		IDAddressPair ret = null;
		try {
			ret = this.internalList.remove(0);
		}
		catch (IndexOutOfBoundsException e) {} // list is empty

		return ret;
	}

	private IDAddressPair nodeBeingChecked = null;

	public void appendToTail(final IDAddressPair newEntry) {
		final IDAddressPair head;

		synchronized (this) {
			// checks if another thread is checking nodes's liveness
			// allows only one thread to check node's liveness
			if (this.nodeBeingChecked != null) {
				return;
			}

			if (this.internalList.remove(newEntry)) {
				this.internalList.add(newEntry);
				return;
			}
			else {
				if (this.internalList.size() < this.algorithm.kBucketLength) {
					this.internalList.add(newEntry);
					return;
				}
				else {
					// this k-bucket is full
					head = this.peekHead();

					this.nodeBeingChecked = head;
				}
			}
		}

		boolean toReplace = algorithm.toReplace(head, newEntry);	// sends a ping message

		synchronized (this) {
			this.nodeBeingChecked = null;

			if (toReplace) {
				// head does not respond
				removeHead();
				appendToTail(newEntry);
			}
			else {
				// head responds
				removeHead();
				appendToTail(head);
			}
		}
	}

	public synchronized Iterator<IDAddressPair> iterator() {
		return this.internalList.iterator();
	}

	public synchronized IDAddressPair[] toArray() {
		IDAddressPair[] ret = new IDAddressPair[this.internalList.size()];
		return this.internalList.toArray(ret);
	}

	public synchronized IDAddressPair[] toSortedArray(Comparator<IDAddressPair> comparator) {
		SortedSet<IDAddressPair> s = new TreeSet<IDAddressPair>(comparator);
		s.addAll(this.internalList);

		IDAddressPair[] ret = new IDAddressPair[s.size()];
		return s.toArray(ret);
	}
}

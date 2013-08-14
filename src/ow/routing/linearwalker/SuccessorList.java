/*
 * Copyright 2006-2010 National Institute of Advanced Industrial Science
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

package ow.routing.linearwalker;

import java.math.BigInteger;
import java.util.SortedSet;
import java.util.TreeSet;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedFromSrcIDAddrPairComparator;
import ow.id.comparator.AlgoBasedTowardTargetIDAddrComparator;

public final class SuccessorList {
	private final IDAddressPair selfIDAddress;
	private final int maxLength;
	private final SortedSet<IDAddressPair> list;	// not being empty
	private final LinearWalker algorithm;

	/**
	 * Constructor.
	 *
	 * @param maxLength infinity if equal to or less than 0
	 */
	public SuccessorList(LinearWalker algo, IDAddressPair selfIDAddress, int maxLength) {
		this.algorithm = algo;
		this.selfIDAddress = selfIDAddress;
		this.maxLength = maxLength;
		this.list = new TreeSet<IDAddressPair>(
				new AlgoBasedFromSrcIDAddrPairComparator(algo, selfIDAddress.getID()));

		// initialize
		this.clear();
	}

	synchronized void clear() {
		this.list.clear();
		this.list.add(selfIDAddress);
	}

	public void add(IDAddressPair elem) {
		if (elem == null) return;

		synchronized (this.list) {
			boolean added = this.list.add(elem);

			if (added && maxLength > 0) {
				while (this.list.size() > maxLength) {
					IDAddressPair lastElem = this.list.last();
					this.list.remove(lastElem);
				}
			}
		}
	}

	public void addAll(IDAddressPair[] elems) {
		if (elems == null) return;

		synchronized (this.list) {
			boolean added = false;

			for (IDAddressPair elem: elems) {
				added |= this.list.add(elem);
			}

			if (added && maxLength > 0) {
				while (this.list.size() > maxLength) {
					IDAddressPair lastElem = this.list.last();
					this.list.remove(lastElem);
				}
			}
		}
	}

	public boolean contains(IDAddressPair elem) {
		if (elem == null) return false;

		return this.list.contains(elem);
	}

	public boolean remove(IDAddressPair elem) {
		boolean ret;

		synchronized (this.list) {
			ret = this.list.remove(elem); 

			if (this.list.isEmpty()) {
				this.list.add(selfIDAddress);
			}
		}

		return ret;
	}

	public IDAddressPair first() {
		synchronized (this.list) {
			return this.list.first();
		}
	}

	public IDAddressPair lastOtherNode() {
		synchronized (this.list) {
			SortedSet<IDAddressPair> headSet = this.list.headSet(selfIDAddress);
			if (headSet.isEmpty())
				return null;
			else
				return headSet.last();
		}
	}

	public IDAddressPair[] toArray() {
		synchronized (this.list) {
			IDAddressPair[] array = new IDAddressPair[this.list.size()];
			return this.list.toArray(array);
		}
	}

	public IDAddressPair[] toArrayExcludingSelf() {
		IDAddressPair[] array;

		synchronized (this.list) {
			int size = this.list.size();
			if (this.list.contains(this.selfIDAddress)) size--;

			array = new IDAddressPair[size];
			int i = 0;
			for (IDAddressPair p: this.list)
				if (!this.selfIDAddress.equals(p)) array[i++] = p;
		}

		return array;
	}

	IDAddressPair[] responsibleNodeCandidates(ID target, int maxNumber, IDAddressPair predecessor) {
		BigInteger targetMinusOneInt = target.toBigInteger().subtract(BigInteger.ONE);
		if (targetMinusOneInt.compareTo(BigInteger.ZERO) < 0) {
			targetMinusOneInt = targetMinusOneInt.add(this.algorithm.sizeOfIDSpace);
		}
		ID targetMinusOne = ID.getID(targetMinusOneInt, target.getSize());

		SortedSet<IDAddressPair> retSet = new TreeSet<IDAddressPair>(
					new AlgoBasedFromSrcIDAddrPairComparator(this.algorithm, targetMinusOne));

		// add all candidates
		retSet.add(selfIDAddress);
		synchronized (this.list) {
			retSet.addAll(this.list);
		}
		if (predecessor != null) retSet.add(predecessor);

//System.out.println("target - 1         : " + targetMinusOne);
//System.out.println("resp candidates for: " + target);
//for (IDAddressPair p: retSet) {
//	System.out.println(" " + p);
//	System.out.println("    dist: " + algrithm.distance(p.getID(), targetMinusOne).toString(16));
//}
		// convert to an array
		int len = Math.min(maxNumber, retSet.size());
		IDAddressPair[] ret = new IDAddressPair[len];

		int i = 0;
		for (IDAddressPair p: retSet) {
			if (i >= len) break;
			ret[i++] = p;
		}

		return ret;
	}

	public SortedSet<IDAddressPair> closestTo(ID target, boolean includeSelf) {
		SortedSet<IDAddressPair> result = new TreeSet<IDAddressPair>(
				new AlgoBasedTowardTargetIDAddrComparator(algorithm, target));

		synchronized (this.list) {
			result.addAll(this.list);
		}

		if (includeSelf) result.add(this.selfIDAddress);
		else result.remove(this.selfIDAddress);

		return result;
	}
}

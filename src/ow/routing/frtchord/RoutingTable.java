/*
 * Copyright 2010-2011 Kazuyuki Shudo, and contributors.
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

package ow.routing.frtchord;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedFromSrcIDAddrPairComparator;

/**
 * Routing table for FRT-Chord.
 */
final class RoutingTable {
	private final FRTChord algorithm;
	private final IDAddressPair selfIDAddress;
	private final ID selfIDMinusOne;

	private final SortedMap<IDAddressPair,Entry> table;	// does not have the node itself
	private final SortedSet<Entry> removeeCandidates;
	private IDAddressPair lastDroppedIDAddressPair = null;	// optimization

	private final double normalizingConst;

	RoutingTable(FRTChord algorithm, IDAddressPair selfIDAddress) {
		this.algorithm = algorithm;
		this.selfIDAddress = selfIDAddress;

		BigInteger minusOne = selfIDAddress.getID().toBigInteger().subtract(BigInteger.ONE);
		if (minusOne.compareTo(BigInteger.ZERO) < 0)
			minusOne = minusOne.add(algorithm.sizeOfIDSpace);
		this.selfIDMinusOne = ID.getID(minusOne, algorithm.config.getIDSizeInByte());

		this.normalizingConst = Math.log(2.0) * algorithm.idSizeInBit;

		// Prepare tables
		this.table = new TreeMap<IDAddressPair,Entry>(
				new AlgoBasedFromSrcIDAddrPairComparator(algorithm, selfIDAddress.getID()));
		this.removeeCandidates = new TreeSet<Entry>();
	}

	public synchronized void clear() {
		this.table.clear();
		this.removeeCandidates.clear();
	}

	public IDAddressPair insert(IDAddressPair idAddr) {
		if (this.selfIDAddress.equals(idAddr)) return idAddr;

		synchronized (this) {
			if (idAddr.equals(this.lastDroppedIDAddressPair)) return idAddr;	// optimization

			if (this.table.containsKey(idAddr)) return idAddr;

			Entry precedingEntry1 = null;	// pred of pred
			Entry precedingEntry0 = null;	// pred
			Entry succeedingEntry0 = null;	// succ
			Entry succeedingEntry1 = null;	// succ of succ
			{
				SortedMap<IDAddressPair,Entry> subMap;

				subMap = this.table.headMap(idAddr);
				if (!subMap.isEmpty())
					precedingEntry0 = this.table.get(subMap.lastKey());

				if (precedingEntry0 != null) {
					subMap = this.table.headMap(precedingEntry0.getIDAddressPair());
					if (!subMap.isEmpty())
						precedingEntry1 = this.table.get(subMap.lastKey());
				}

				subMap = this.table.tailMap(idAddr);
				if (!subMap.isEmpty()) {
					int i = 0;
					for (IDAddressPair key: subMap.keySet()) {
						if (i == 0) succeedingEntry0 = this.table.get(key);
						else if (i == 1) succeedingEntry1 = this.table.get(key);
						else break;
						i++;
					}
				}
			}

			// Inserts the new entry to the routing table
			Entry newEntry = new Entry(idAddr);
			this.table.put(idAddr, newEntry);

			// Recalculate normalized interval of predecessor of new entry
			if (precedingEntry0 != null) {
				this.removeeCandidates.remove(precedingEntry0);
				precedingEntry0.calculateNormalizedIDInterval(precedingEntry1, newEntry);
				this.removeeCandidates.add(precedingEntry0);
			}

			// Calculate normalized interval of new entry
			newEntry.calculateNormalizedIDInterval(precedingEntry0, succeedingEntry0);
			this.removeeCandidates.add(newEntry);

			// Recalculate normalized interval of successor of new entry
			if (succeedingEntry0 != null) {
				this.removeeCandidates.remove(succeedingEntry0);
				succeedingEntry0.calculateNormalizedIDInterval(newEntry, succeedingEntry1);
				this.removeeCandidates.add(succeedingEntry0);
			}

//System.out.println("removeeCandidates " + this.selfIDAddress.toString(-1) + "{");
//for (Entry e: this.removeeCandidates) {
//				System.out.println(e.getIDAddressPair().toString(-1) + " " + e.normalizedIDInterval);
//}
//System.out.println("}");

			// Routing table overflows ?
			if (this.table.size() > this.algorithm.config.getRoutingTableSize()) {
				Entry removee = null;

				SortedSet<IDAddressPair> keySortedSet =
					(SortedSet<IDAddressPair>)this.table.keySet();

				stickyTest:
				for (Entry removeeCand: this.removeeCandidates) {
					int i = this.algorithm.config.getNumStickyNodes();
					for (IDAddressPair p: keySortedSet) {
						if (i-- <= 0) break;
						if (removeeCand.getIDAddressPair().equals(p)) {
							continue stickyTest;
						}
					}
					if (removeeCand.getIDAddressPair().equals(keySortedSet.last())) {
						continue stickyTest;
					}

					removee = removeeCand;
					break;
				}

				if (removee != null) {
					IDAddressPair removed = removee.getIDAddressPair();
					this.remove(removed);
					this.lastDroppedIDAddressPair = removed;	// optimization

					return removed;
				}
			}
		}	//synchronized (this)

		return null;
	}

	public synchronized void insertAll(IDAddressPair[] idAddrs) {
		for (IDAddressPair idAddr: idAddrs) {
			this.insert(idAddr);
		}
	}

	public IDAddressPair remove(IDAddressPair idAddr) {
		Entry removed = null;

		synchronized (this) {
			removed = this.table.remove(idAddr);

			if (removed != null) {
				this.removeeCandidates.remove(removed);
				
				Entry precedingEntry1 = null;	// pred of pred
				Entry precedingEntry0 = null;	// pred
				Entry succeedingEntry0 = null;	// succ
				Entry succeedingEntry1 = null;	// succ of succ
				{
					SortedMap<IDAddressPair,Entry> subMap;

					subMap = this.table.headMap(idAddr);
					if (!subMap.isEmpty())
						precedingEntry0 = this.table.get(subMap.lastKey());

					if (precedingEntry0 != null) {
						subMap = this.table.headMap(precedingEntry0.getIDAddressPair());
						if (!subMap.isEmpty())
							precedingEntry1 = this.table.get(subMap.lastKey());
					}

					subMap = this.table.tailMap(idAddr);
					if (!subMap.isEmpty()) {
						int i = 0;
						for (IDAddressPair key: subMap.keySet()) {
							if (i == 0) succeedingEntry0 = this.table.get(key);
							else if (i == 1) succeedingEntry1 = this.table.get(key);
							else break;
							i++;
						}
					}
				}

				// Recalculate normalized interval of predecessor of removed entry
				if (precedingEntry0 != null) {
					this.removeeCandidates.remove(precedingEntry0);
					precedingEntry0.calculateNormalizedIDInterval(precedingEntry1, succeedingEntry0);
					this.removeeCandidates.add(precedingEntry0);
				}

				// Recalculate normalized interval of successor of removed entry
				if (succeedingEntry0 != null) {
					this.removeeCandidates.remove(succeedingEntry0);
					succeedingEntry0.calculateNormalizedIDInterval(precedingEntry0, succeedingEntry1);
					this.removeeCandidates.add(succeedingEntry0);
				}

				this.lastDroppedIDAddressPair = null;	// optimization
			}
		}

		if (removed != null)
			return removed.getIDAddressPair();
		else
			return null;
	}

	public synchronized IDAddressPair[] toArray() {
		return this.table.keySet().toArray(new IDAddressPair[0]);
	}

	protected synchronized Entry[] toEntryArray() {
		return this.table.values().toArray(new Entry[0]);
	}

	IDAddressPair[] responsibleNodeCandidates(ID target, int maxNumber) {
		BigInteger targetMinusOneInt = target.toBigInteger().subtract(BigInteger.ONE);
		if (targetMinusOneInt.compareTo(BigInteger.ZERO) < 0) {
			targetMinusOneInt = targetMinusOneInt.add(this.algorithm.sizeOfIDSpace);
		}
		ID targetMinusOne = ID.getID(targetMinusOneInt, target.getSize());

		SortedSet<IDAddressPair> retSet = new TreeSet<IDAddressPair>(
					new AlgoBasedFromSrcIDAddrPairComparator(this.algorithm, targetMinusOne));

		// add all candidates
		retSet.add(selfIDAddress);
		synchronized (this) {
			retSet.addAll(this.table.keySet());
		}

//System.out.println("target - 1         : " + targetMinusOne);
//System.out.println("resp candidates for: " + target);
//for (IDAddressPair p: retSet) {
//	System.out.println(" " + p);
//	System.out.println("    dist: " + algorithm.distance(p.getID(), targetMinusOne).toString(16));
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

	IDAddressPair[] closestTo(ID target, boolean joining, boolean passOverTarget) {
		List<IDAddressPair> result = new ArrayList<IDAddressPair>();

		SortedMap<IDAddressPair,Entry> smallerHalf, largerHalf;

		IDAddressPair targetIDAddr = IDAddressPair.getIDAddressPair(target, null);
		synchronized (this) {
			smallerHalf = this.table.headMap(targetIDAddr);
			largerHalf = this.table.tailMap(targetIDAddr);	// may include target

			result.addAll(largerHalf.keySet());
			result.add(this.selfIDAddress);
			result.addAll(smallerHalf.keySet());
		}

		if (joining) {
			for (Iterator<IDAddressPair> ite = result.iterator(); ite.hasNext();) {
				IDAddressPair p = ite.next();
				if (target.equals(p.getID())) ite.remove();
			}
		}

		// reverse the list
		int len = result.size();
		IDAddressPair[] ret = new IDAddressPair[len];
		if (len <= 0) return ret;

//boolean covered;
		if (passOverTarget) {
//covered = true;
			ret[0] = result.get(0);	// successor of target
			for (int i = 0; i < len - 1; i++)
				ret[i + 1] = result.get(len - 1 - i);
		}
		else {
//covered = false;
			for (int i = 0; i < len; i++)
				ret[i] = result.get(len - 1 - i);
		}

//System.out.println("RoutingTable#closestTo(" + target.toString(-1) + ") " + covered + " on " + selfIDAddress.toString(-1) + ":");
//for (int i = 0; i < len; i++) System.out.println("  " + ret[i].toString(-1));
		return ret;
	}

	public int size() { return this.table.size(); }

	public synchronized IDAddressPair getFirstNode() {
		if (!this.table.isEmpty())
			return this.table.firstKey();
		else
			return null;
	}

	public synchronized IDAddressPair getLastNode() {
		if (!this.table.isEmpty())
			return this.table.lastKey();
		else
			return null;
	}

	public synchronized IDAddressPair[] getStickyNodes() {
		Set<IDAddressPair> stickyNodeSet = new HashSet<IDAddressPair>();

		SortedSet<IDAddressPair> keySortedSet = (SortedSet<IDAddressPair>)this.table.keySet();

		if (!keySortedSet.isEmpty()) {
			int i = this.algorithm.config.getNumStickyNodes();
			for (IDAddressPair p: keySortedSet) {
				if (i-- <= 0) break;
				stickyNodeSet.add(p);
			}
			stickyNodeSet.add(keySortedSet.last());
		}

		IDAddressPair[] stickyNodeArray = new IDAddressPair[stickyNodeSet.size()];
		stickyNodeArray = stickyNodeSet.toArray(stickyNodeArray);
		return stickyNodeArray;
	}

	public synchronized IDAddressPair getLastStickyNode() {
		SortedSet<IDAddressPair> keySortedSet = (SortedSet<IDAddressPair>)this.table.keySet();
		int i = this.algorithm.config.getNumStickyNodes();

		IDAddressPair lastStickyNode = null;
		for (IDAddressPair p: keySortedSet) {
			if (i-- <= 0) break;
			lastStickyNode = p;
		}

		return lastStickyNode;
	}

	/**
	 * A routing table entry.
	 * It has normalized interval to the preceding node.
	 */
	protected final class Entry implements Comparable<Entry> {
		private final IDAddressPair idAddr;
		private final double normalizedID;
		double normalizedIDInterval;

		Entry(IDAddressPair idAddr) {
			this.idAddr = idAddr;

			BigInteger distance = RoutingTable.this.algorithm.distance(
					this.idAddr.getID(), RoutingTable.this.selfIDMinusOne);
			this.normalizedID =
				Math.log(distance.doubleValue()) / RoutingTable.this.normalizingConst;
		}

		public IDAddressPair getIDAddressPair() { return this.idAddr; }

		public double getNormalizedID() { return this.normalizedID; }

		private void calculateNormalizedIDInterval(Entry precedingEntry, Entry succeedingEntry) {
			double normalizedIDOfPred;
			double normalizedIDOfSucc;

			if (precedingEntry != null)
				normalizedIDOfPred = precedingEntry.normalizedID;
			else
				normalizedIDOfPred = 0.0;	// normalized distance from self to self

			if (succeedingEntry != null)
				normalizedIDOfSucc = succeedingEntry.normalizedID;
			else
				normalizedIDOfSucc = 1.0;

			this.normalizedIDInterval = normalizedIDOfSucc - normalizedIDOfPred;
//System.out.print("interval: " + succeedingEntry.getIDAddressPair().toString(-1) + " - " + precedingEntry.getIDAddressPair().toString(-1));
//System.out.println(" on " + selfIDAddress.toString(-1));
//System.out.println("  " + normalizedIDOfSucc + " - " + normalizedIDOfPred + " = " + this.normalizedIDInterval);
		}

		public int compareTo(Entry other) {
			if (this.normalizedIDInterval < other.normalizedIDInterval) return -1;
			else if (this.normalizedIDInterval > other.normalizedIDInterval) return 1;
			else return 0;
		}

		public boolean equals(Object o) {
			if (o instanceof Entry) {
				Entry other = (Entry)o;
				if (this.idAddr.equals(other.idAddr)) return true;
			}

			return false;
		}

		public String toString() {
			return this.toString(0);
		}

		public String toString(int verboseLevel) {
			StringBuilder sb = new StringBuilder();

			// <ID>:<address>:<normalized interval>
			sb.append(this.idAddr.toString(verboseLevel));
			sb.append(":");
			String s = null;
			if (verboseLevel < 0)
				s = String.format("%.4g", this.normalizedID);
			else
				s = Double.toString(this.normalizedID);
			sb.append(s);

			return sb.toString();
		}
	}
}

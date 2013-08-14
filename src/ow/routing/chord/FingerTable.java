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

package ow.routing.chord;

import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.routing.RoutingAlgorithm;

/**
 * Chord's finger table.
 */
public final class FingerTable {
	private final static Logger logger = Logger.getLogger("routing");

	private final int idSizeInBit;
	private final IDAddressPair[] fingerTable;
	private final RoutingAlgorithm algorithm;
	private final IDAddressPair selfIDAddressPair;
	private final boolean aggressiveJoining;

	/**
	 * Create a new finger table.
	 *
	 * @param size the number of bits in an ID.
	 */
	public FingerTable(int idSizeInByte, RoutingAlgorithm algorithm,
			IDAddressPair selfIDAddressPair, boolean aggressiveJoining) {
		this.idSizeInBit = idSizeInByte * 8;

		this.fingerTable = new IDAddressPair[this.idSizeInBit + 1];
		this.algorithm = algorithm;
		this.selfIDAddressPair = selfIDAddressPair;
		this.aggressiveJoining = aggressiveJoining;

		this.clear();
	}

	synchronized void clear() {
		// fill the table with self address
		for (int i = 0; i < this.idSizeInBit + 1; i++) {
			this.fingerTable[i] = selfIDAddressPair;
		}
	}

	/**
	 * Return an entry.
	 *
	 * @param k index (1 <= k <= size).
	 */
	public IDAddressPair get(int k) {
		return this.fingerTable[k];
	}

	/**
	 * Set an entry
	 *
	 * @param k index (1 <= k <= size).
	 */
	public IDAddressPair set(int k, IDAddressPair entry) {
		IDAddressPair old;

		synchronized (this) {
			old = fingerTable[k];
			fingerTable[k] = entry;
		}

		return old;
	}

	/**
	 * Put the specified entry to this finger table.
	 *
	 * @return true if an entry was updated.
	 */
	public boolean put(IDAddressPair newEntry) {
		ID newID = newEntry.getID();
		ID selfID = this.selfIDAddressPair.getID();

		BigInteger distanceOfNewEntry =
			algorithm.distance(newID, selfID);
		int largestIndex = distanceOfNewEntry.bitLength();

		return put(newEntry, largestIndex);
	}

	/**
	 * Put the specified entry to this finger table from finger[i] down to possibly finger[1].
	 *
	 * @return true if an entry was updated.
	 */
	public boolean put(IDAddressPair newEntry, int largestIndex) {
		boolean updated = false;

		synchronized (this) {
			int i;
			for (i = largestIndex; i > 0; i--) {
				IDAddressPair existingEntry = this.fingerTable[i];

				// distance from this node is smaller -> better
				if (!this.algorithm.toReplace(existingEntry, newEntry)) {
					// exisintEntry is nearer to the finger target
					if (this.aggressiveJoining)
						break;
					else
						continue;
				}

				this.fingerTable[i] = newEntry;
				updated = true;
			}	// for

			if (updated) {
				logger.log(Level.INFO, "FingerTable#put: " + newEntry
						+ " from " + largestIndex + " to " + (i + 1));
			}
		}	// synchronized (this)

		return updated;
	}

	/**
	 * Remove the specified entry from this finger table
	 * and fill the blanks with the appropriate alternative entry.
	 */
	public void remove(ID target) {
		BigInteger distance = algorithm.distance(target, selfIDAddressPair.getID());
		int possibleLargestIndex = distance.bitLength();
		if (possibleLargestIndex > this.idSizeInBit) {
			// target is this node itself
			return;
		}

		logger.log(Level.INFO, "FingerTable#remove: " + target);

		IDAddressPair altEntry;
		synchronized (this) {
			try {
				altEntry = this.fingerTable[possibleLargestIndex + 1];
			}
			catch (ArrayIndexOutOfBoundsException e) {
				altEntry = this.selfIDAddressPair;
			}

			for (int i = possibleLargestIndex; i > 0; i--) {
				if (this.fingerTable[i].getID().equals(target)) {
					this.fingerTable[i] = altEntry;
				}
				else {
					if (!this.aggressiveJoining)
						continue;
					else
						break;
				}
			}
		}	// synchronized (this)
	}

	/**
	 * Returns the number of nodes which are different from this node itself.
	 * Note that this ratio will be roughly propotional to log(number of nodes).
	 */
	public int numOfDifferentEntries() {
		int count = 0;
		IDAddressPair lastEntry = this.selfIDAddressPair;
		synchronized (this.fingerTable) {
			for (int i = this.idSizeInBit; i >= 1; i--) {
				if (!lastEntry.equals(this.fingerTable[i])) {
					lastEntry = this.fingerTable[i];
					count++;
				}
			}
		}

		return count;
	}
}

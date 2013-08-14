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

package ow.routing.kademlia;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedTowardTargetIDAddrComparator;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingService;
import ow.routing.impl.AbstractRoutingAlgorithm;
import ow.util.HTMLUtil;

/**
 * A RoutingAlgorithm implementing Kademlia.
 */
public final class Kademlia extends AbstractRoutingAlgorithm {
	private final KademliaConfiguration config;

	// k-buckets
	private final int numKBuckets;
	private final KBucket[] kBuckets;
	final int kBucketLength;	// accessed by KBucket#appendToTail()

	protected Kademlia(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		try {
			this.config = (KademliaConfiguration)config;
		}
		catch (ClassCastException e) {
			throw new InvalidAlgorithmParameterException("The given config is not KademliaConfiguration.");
		}

		// prepare k-buckets
		this.numKBuckets = this.config.getIDSizeInByte() * 8;
		this.kBucketLength = this.config.getKBucketLength();

		kBuckets = new KBucket[numKBuckets];
	}

	public synchronized void reset() {
		for (int i = 0; i < this.numKBuckets; i++) {
			kBuckets[i] = null;
		}
	}

	public void stop() { /* do nothing */ }

	public synchronized void suspend() { /* do nothing */ }

	public synchronized void resume() { /* do nothing */ }

	public BigInteger distance(ID to, ID from) {
		BigInteger toInt = to.toBigInteger();
		BigInteger fromInt = from.toBigInteger();

		// XOR distance
		return fromInt.xor(toInt);
	}

	public IDAddressPair[] nextHopCandidates(ID targetID, ID lastHop /*ignored*/, boolean joining,
			int maxNum, RoutingContext cxt) {
		final IDAddressPair[] results = new IDAddressPair[maxNum];

		BigInteger distance = distance(targetID, selfIDAddress.getID());
		int highestSetBit = distance.bitLength() - 1;

		Comparator<IDAddressPair> comparator =
			new AlgoBasedTowardTargetIDAddrComparator(this, targetID);

		// pick nodes from k-buckets
		// and fulfill the resulting array with them
		int index = 0;
		KBucket kb;

		if (highestSetBit >= 0) {	// this node is not the target
			kb = this.kBuckets[highestSetBit];
			if (kb != null) {
				index = pickNodes(index, results, kb, comparator);
				if (index >= results.length) return results;		// fulfilled
			}

			for (int i = highestSetBit - 1; i >= 0; i--) {
				if (distance.testBit(i)) {
					kb = this.kBuckets[i];
					if (kb != null) {
						index = pickNodes(index, results, kb, comparator);
						if (index >= results.length) return results;	// fulfilled
					}
				}
			}
		}

		if (!joining) {
			results[index++] = selfIDAddress;	// this node itself
			if (index >= results.length) return results;			// fulfilled
		}

		if (highestSetBit >= 0) {	// this node is not the target
			for (int i = 0; i < highestSetBit; i++) {
				if (!distance.testBit(i)) {
					kb = this.kBuckets[i];
					if (kb != null) {
						index = pickNodes(index, results, kb, comparator);
						if (index >= results.length) return results;	// fulfilled
					}
				}
			}
		}

		for (int i = highestSetBit + 1; i < this.numKBuckets; i++) {
			kb = this.kBuckets[i];
			if (kb != null) {
				index = pickNodes(index, results, kb, comparator);
			}
		}

		// shorten the array
		IDAddressPair[] ret = new IDAddressPair[index];
		System.arraycopy(results, 0, ret, 0, index);

//System.out.println("target: " + targetID);
//for (IDAddressPair r: ret) {
//	if (r == null) break;
//	System.out.println("  " + r + ": " + distance(r.getID(), targetID));
//}

		return ret;
	}

	public IDAddressPair[] responsibleNodeCandidates(ID target, int maxNum) {
		return this.nextHopCandidates(target, null, false, maxNum, null);
	}

	private int pickNodes(int index, IDAddressPair[] dest, KBucket kb, Comparator<IDAddressPair> comparator) {
		IDAddressPair[] result;
		if (true) {	// performs better
			result = kb.toSortedArray(comparator);
		}
		else {
			result = kb.toArray();
			Arrays.<IDAddressPair>sort(result, comparator);
		}

		int resultLen = result.length;
		int destLen = dest.length;
		int copyLen = Math.min(destLen - index, resultLen);

		System.arraycopy(result, 0, dest, index, copyLen);

		return index + copyLen;
	}

	public boolean toReplace(IDAddressPair existingEntry, IDAddressPair newEntry) {
		if (existingEntry.equals(newEntry)) {
			return false;
		}

		boolean pingSucceeded = false;
		try {
			pingSucceeded = runtime.ping(sender, existingEntry);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "An IOException thrown during ping() from "
					+ this.selfIDAddress.getAddress() + " to " + existingEntry.getAddress());
		}

		return !pingSucceeded;
	}

	public void join(IDAddressPair[] neighbors) {
		// do nothing
	}

	public void join(IDAddressPair joiningNode, IDAddressPair lastHop, boolean isFinalNode) {
		// do nothing
		logger.log(Level.INFO, "On " + selfIDAddress.getAddress() + ", "
				+ "Kademlia#join(" + joiningNode.getAddress() + ", " + (lastHop != null ? lastHop.getAddress() : "null") + ", " + isFinalNode + ") called.");
	}

	public void touch(IDAddressPair from) {
		BigInteger distance = distance(from.getID(), selfIDAddress.getID());
		int highestSetBit = distance.bitLength() - 1;

		if (highestSetBit < 0) {
			// from is myself, and ignore
			return;
		}

		KBucket kb;
		synchronized (this.kBuckets) {
			kb = this.kBuckets[highestSetBit];
			if (kb == null) {
				kBuckets[highestSetBit] = kb = new KBucket(this);
			}
		}
		kb.appendToTail(from);
	}

	/**
	 * Remove the specified node from k-buckets.
	 */
	public void forget(IDAddressPair failedNode) {
		BigInteger distance = distance(failedNode.getID(), selfIDAddress.getID());
		int highestSetBit = distance.bitLength() - 1;

		if (highestSetBit < 0) {
			// from is myself, and ignore
			return;
		}

		synchronized (this.kBuckets) {
			KBucket kb = this.kBuckets[highestSetBit];
			if (kb != null) {
				kb.remove(failedNode);
				if (kb.size() <= 0) {
					this.kBuckets[highestSetBit] = null;
				}
			}
		}
	}

	public String getRoutingTableString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append("[");
		for (int i = 0; i < numKBuckets; i++) {
			KBucket kb = kBuckets[i];
			if (kb != null && kb.size() > 0) {
				sb.append("\n ").append(i).append(":");
				synchronized (kb) {
					for (IDAddressPair pair: kb) {
						sb.append("\n  ").append(pair.toString(verboseLevel));
					}
				}
			}
		}
		sb.append("\n]");

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append("<table>\n");
		for (int i = 0; i < numKBuckets; i++) {
			KBucket kb = kBuckets[i];
			if (kb != null && kb.size() > 0) {
				sb.append("<tr><td>" + HTMLUtil.stringInHTML(Integer.toString(i)) + "</td><td></td><td></td></tr>\n");
				synchronized (kb) {
					for (IDAddressPair pair: kb) {
						String url = HTMLUtil.convertMessagingAddressToURL(pair.getAddress());
						sb.append("<tr><td></td><td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td><td>"
								+ HTMLUtil.stringInHTML(pair.getID().toString()) + "</td></tr>\n");
					}
				}
			}
		}
		sb.append("</table>\n");

		return sb.toString();
	}
}

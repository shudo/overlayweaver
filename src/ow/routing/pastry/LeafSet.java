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

package ow.routing.pastry;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedTowardTargetIDAddrComparator;
import ow.id.comparator.ClockwiseFromSrcIDAddrComparator;
import ow.id.comparator.ClockwiseTowardTargetIDAddrComparator;
import ow.routing.RoutingAlgorithm;
import ow.util.HTMLUtil;

public final class LeafSet {
	private final int oneSideSize;
	// following sets are sorted by distance from ID of this node
	private SortedSet<IDAddressPair> smallerSet;	
	private SortedSet<IDAddressPair> largerSet;

	private volatile RoutingAlgorithm algorithm = null;
	private volatile IDAddressPair selfIDAddress = null;
	private volatile Comparator<IDAddressPair> smallerComparator = null;
	private volatile Comparator<IDAddressPair> largerComparator = null;

	public LeafSet(RoutingAlgorithm algorithm, int idSizeInBit, IDAddressPair self, int oneSideSize) {
		this.algorithm = algorithm;
		this.selfIDAddress = self;
		this.oneSideSize = oneSideSize;

		this.smallerComparator =
			new ClockwiseTowardTargetIDAddrComparator(idSizeInBit, selfIDAddress.getID());
		this.largerComparator =
			new ClockwiseFromSrcIDAddrComparator(idSizeInBit, selfIDAddress.getID());

		this.clear();
	}

	synchronized void clear() {
		this.smallerSet = new TreeSet<IDAddressPair>(this.smallerComparator);
		this.largerSet = new TreeSet<IDAddressPair>(this.largerComparator);
	}

	public void merge(IDAddressPair elem) {
		if (elem.equals(this.selfIDAddress)) return;

		this.mergeToSortedSet(this.smallerSet, elem);
		this.mergeToSortedSet(this.largerSet, elem);
	}

	private void mergeToSortedSet(SortedSet<IDAddressPair> set, IDAddressPair elem) {
		synchronized (set) {
			set.add(elem);

			while (set.size() > this.oneSideSize) {
				IDAddressPair lastElem = set.last();
				set.remove(lastElem);
			}
		}
	}

	public void merge(IDAddressPair[] leafSet) {
		if (leafSet != null) {
			this.mergeToSortedSet(this.smallerSet, leafSet);
			this.mergeToSortedSet(this.largerSet, leafSet);
		}
	}

	private void mergeToSortedSet(SortedSet<IDAddressPair> set, IDAddressPair[] oneSideLeafSet) {
		synchronized (set) {
			synchronized (oneSideLeafSet) {
				for (IDAddressPair p: oneSideLeafSet) {
					if (p == null || p.equals(this.selfIDAddress)) continue;

					set.add(p);
				}
			}

			while (set.size() > this.oneSideSize) {
				IDAddressPair lastElem = set.last();
				set.remove(lastElem);
			}
		}
	}

	public void remove(IDAddressPair elem) {
		synchronized (this.smallerSet) {
			this.smallerSet.remove(elem);
		}

		synchronized (this.largerSet) {
			this.largerSet.remove(elem);
		}
	}

	public boolean coversWithSmallerSet(IDAddressPair elem) {
		try {
			IDAddressPair smallest;
			synchronized (this.smallerSet) {
				smallest = this.smallerSet.last();
			}

			if (this.smallerComparator.compare(elem, smallest) <= 0
					&& !elem.equals(this.selfIDAddress))
				return true;
		}
		catch (NoSuchElementException e) { /* ignore */ }

		return false;
	}

	public boolean coversWithLargerSet(IDAddressPair elem) {
		try {
			IDAddressPair largest;
			synchronized (this.largerSet) {
				largest = this.largerSet.last();
			}

			if (this.largerComparator.compare(elem, largest) <= 0
					&& !elem.equals(this.selfIDAddress))
				return true;
		}
		catch (NoSuchElementException e) { /* ignore */ } 

		return false;
	}

	public boolean coversEntireRing() {
		IDAddressPair smallest = null;
		IDAddressPair largest = null;

		try {
			synchronized (this.smallerSet) {
				smallest = this.smallerSet.last();
			}
			if (this.largerSet.contains(smallest))
				return true;

			synchronized (this.largerSet) {
				largest = this.largerSet.last();
			}
			if (this.smallerSet.contains(largest))
				return true;
		}
		catch (NoSuchElementException e) {
			return true;
		}

		return false;
	}

	public int getOneSideSize() { return this.oneSideSize; }
	public int getNumberOfSmallerNodes() {
		return this.getSizeOfSortedSet(this.smallerSet);
	}
	public int getNumberOfLargerNodes() {
		return this.getSizeOfSortedSet(this.largerSet);
	}

	private int getSizeOfSortedSet(SortedSet<IDAddressPair> set) {
		synchronized (set) { return set.size(); }
	}

	public IDAddressPair getNearestSmallerNode() { return getFirstElement(this.smallerSet); }
	public IDAddressPair getNearestLargerNode() { return getFirstElement(this.largerSet); }
	public IDAddressPair getFarthestSmallerNode() { return getLastElement(this.smallerSet); }
	public IDAddressPair getFarthestLargerNode() { return getLastElement(this.largerSet); }

	private IDAddressPair getFirstElement(SortedSet<IDAddressPair> set) {
		try { return set.first(); } catch (NoSuchElementException e) { return null; }
	}

	private IDAddressPair getLastElement(SortedSet<IDAddressPair> set) {
		try { return set.last(); } catch (NoSuchElementException e) { return null; }
	}

	public SortedSet<IDAddressPair> closestNodes(ID target, int maxNum) {
		Comparator<IDAddressPair> towardTargetComparator =
			new AlgoBasedTowardTargetIDAddrComparator(this.algorithm, target);
		SortedSet<IDAddressPair> result = new TreeSet<IDAddressPair>(towardTargetComparator);

		result.add(selfIDAddress);	// results includes this node itself

		synchronized (this.smallerSet) {
			result.addAll(this.smallerSet);
		}

		synchronized (this.largerSet) {
			result.addAll(this.largerSet);
		}

		// trim
		while (result.size() > maxNum) {
			IDAddressPair lastElem = result.last();
			result.remove(lastElem);
		}

		return result;
	}

	public IDAddressPair[] toArray() { return this.toArray(null); }
	public IDAddressPair[] toArray(IDAddressPair exclude) {
		Set<IDAddressPair> retSet = new HashSet<IDAddressPair>();
		IDAddressPair[] ret;

		this.addtoCollection(retSet);
		retSet.remove(exclude);

		ret = new IDAddressPair[retSet.size()];
		retSet.toArray(ret);

		return ret;
	}
	public void addtoCollection(Collection<IDAddressPair> c) {
		synchronized(this.smallerSet) {
			c.addAll(this.smallerSet);
		}
		synchronized(this.largerSet) {
			c.addAll(this.largerSet);
		}
	}

	public String toString() {
		return this.toString(0);
	}

	public String toString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append("[");

		synchronized (this.smallerSet) {
			for (IDAddressPair p: this.smallerSet) {
				sb.append(" ");
				sb.append(p.getAddress().toString(verboseLevel));
			}
		}

		sb.append(" |");

		synchronized (this.largerSet) {
			for (IDAddressPair p: this.largerSet) {
				sb.append(" ");
				sb.append(p.getAddress().toString(verboseLevel));
			}
		}

		sb.append(" ]");

		return sb.toString();
	}

	public String toHTMLString() {
		StringBuilder sb = new StringBuilder();
		String url;

		synchronized (this.smallerSet) {
			for (IDAddressPair p: this.smallerSet) {
				url = HTMLUtil.convertMessagingAddressToURL(p.getAddress());
				sb.append("<a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a><br>\n");
			}
		}

		url = HTMLUtil.convertMessagingAddressToURL(this.selfIDAddress.getAddress());
		sb.append("<a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a> (self)<br>\n");

		synchronized (this.largerSet) {
			for (IDAddressPair p: this.largerSet) {
				url = HTMLUtil.convertMessagingAddressToURL(p.getAddress());
				sb.append("<a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a><br>\n");
			}
		}

		return sb.toString();
	}
}

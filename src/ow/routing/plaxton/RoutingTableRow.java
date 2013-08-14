/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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

package ow.routing.plaxton;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import ow.id.IDAddressPair;
import ow.util.HTMLUtil;

public final class RoutingTableRow implements Serializable {
	private transient RoutingTable routingTable;
	private final int selfDigit;
	private IDAddressPair[] entries = null;

	protected RoutingTableRow(RoutingTable routingTable, int selfDigit) {
		this.routingTable = routingTable;
		this.selfDigit = selfDigit;
	}

	public RoutingTableRow(RoutingTableRow row) {	// copy constructor
		this.routingTable = row.routingTable;
		this.selfDigit = row.selfDigit;
		this.prepareBody();
		System.arraycopy(row.entries, 0, this.entries, 0, row.entries.length);
	}

	void setRoutingTable(RoutingTable routingTable) {
		this.routingTable = routingTable;
	}

	public int size() {
		return this.routingTable.colSize;
	}

	public boolean isEmpty() { return this.entries == null; }

	private boolean checkEmptyOrNot() {
		if (this.entries == null) return true;

		for (int i = 0; i < this.entries.length; i++) {
			if (i == this.selfDigit) continue;
			if (this.entries[i] != null) {
				return false;
			}
		}

		this.entries = null;
		return true;
	}

	private synchronized void prepareBody() {
		if (this.entries == null) {
			this.entries = new IDAddressPair[this.routingTable.colSize];
			this.entries[selfDigit] = this.routingTable.selfIDAddress;
		}
	}

	/**
	 * An accessor.
	 */
	public IDAddressPair get(int col) {
		if (col == this.selfDigit)
			return this.routingTable.selfIDAddress;
		else if (this.entries == null)
			return null;
		else
			return this.entries[col];
	}

	/**
	 * An accessor.
	 */
	protected synchronized IDAddressPair set(int col, IDAddressPair entry) {
		if (col == this.selfDigit) return null;	// refuse to set

		if (entry == null) return null;	// refuse to set null

		this.prepareBody();

		IDAddressPair old = this.entries[col];
		this.entries[col] = entry;

		return old;
	}

	protected synchronized IDAddressPair merge(int col, IDAddressPair entry, Plaxton algo) {
		IDAddressPair existingEntry = this.get(col);

		if (existingEntry == null
				|| algo.toReplace(existingEntry, entry)) {
//			System.out.println("RoutingTableRow merged (" + this.rowIndex + "," + Integer.toHexString(col) + "): " + entry);
			return this.set(col, entry);
		}
		else {
//			if (existingEntry.equals(entry))
//			System.out.println("RoutingTableRow equals (" + this.rowIndex + "," + Integer.toHexString(col) + "): " + entry);
//			else
//			System.out.println("RoutingTableRow not merged (" + this.rowIndex + "," + Integer.toHexString(col) + "): " + entry);
			return null;
		}
	}

	protected synchronized IDAddressPair remove(int col) {
		IDAddressPair old = this.get(col);

		if (this.entries != null) {
			this.entries[col] = null;
			this.checkEmptyOrNot();
		}
//System.out.println("RoutingTableRow remove (" + this.rowIndex + "," + Integer.toHexString(col) + ")");
//(new Exception()).printStackTrace();

		return old;
	}

	public synchronized Set<IDAddressPair> getAllNodes() {
		Set<IDAddressPair> set = new HashSet<IDAddressPair>();

		if (this.entries != null) {
			for (int i = 0; i < this.entries.length; i++) {
				IDAddressPair p = this.entries[i];
				if (p != null) set.add(p);
			}
		}
		else {
			set.add(this.routingTable.selfIDAddress);
		}

		return set;
	}

	public String toString() {
		return toString(false, 0);
	}

	public String toString(boolean onlyAddress, int verboseLevel) {
		if (this.entries == null) return "";

		StringBuilder sb = new StringBuilder();

		boolean firstEntry = true;
		for (int i = 0; i < this.entries.length; i++) {
			if (i == this.selfDigit) continue;

			IDAddressPair entry = this.entries[i];
			if (entry != null) {
				if (firstEntry)
					firstEntry = false;
				else
					sb.append(" ");

				sb.append(Integer.toHexString(i));
				sb.append(":");
				if (onlyAddress) {
					sb.append(entry.getAddress().toString(verboseLevel));
				}
				else {
					sb.append(entry.toString(verboseLevel));
				}
			}
		}

		return sb.toString();
	}

	public String toHTMLString() {
		if (this.entries == null) return "";

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < this.entries.length; i++) {
			if (i == this.selfDigit) continue;

			IDAddressPair entry = this.entries[i];
			if (entry != null) {
				String url = HTMLUtil.convertMessagingAddressToURL(entry.getAddress());

				sb.append("<tr><td></td><td>" + HTMLUtil.stringInHTML(Integer.toHexString(i)) + "</td>"
						+ "<td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td></tr>\n");
			}
		}

		return sb.toString();
	}
}

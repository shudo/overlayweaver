/*
 * Copyright 2006,2008-2010 National Institute of Advanced Industrial Science
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

import java.util.HashSet;
import java.util.Set;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.util.HTMLUtil;

public final class RoutingTable {
	private final int digitSize;
	private final Plaxton algorithm;
	protected final IDAddressPair selfIDAddress;
	protected int colSize;
	private RoutingTableRow[] rows;

	public RoutingTable(int rowSize, int digitSize, IDAddressPair selfIDAddress, Plaxton algo) {
		this.digitSize = digitSize;
		this.algorithm = algo;
		this.selfIDAddress = selfIDAddress;

		this.initialize(rowSize);
	}

	private synchronized void initialize(int rowSize) {
		this.colSize = 1 << digitSize;	// 2 ^ digit_size

		// create a table
		this.rows = new RoutingTableRow[rowSize];
		for (int i = 0; i < rowSize; i++) {
			int digit = this.algorithm.getDigit(this.selfIDAddress.getID(), i);

			this.rows[i] = new RoutingTableRow(this, digit);
		}
	}

	void clear() {
		this.initialize(this.rows.length);
	}

	public IDAddressPair merge(IDAddressPair entry) {
		int nMatchBits = ID.matchLengthFromMSB(this.selfIDAddress.getID(), entry.getID());
		int nMatchDigits = nMatchBits / this.digitSize;
		if (nMatchDigits >= this.rows.length) return null;
		int notMatchDigit = this.algorithm.getDigit(entry.getID(), nMatchDigits);

		RoutingTableRow row = this.rows[nMatchDigits];
		return row.merge(notMatchDigit, entry, this.algorithm);
	}

	public void merge(RoutingTableRow row) {
		if (row == null) return;

		row.setRoutingTable(this);

		for (int i = 0; i < row.size(); i++) {
			IDAddressPair entry = row.get(i);
			if (entry != null) {
				this.merge(entry);
			}
		}
	}

	public boolean remove(IDAddressPair entry) {
		int nMatchBits = ID.matchLengthFromMSB(this.selfIDAddress.getID(), entry.getID());
		int nMatchDigits = nMatchBits / this.digitSize;
		if (nMatchDigits >= this.rows.length) return false;
		int notMatchDigit = this.algorithm.getDigit(entry.getID(), nMatchDigits);

		RoutingTableRow row = this.rows[nMatchDigits];

		synchronized (row) {
			IDAddressPair existingEntry = row.get(notMatchDigit);
			if (entry.equals(existingEntry)) {
				row.remove(notMatchDigit);

				return true;
			}
		}

		return false;
	}

	public RoutingTableRow getRow(int row) {
		return this.rows[row];
	}

	public Set<IDAddressPair> getNodes(int startRow, int width) {
		width = Math.min(width, this.rows.length - startRow);

		Set<IDAddressPair> nodeSet = new HashSet<IDAddressPair>();
		for (int i = 0; i < width; i++) {
			RoutingTableRow row = this.getRow(startRow + i);
			nodeSet.addAll(row.getAllNodes());
		}

		return nodeSet;
	}

	public String toString() {
		return this.toString(0);
	}

	public String toString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append("[\n");
		for (int i = 0; i < this.rows.length; i++) {
			RoutingTableRow row = this.rows[i];

			if (row.isEmpty()) continue;

			sb.append(" ").append(i).append(" ");
			sb.append(row.toString(true, verboseLevel));
			sb.append("\n");
		}
		sb.append("]");

		return sb.toString();
	}

	public String toHTMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append("<table>\n");
		for (int i = 0; i < this.rows.length; i++) {
			RoutingTableRow row = this.rows[i];

			if (row.isEmpty()) continue;

			sb.append("<tr><td>" + HTMLUtil.stringInHTML(Integer.toString(i))
					+ "</td><td></td><td></td></tr>\n");
			sb.append(row.toHTMLString());
		}
		sb.append("</table>\n");

		return sb.toString();
	}
}

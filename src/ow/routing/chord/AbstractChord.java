/*
 * Copyright 2006-2007,2009-2011 National Institute of Advanced Industrial Science
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

import java.security.InvalidAlgorithmParameterException;
import java.util.SortedSet;

import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingService;
import ow.routing.linearwalker.LinearWalker;
import ow.util.HTMLUtil;

public abstract class AbstractChord extends LinearWalker {
	// routing table
	final FingerTable fingerTable;

	protected AbstractChord(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		// initialize routing table
		this.fingerTable = new FingerTable(config.getIDSizeInByte(), this, selfIDAddress,
				this.config.getAggressiveJoiningMode());
	}

	public void reset() {
		super.reset();
		this.fingerTable.clear();
	}

	// called by LinearWalker#nextHopCandidates()
	protected void addToNextHopCandidatesSet(SortedSet<IDAddressPair> nextHopCandsSet) {	// overrides LinearWalker
		for (int i = 1; i <= this.idSizeInBit; i++)
			nextHopCandsSet.add(this.fingerTable.get(i));
	}

	public void touch(IDAddressPair from) {
		if (this.config.getUpdateRoutingTableByAllCommunications()) {
			super.touch(from);

			this.fingerTable.put(from);
		}
	}

	public void forget(IDAddressPair failedNode) {
		super.forget(failedNode);

		// finger table
		this.fingerTable.remove(failedNode.getID());
	}

	public String getRoutingTableString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append(super.getRoutingTableString(verboseLevel));	// successors and predecessor
		sb.append("\n");

		sb.append("finger table: [");
		IDAddressPair lastEntry = null;
		for (int i = 1; i <= this.idSizeInBit; i++) {
			IDAddressPair entry = this.fingerTable.get(i);
			if (!entry.equals(lastEntry)) {
				sb.append("\n ").append(i).append(": ").append(entry.toString(verboseLevel));
				lastEntry = entry;
			}
		}
		sb.append("\n]");

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append(super.getRoutingTableHTMLString());	// successors and predecessor

		sb.append("<h4>Finger Table</h4>\n");
		sb.append("<table>\n");
		IDAddressPair lastEntry = null;
		for (int i = 1; i <= this.idSizeInBit; i++) {
			IDAddressPair entry = this.fingerTable.get(i);
			if (!entry.equals(lastEntry)) {
				String url = HTMLUtil.convertMessagingAddressToURL(entry.getAddress());
				sb.append("<tr><td>" + HTMLUtil.stringInHTML(Integer.toString(i)) + "</td>"
						+ "<td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td>"
						+ "<td>" + HTMLUtil.stringInHTML(entry.getID().toString()) + "</td></tr>\n");
				lastEntry = entry;
			}
		}
		sb.append("</table>\n");

		return sb.toString();
	}

	class ReqSuccAndPredMessageHandler extends LinearWalker.ReqSuccAndPredMessageHandler {
		public Message process(Message msg) {
			Message repMsg = super.process(msg);

			// try to add the predecessor to finger table and successor list
			// these does not exist in the Figure 6
			fingerTable.put((IDAddressPair)msg.getSource());

			return repMsg;
		}
	}
}

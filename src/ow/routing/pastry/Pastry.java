/*
 * Copyright 2006-2011 National Institute of Advanced Industrial Science
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedTowardTargetIDAddrComparator;
import ow.id.comparator.AlgoBasedTowardTargetIDComparator;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingService;
import ow.routing.pastry.message.JoinedMessage;
import ow.routing.pastry.message.RepLeafSetMessage;
import ow.routing.pastry.message.RepRoutingTableRowMessage;
import ow.routing.pastry.message.ReqLeafSetMessage;
import ow.routing.pastry.message.ReqRoutingTableRowMessage;
import ow.routing.pastry.message.UpdateRoutingTableMessage;
import ow.routing.plaxton.Plaxton;
import ow.routing.plaxton.RoutingTableRow;
import ow.util.Timer;

public final class Pastry extends Plaxton {
	// messages
	private final Message reqLeafSetMessage;

	private PastryConfiguration config;

	private LeafSet leafSet = null;

	private RoutingTableMaintainer routingTableMaintainer;
	private Thread routingTableMaintainerThread = null;

	protected Pastry(RoutingAlgorithmConfiguration conf, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(conf, routingSvc);

		this.config = (PastryConfiguration)conf;

		if (this.config.getUseLeafSet()) {
			if (routingSvc != null) {
				this.leafSet = new LeafSet(this, this.config.getIDSizeInByte() * 8,
						routingSvc.getSelfIDAddressPair(), this.config.getLeafSetOneSideSize());
			}
			else {
				logger.log(Level.SEVERE, "routingSvc is null. test?");
			}
		}

		// prepare messages
		this.reqLeafSetMessage = new ReqLeafSetMessage();

		// initialize message handlers
		this.prepareHandlers();

		// does not invoke routing table maintainer
		//startRoutingTableMaintainer();
	}

	private synchronized void startRoutingTableMaintainer() {
		if (!this.config.getDoPeriodicRoutingTableMaintenance()) return;

		if (this.routingTableMaintainer != null) return;	// to avoid multiple invocations

		this.routingTableMaintainer = new RoutingTableMaintainer();

		if (this.config.getUseTimerInsteadOfThread()) {
			timer.schedule(this.routingTableMaintainer, Timer.currentTimeMillis(),
					true /*isDaemon*/, true /*executeConcurrently*/);
		}
		else if (this.routingTableMaintainerThread == null){
			this.routingTableMaintainerThread = new Thread(this.routingTableMaintainer);
			this.routingTableMaintainerThread.setName("RoutingTableMaintainer on " + selfIDAddress.getAddress());
			this.routingTableMaintainerThread.setDaemon(true);
			this.routingTableMaintainerThread.start();
		}
	}

	private synchronized void stopRoutingTableMaintainer() {
		if (this.routingTableMaintainerThread != null) {
			this.routingTableMaintainerThread.interrupt();
			this.routingTableMaintainerThread = null;
		}
	}

	public void reset() {
		super.reset();

		if (this.leafSet != null)
			this.leafSet.clear();
	}

	public synchronized void stop() {
		super.stop();
		this.stopRoutingTableMaintainer();
	}

	public synchronized void suspend() {
		super.suspend();
		this.stopRoutingTableMaintainer();
	}

	public synchronized void resume() {
		super.resume();
		this.startRoutingTableMaintainer();
	}

	private final BigInteger ID_SPACE_SIZE = BigInteger.ONE.shiftLeft(idSizeInBit);
	private final BigInteger HALF_ID_SPACE_SIZE = BigInteger.ONE.shiftLeft(idSizeInBit - 1);

	public BigInteger distance(ID to, ID from) {
		BigInteger toInt = to.toBigInteger();
		BigInteger fromInt = from.toBigInteger();

		BigInteger distance = toInt.subtract(fromInt);
		if (distance.compareTo(BigInteger.ZERO) < 0) {
			distance = distance.add(ID_SPACE_SIZE);
		}

		if (distance.compareTo(HALF_ID_SPACE_SIZE) < 0) {	// if d < 2^(ID_SIZE - 1)
			// d = d * 2
			distance = distance.shiftLeft(1);
		}
		else {
			// d = ((2 ^ ID_SIZE) - d) * 2 - 1
			distance = ID_SPACE_SIZE.subtract(distance);
			distance = distance.shiftLeft(1);
			distance = distance.subtract(BigInteger.ONE);
		}

		return distance;
	}

	public IDAddressPair[] nextHopCandidates(ID target, ID lastHop /*ignored*/, boolean joining,
			int maxNum, RoutingContext cxt) {
		SortedSet<IDAddressPair> nodesByLeafSet = null;
		IDAddressPair[] nodesByRoutingTable = null;
		boolean leafSetPreferred = false;
		int num = 0;

		// leaf set
		if (this.leafSet != null) {
			nodesByLeafSet = this.leafSet.closestNodes(target, maxNum);
				// includes this node itself
			num = nodesByLeafSet.size();

			// check if leaf set covers the target
			IDAddressPair targetIDAddress = IDAddressPair.getIDAddressPair(target, null);
			if (target.equals(selfIDAddress.getID())
					|| this.leafSet.coversWithSmallerSet(targetIDAddress)
					|| this.leafSet.coversWithLargerSet(targetIDAddress)) {
				leafSetPreferred = true;
			}
		}

		// routing table (Plaxton et al.)
		if (!leafSetPreferred || num < maxNum) {
			nodesByRoutingTable = super.nextHopCandidates(target, lastHop, joining, maxNum - num, cxt);
				// includes this node itself,

			// decide which is preferred, leaf set or routing table
			if (!leafSetPreferred
					&& nodesByLeafSet != null && nodesByLeafSet.size() > 0
					/*&& nodesByRoutingTable != null*/ && nodesByRoutingTable.length > 0) {
				ID headOfLeafSet = nodesByLeafSet.first().getID();
				ID headOfRoutingTable = nodesByRoutingTable[0].getID();

				Comparator<ID> toTargetComparator = new AlgoBasedTowardTargetIDComparator(this, target);
				if (toTargetComparator.compare(headOfLeafSet, headOfRoutingTable) <= 0) {
					leafSetPreferred = true;
				}
			}
		}

		// merge
		List<IDAddressPair> result = new ArrayList<IDAddressPair>();
		if (leafSetPreferred) {
			if (nodesByLeafSet != null)
				result.addAll(nodesByLeafSet);
			if (nodesByRoutingTable != null)
				for (IDAddressPair p: nodesByRoutingTable) result.add(p);
		}
		else {
			if (nodesByRoutingTable != null)
				for (IDAddressPair p: nodesByRoutingTable) result.add(p);
			if (result.size() < maxNum)
				if (nodesByLeafSet != null)
					result.addAll(nodesByLeafSet);
		}

		num = Math.min(result.size(), maxNum);

		IDAddressPair[] ret = new IDAddressPair[num];
		for (int i = 0; i < num; i++) {
			ret[i] = result.get(i);
		}
		return ret;
	}

// (almost) original algorithm
//	public IDAddressPair[] nextHopCandidates(ID target, ID lastHop, boolean joining, int maxNumber, RoutingContext context) {
//		SortedSet<IDAddressPair> setByLeafSet = null;
//		List<IDAddressPair> listByLeafSet = null;
//		IDAddressPair[] arrayByRoutingTable = null;
//
//		// leaf set
//		if (this.leafSet != null) {
//			IDAddressPair targetIDAddress = IDAddressPair.getIDAddressPair(target, null);
//
//			if (target.equals(selfIDAddress.getID())
//					|| this.leafSet.coversWithSmallerSet(targetIDAddress)
//					|| this.leafSet.coversWithLargerSet(targetIDAddress)) {
//				// leaf set covers the target
//				setByLeafSet = this.leafSet.closestNodes(target, maxNum);
//					// includes this node itself
//			}
//		}
//
//		int num = 0;
//
//		if (setByLeafSet != null) {
//			listByLeafSet = new ArrayList<IDAddressPair>();
//			listByLeafSet.addAll(setByLeafSet);
//
//			num = listByLeafSet.size();
//		}
//
//		// routing table (Plaxton et al.)
//		if (num < maxNum) {
//			arrayByRoutingTable = super.nextHopCandidates(target, maxNum - num, cxt);
//				// includes this node itself,
//
//				// It's possible for the obtained list not to be in order of ID distance.
//				// This can cause a loop in routing.
//				// Plaxton table traversal necessarily yields this incorrectly-ordered list. 
//				// It is very easy to sort the list of nodes instead of traversal
//				// but Plaxton table loses its meaning if do it. Isn't it?
//		}
//
//		if (listByLeafSet == null) return arrayByRoutingTable;
//
//		// merge
//		if (arrayByRoutingTable != null) {
//			for (IDAddressPair p: arrayByRoutingTable) {
//				if (!setByLeafSet.contains(p)) {
//					listByLeafSet.add(p);
//				}
//			}
//
//			num = listByLeafSet.size();
//		}
//
//		IDAddressPair[] ret = new IDAddressPair[num];
//		for (int i = 0; i < num; i++) {
//			ret[i] = listByLeafSet.get(i);
//		}
//		return ret;
//	}

	protected List<IDAddressPair> traverseDownward(int rowIndex, int startingCol, ID target, int maxNum) {
		Comparator<IDAddressPair> comparator =
			new AlgoBasedTowardTargetIDAddrComparator(this, target);
		List<IDAddressPair> results = new ArrayList<IDAddressPair>();

		traverseDownward(results, comparator, rowIndex, startingCol, target, maxNum);
			// results include this node itself

		return results;
	}

	private void traverseDownward(List<IDAddressPair> results,
			Comparator<IDAddressPair> comparator,
			int rowIndex, int startingCol, ID target, int maxNum) {
		RoutingTableRow row = this.routingTable.getRow(rowIndex);
		int rowSize = row.size();

		if (pickCloseEntry(results, comparator, maxNum,
				target, row, rowIndex, startingCol, 0)) {
			return;
		}

		for (int i = 1; i < rowSize; i++) {
			int colIndex = startingCol + i;
			if (colIndex < rowSize) {
//System.out.println("[" + Integer.toHexString(rowIndex) + ":" + Integer.toHexString(colIndex) + "]");
				if (pickCloseEntry(results, comparator,
						maxNum, target, row, rowIndex, colIndex, +1)) {
					break;
				}
			}

			colIndex = startingCol - i;
			if (colIndex >= 0) {
//System.out.println("[" + Integer.toHexString(rowIndex) + ":" + Integer.toHexString(colIndex) + "]");
				if (pickCloseEntry(results, comparator,
						maxNum, target, row, rowIndex, colIndex, -1)) {
					break;
				}
			}
		}
	}

	// a specialized version of traverseDownward(): startingCol is 0.
	private void traverseDownwardFromLeftToRight(List<IDAddressPair> results,
			int rowIndex, ID target, int maxNum) {
		RoutingTableRow row = this.routingTable.getRow(rowIndex);
		int rowSize = row.size();

		for (int i = 0; i < rowSize; i++) {
			if (pickCloseEntry(results, null, maxNum, target, row, rowIndex, i, +1)) break;
		}
	}

	// a specialized version of traverseDownward(): startingCol is row.size() - 1.
	private void traverseDownwardFromRightToLeft(List<IDAddressPair> results,
			int rowIndex, ID target, int maxNum) {
		RoutingTableRow row = this.routingTable.getRow(rowIndex);
		int rowSize = row.size();

		for (int i = rowSize - 1; i >= 0; i--) {
			if (pickCloseEntry(results, null, maxNum, target, row, rowIndex, i, -1)) break;
		}
	}

	private boolean pickCloseEntry(List<IDAddressPair> results, Comparator<IDAddressPair> comparator,
			int maxNum, ID target, RoutingTableRow row,
			int rowIndex, int colIndex, int exploringSide) {
		IDAddressPair entry = row.get(colIndex);
		if (entry == null) return false;

		if (entry.equals(selfIDAddress) && rowIndex + 1 < idSizeInDigit) {
			// recursive call
			if (exploringSide > 0) {
				traverseDownwardFromLeftToRight(results,
						rowIndex + 1, target, maxNum - results.size());
			}
			else if (exploringSide < 0) {
				traverseDownwardFromRightToLeft(results,
						rowIndex + 1, target, maxNum - results.size());
			}
			else {
				traverseDownward(results, comparator,
						rowIndex + 1, colIndex, target, maxNum - results.size());
			}
		}

		results.add(entry);	// can be selfIDAddress on the lowest row

		if (results.size() >= maxNum)
			return true;
		else
			return false;
	}

	protected List<IDAddressPair> traverseUpward(int rowIndex, int maxNum) {
		// Plaxton#traverseUpward() is provided here
		// mainly for getting enough number of neighbors in Tapestry.
		// In Pastry, this method will not be called usually
		// because a leaf set provides enough number of neighbors.

		List<IDAddressPair> results = new ArrayList<IDAddressPair>();
		ID selfID = selfIDAddress.getID();

		outer:
		for (int i = rowIndex - 1; i >= 0; i--) {
			RoutingTableRow row = this.routingTable.getRow(i);
			int rowSize = row.size();
			int digit = getDigit(selfID, i);

			int colIndex;
			IDAddressPair entry;
			for (int j = 0; j < rowSize; j++) {
				if (j != 0) {
					colIndex = digit + j;
					if (colIndex < rowSize) {
						if ((entry = row.get(colIndex)) != null) {
							results.add(entry);
							if (results.size() >= maxNum) break outer;
						}
					}
				}

				colIndex = (digit - 1 - j) % rowSize;
				if (colIndex >= 0) {
					if ((entry = row.get(colIndex)) != null) {
						results.add(entry);
						if (results.size() >= maxNum) break outer;
					}
				}
			}
		}

		return results;
	}

	public void join(IDAddressPair joiningNode, IDAddressPair lastHop, boolean isFinalNode) {
//System.out.println("join:");
//System.out.println("  lastHop:     " + (lastHop == null ? "(null)" : lastHop));
//System.out.println("  self:        " + selfIDAddress);
//System.out.println("  joiningNode: " + joiningNode);
		super.join(joiningNode, lastHop, isFinalNode);

		if (lastHop == null) {
			// this node is the joining node
			// and do not have to send a UPDATE_ROUTING_TABLE message
			return;
		}

		int startRow, endRow;

		if (lastHop.equals(joiningNode)) {
			// on the next node to the joining node
			startRow = 0;
		}
		else {
			int lastMatchBits = ID.matchLengthFromMSB(lastHop.getID(), joiningNode.getID());
			int lastMatchDigits = lastMatchBits / digitSize;

			startRow = lastMatchDigits + 1;
//System.out.println("lastMatchDigits: " + lastMatchDigits);
		}

		int selfMatchBits = ID.matchLengthFromMSB(selfIDAddress.getID(), joiningNode.getID());
		int selfMatchDigits = selfMatchBits / digitSize;

		endRow = selfMatchDigits;
		if (isFinalNode || endRow >= idSizeInDigit) {
			// on the last hop (or overflow)
			endRow = idSizeInDigit - 1;
		}

//System.out.println("  selfMatchDigits: " + selfMatchDigits);
//System.out.println("  startRow: " + startRow);
//System.out.println("  endRow:   " + endRow);
		if (startRow < idSizeInDigit && startRow <= endRow) {
			int rowSetWidth = endRow - startRow + 1;
//System.out.println("rowSetWidth:     " + rowSetWidth);

			Set<IDAddressPair> nodeSet = this.routingTable.getNodes(startRow, rowSetWidth);
			nodeSet.remove(joiningNode);	// is not required
			IDAddressPair[] nodes = new IDAddressPair[nodeSet.size()];
			nodeSet.toArray(nodes);

			IDAddressPair[] leafSetArray = null;
			if (this.leafSet != null && isFinalNode) {
				leafSetArray = this.leafSet.toArray(joiningNode);
			}

			Message reqMsg = new UpdateRoutingTableMessage(
					nodes, leafSetArray, isFinalNode);
//int nLeafSet = (leafSetArray != null ? leafSetArray.length : 0);
//System.out.println("UPDATE_ROUTING_TABLE sent to " + joiningNode.getAddress() + ": " +
//nodes.length + ", " + nLeafSet + ", " + isFinalHop);

			try {
				sender.send(joiningNode.getAddress(), reqMsg);
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "Failed to send a UPDATE_ROUNTING_TABLE message: " + joiningNode.getAddress(), e);

				fail(joiningNode);
			}
		}

		// add the joining node to routing table
		this.routingTable.merge(joiningNode);
		if (this.leafSet != null)
			this.leafSet.merge(joiningNode);
	}

	public void touch(IDAddressPair from) {
		if (this.config.getUpdateRoutingTableByAllCommunications()) {
			// processes not described in the paper
			super.touch(from);

			if (this.leafSet != null)
				this.leafSet.merge(from);
		}
	}

	public void forget(IDAddressPair failedNode) {
		super.forget(failedNode);

		if (this.leafSet != null) {
			this.leafSet.remove(failedNode);
			this.fillLeafSet();
		}
	}

	public String getRoutingTableString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append(this.routingTable.toString(verboseLevel));

		if (this.leafSet != null) {
			sb.append("\n");
			sb.append("leaf set: ");
			sb.append(this.leafSet.toString(verboseLevel));
		}

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append("<h4>Plaxton Routing Table</h5>\n");
		sb.append(this.routingTable.toHTMLString());

		if (this.leafSet != null) {
			sb.append("<h4>Leaf Set</h4>");
			sb.append(this.leafSet.toHTMLString());
		}

		return sb.toString();
	}

	private void updateLeafSet() {
		IDAddressPair contact, lastContact;

		contact = null;
		while (true) {
			lastContact = contact;
			contact = this.leafSet.getNearestSmallerNode();
			if (contact.equals(lastContact)) break;

			this.requestLeafSet(contact);
		}

		contact = null;
		while (true) {
			lastContact = contact;
			contact = this.leafSet.getNearestLargerNode();
			if (contact.equals(lastContact)) break;

			this.requestLeafSet(contact);
		}
	}

	private void fillLeafSet() {
		if (this.leafSet.coversEntireRing()) {
			// current leaf set covers entire ring
			return;
		}

		int oneSideSize = this.leafSet.getOneSideSize();
		if (this.leafSet.getNumberOfSmallerNodes() < oneSideSize) {
			// smaller half of leaf set is not full
			IDAddressPair smallest = this.leafSet.getFarthestSmallerNode();
			if (smallest != null) {
				this.requestLeafSet(smallest);
			}
		}

		if (this.leafSet.getNumberOfLargerNodes() < oneSideSize) {
			// larger half of leaf set is not full
			IDAddressPair largest = this.leafSet.getFarthestLargerNode();
			if (largest != null) {
				this.requestLeafSet(largest);
			}
		}
	}

	private void requestLeafSet(IDAddressPair target) {
		Message reqMsg = this.reqLeafSetMessage;
		Message repMsg;
		try {
			repMsg = sender.sendAndReceive(target.getAddress(), reqMsg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send or receive REQ/REP_LEAF_SET message.", e);
			fail(target);
			return;
		}

		IDAddressPair[] leafSetArray = ((RepLeafSetMessage)repMsg).leafSet;

		this.leafSet.merge(leafSetArray);

		// merge nodes into routing table
		// processes not described in the paper
		for (IDAddressPair p: leafSetArray) this.routingTable.merge(p);
	}

	private Runnable joinReportingTask = null;
	private boolean joinReported = false;
		// for UPDATE_ROUTING_TABLE message

	protected void prepareHandlers() {
		super.prepareHandlers();

		MessageHandler handler;

		// UPDATE_ROUTING_TABLE
		handler = new MessageHandler() {
			public Message process(final Message msg) {
				IDAddressPair[] nodes = ((UpdateRoutingTableMessage)msg).nodes;
				IDAddressPair[] leafSetArray = ((UpdateRoutingTableMessage)msg).leafSet;
				boolean fromFinalHop = ((UpdateRoutingTableMessage)msg).fromFinalHop;

				logger.log(Level.INFO, "UPDATE_ROUTING_TABLE received: # of nodes: " + nodes.length + " on " + selfIDAddress.getAddress());

				// merge row set
				for (IDAddressPair p: nodes) {
					routingTable.merge(p);
				}
				routingTable.merge((IDAddressPair)msg.getSource());

				if (leafSet != null && leafSetArray != null) {
					// merge leaf set
					leafSet.merge((IDAddressPair)msg.getSource());	// source node should be a neighbor
					leafSet.merge(leafSetArray);

					// fill leaf set
					fillLeafSet();
				}

				synchronized (Pastry.this) {
					if (Pastry.this.joinReportingTask == null && !Pastry.this.joinReported) {
						Runnable r = new Runnable() {
							public void run() {
								// clear itself
								Pastry.this.joinReportingTask = null;
								Pastry.this.joinReported = true;

								// notify all nodes on routing table
								Set<IDAddressPair> nodesToBeNotified = new HashSet<IDAddressPair>();

								nodesToBeNotified.addAll(routingTable.getNodes(0, Integer.MAX_VALUE));

								if (leafSet != null) {
									leafSet.addtoCollection(nodesToBeNotified);
								}

								Pastry.this.sendJoinedMessage(nodesToBeNotified);
							}
						};

						Pastry.this.joinReportingTask = r;

						timer.schedule(r, System.currentTimeMillis() + 30 * 1000L);	// 30 sec later
					}
				}

				// notify of this joined node
				if (fromFinalHop) {
					Runnable r = null;

					synchronized (Pastry.this) {
						r = Pastry.this.joinReportingTask;
						if (r != null) {
							Pastry.this.joinReported = true;
							timer.cancel(r);
						}
					}

					if (r != null) r.run();
				}
				else if (Pastry.this.joinReported) {
					Set<IDAddressPair> nodesToBeNotified = new HashSet<IDAddressPair>();

					// notify reported nodes
					for (IDAddressPair p: nodes) {
						nodesToBeNotified.add(p);
					}

					if (leafSetArray != null) {
						for (IDAddressPair p: leafSetArray) {
							nodesToBeNotified.add(p);
						}
					}

					Pastry.this.sendJoinedMessage(nodesToBeNotified);
				}

				return null;
			}
		};
		runtime.addMessageHandler(UpdateRoutingTableMessage.class, handler);

		// JOINED
		handler = new MessageHandler() {
			public Message process(Message msg) {
				IDAddressPair src = (IDAddressPair)msg.getSource();
				routingTable.merge(src);
				if (leafSet != null)
					leafSet.merge(src);

				return null;
			}
		};
		runtime.addMessageHandler(JoinedMessage.class, handler);

		// REQ_LEAF_SET
		handler = new MessageHandler() {
			public Message process(Message msg) {
				IDAddressPair[] leafSetArray = null;
				if (leafSet != null)
					leafSetArray = leafSet.toArray();

				Message repMsg = new RepLeafSetMessage(leafSetArray);

				return repMsg;
			}
		};
		runtime.addMessageHandler(ReqLeafSetMessage.class, handler);

		// REQ_ROUTING_TABLE_ROW
		handler = new MessageHandler() {
			public Message process(Message msg) {
				int rowIndex = ((ReqRoutingTableRowMessage)msg).rowIndex;
				RoutingTableRow receivedRow = ((ReqRoutingTableRowMessage)msg).row;

				RoutingTableRow row = routingTable.getRow(rowIndex);
				if (!row.isEmpty())
					row = new RoutingTableRow(row);	// copy before altered by the following merge()
				else
					row = null;

				Message repMsg = new RepRoutingTableRowMessage(row);

				routingTable.merge(receivedRow);

				return repMsg;
			}
		};
		runtime.addMessageHandler(ReqRoutingTableRowMessage.class, handler);
	}

	private void sendJoinedMessage(Collection<IDAddressPair> c) {
		// Note: Pastry paper requires sending "resulting state to each of the nodes
		// found in its neighborhood set, leaf set, and routing table",
		// but the state can be large and is not suitable to send over network.
		// Instead, notify each node of appearance of this node by sending a message.

		Message reqMsg = new JoinedMessage();

		for (IDAddressPair node: c) {
			logger.log(Level.INFO, selfIDAddress.getAddress() + " sends a JOINED msg to " + node.getAddress());

			try {
				sender.send(node.getAddress(), reqMsg);
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "An IOException thrown while sending JOINED message.", e);
			}
		}
	}

	private final class RoutingTableMaintainer implements Runnable {
		private int rowIndex = -1;
		private int updateLeafSetFreq = config.getUpdateLeafSetFreq();

		public void run() {
			try {
				// initial sleep
				if (!config.getUseTimerInsteadOfThread()) {
					Thread.sleep(config.getRoutingTableMaintenanceInterval());
				}

				while (true) {
					if (++rowIndex >= idSizeInDigit) rowIndex = 0;

					// check if stopped or suspended
					synchronized (Pastry.this) {
						if (stopped || suspended) {
							Pastry.this.routingTableMaintainer = null;
							Pastry.this.routingTableMaintainerThread = null;
							break;
						}
					}

					// find a target
					RoutingTableRow row = null;
					IDAddressPair target = null;

					find_target:
					for (int i = rowIndex; i < idSizeInDigit; i++) {
						row = routingTable.getRow(rowIndex);
						if (row.isEmpty()) continue;

						int colCandidate = random.nextInt(1 << digitSize);
						target = null;

						for (int j = 0; j < (1 << digitSize); j++) {
							int index = (colCandidate + j) % (1 << digitSize);
							target = row.get(index);

							if (selfIDAddress.equals(target)) target = null;		// ignore self

							if (target != null) break find_target;
						}
					}

					if (target == null) {
						// no node found and try next row
						continue;
					}

					// send a request
					Message msg = new ReqRoutingTableRowMessage(rowIndex, row);
					Message repMsg = null;
					try {
						repMsg = sender.sendAndReceive(target.getAddress(), msg);
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "Failed to send or receive a REQ/REP_ROUTING_TABLE_ROW message.", e);
						fail(target);
						continue;
					}

					row = ((RepRoutingTableRowMessage)repMsg).row;

					logger.log(Level.INFO, selfIDAddress.getAddress()
							+ " received REP_ROUTING_TABLE_ROW: " + (row == null ? "(null)" : row));

					if (row != null) {
						routingTable.merge(row);

						// merge nodes in the row into leaf set
						// processes not described in the paper
						if (leafSet != null) {
							for (IDAddressPair node: row.getAllNodes()) {
								leafSet.merge(node);
							}
						}
					}

					// check and update leaf set
					// note: this process is not described in any paper
					if (--this.updateLeafSetFreq <= 0) {
						this.updateLeafSetFreq = config.getUpdateLeafSetFreq();	// reset

						Pastry.this.updateLeafSet();
					}

					// sleep
					this.sleep();

					if (config.getUseTimerInsteadOfThread()) return;
				}	// while (true)
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "RoutingTableMainainer interrupted and die.", e);
			}
		}

		private void sleep() throws InterruptedException {
			// sleep for 15 - 45 sec (in default)
			long interval = config.getRoutingTableMaintenanceInterval();
			double playRatio = config.getRoutingTableMaintenanceIntervalPlayRatio();
			double pauseRatio = 1.0 - playRatio + (playRatio * 2.0 * random.nextDouble());

			long sleepPeriod = (long)(interval * pauseRatio);
			if (config.getUseTimerInsteadOfThread()) {
				timer.schedule(this, Timer.currentTimeMillis() + sleepPeriod,
						true /*isDaemon*/, true /*executeConcurrently*/);
			}
			else {
				Thread.sleep(sleepPeriod);
			}
		}
	}
}

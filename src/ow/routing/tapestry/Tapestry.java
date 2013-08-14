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

package ow.routing.tapestry;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.ExtendedMessageHandler;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingService;
import ow.routing.plaxton.Plaxton;
import ow.routing.plaxton.RoutingTableRow;
import ow.routing.tapestry.message.MulticastAckMessage;
import ow.routing.tapestry.message.MulticastJoiningNodeMessage;
import ow.routing.tapestry.message.NotifyJoiningNodeMessage;
import ow.routing.tapestry.message.UpdateRoutingTableMessage;

public final class Tapestry extends Plaxton {
	// messages
	private final Message notifyJoiningNodeMessage;
	private final Message multicastAckMessage;

	private TapestryConfiguration config;

	protected Tapestry(RoutingAlgorithmConfiguration conf, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(conf, routingSvc);

		this.config = (TapestryConfiguration)conf;

		// prepare messages
		this.notifyJoiningNodeMessage = new NotifyJoiningNodeMessage();
		this.multicastAckMessage = new MulticastAckMessage();

		// initialize message handlers
		this.prepareHandlers();
	}

	public BigInteger distance(ID to, ID from) {
		int nMatchBits, nMatchDigits;

		if (to.equals(from)) {
			return BigInteger.ZERO;
		}

		nMatchBits = ID.matchLengthFromMSB(to, from);
		nMatchDigits = nMatchBits / digitSize;

		BigInteger distance = BigInteger.ZERO;
		for (int i = nMatchDigits; i < idSizeInDigit; i++) {
			int toDigit = getDigit(to, i);
			int fromDigit = getDigit(from, i);

			int digitDistance = this.digitDistanceInTapestry(toDigit, fromDigit);

			BigInteger digitDistanceBigInteger =
				BigInteger.valueOf(digitDistance).shiftLeft((idSizeInDigit - 1 - i) * digitSize); 
			distance = distance.add(digitDistanceBigInteger);
		}

		return distance;
	}

	private int digitDistanceInTapestry(int toDigit, int fromDigit) {
		int distance;

		distance = fromDigit - toDigit;
		if (distance < 0) {
			distance += (1 << this.digitSize);
		}

		return distance;
	}

	protected List<IDAddressPair> traverseDownward(int rowIndex, int startingCol, ID target, int maxNum) {
		List<IDAddressPair> results = new ArrayList<IDAddressPair>();

		traverseDownward(results, rowIndex, startingCol, target, maxNum);
			// results include this node itself

		return results;
	}

	private void traverseDownward(List<IDAddressPair> results,
			int rowIndex, int startingCol, ID target, int maxNum) {

		// downward traversal
		RoutingTableRow row = routingTable.getRow(rowIndex);
		int rowSize = row.size();

		for (int i = 0; i < rowSize; i++) {
			int colIndex = (startingCol + i) % rowSize;
			IDAddressPair entry = row.get(colIndex);
//System.out.print("[" + Integer.toHexString(rowIndex) + ":" + Integer.toHexString(colIndex) + ":" + (entry == null ? "null" : entry.getAddress().getHostAddress()) + "]");
			if (entry != null) {
				if (entry.equals(selfIDAddress)) {
					if (rowIndex + 1 < idSizeInDigit) {
						traverseDownward(results, rowIndex + 1, colIndex, target, maxNum - results.size());
							// recursive call
						if (results.size() >= maxNum) break;
						continue;
					}
				}

				results.add(entry);
				if (results.size() >= maxNum) break;
			}
		}
//System.out.println();
	}

	protected List<IDAddressPair> traverseUpward(int rowIndex, int maxNum) {
		List<IDAddressPair> results = new ArrayList<IDAddressPair>();
		ID selfID = selfIDAddress.getID();

		outer:
		for (int i = rowIndex - 1; i >= 0; i--) {
			RoutingTableRow row = routingTable.getRow(i);
			int rowSize = row.size();
			int digit = getDigit(selfID, i);

			for (int j = 1; j < rowSize; j++) {
				int colIndex = (digit + j) % rowSize;
				IDAddressPair entry = row.get(colIndex);
				if (entry != null) {
					results.add(entry);
					if (results.size() >= maxNum) break outer;
				}
			}
		}

		return results;
	}

	public void join(IDAddressPair joiningNode, IDAddressPair lastHop, boolean isFinalNode) {
//System.out.println("join " + (lastHop == null ? "null":lastHop.getAddress())
//+ " -> " + selfIDAddress.getAddress()
//+ " join: " + (joiningNode == null ? "null":joiningNode.getAddress()) + " final: " + isFinalHop);
		super.join(joiningNode, lastHop, isFinalNode);

		if (lastHop == null) {	// this node is the joining node
			startJoining();
		}

		if (!isFinalNode) return;

		// wait for this node receiving routing table
		waitForJoinCompletion();

		int nMatchBits = ID.matchLengthFromMSB(selfIDAddress.getID(), joiningNode.getID());
		int nMatchDigits = nMatchBits / this.digitSize;

		this.sendAcknowledgedMulticast(joiningNode.getID(), nMatchDigits, joiningNode);

		// send the routing table to the joining node
		int nRowsToBeSent = nMatchDigits + 1;
		if (nRowsToBeSent > idSizeInDigit) nRowsToBeSent = idSizeInDigit;

		Set<IDAddressPair> nodeSet = new HashSet<IDAddressPair>();
		for (int i = 0; i < nRowsToBeSent; i++) {
			RoutingTableRow row = routingTable.getRow(i);
			nodeSet.addAll(row.getAllNodes());
		}
		nodeSet.remove(joiningNode);	// the receiver itself is unnecessary
		nodeSet.remove(selfIDAddress);	// this is compensated by the receiver

		IDAddressPair[] nodes = new IDAddressPair[nodeSet.size()];
		nodeSet.toArray(nodes);

		Message reqMsg = new UpdateRoutingTableMessage(nodes);
		try {
			sender.send(joiningNode.getAddress(), reqMsg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a UPDATE_ROUNTING_TABLE message: " + joiningNode.getAddress(), e);

			fail(joiningNode);
		}

		// add the joining node to the routing table
		routingTable.merge(joiningNode);
	}

	private void sendAcknowledgedMulticast(ID prefix, int prefixLen, IDAddressPair joiningNode) {
		logger.log(Level.INFO, "On " + selfIDAddress.getAddress()
				+ ", sendAck'edMulticast() called: prefixLen: " + prefixLen
				+ ", joining node: " + joiningNode.getAddress() + ".");
//System.out.println("On " + selfIDAddress.getAddress()
//+ ", sendAck'edMulticast() called: prefixLen: " + prefixLen
//+ ", joining node: " + joiningNode.getAddress() + ".");
//System.out.flush();

		for (; prefixLen < idSizeInDigit; prefixLen++) {
			// send Acknowledged Multicast messages
			RoutingTableRow row = routingTable.getRow(prefixLen);

			if (row.isEmpty()) continue;

			for (int i = 0; i < (1 << digitSize); i++) {
				IDAddressPair childOnMulticastTree = row.get(i);
				if (childOnMulticastTree == null ||
					childOnMulticastTree.equals(selfIDAddress) ||
					childOnMulticastTree.equals(joiningNode)) continue;

				ID childPrefix = setDigit(prefix, prefixLen, i);
				Message reqMsg = new MulticastJoiningNodeMessage(
						childPrefix, prefixLen + 1, joiningNode);

				boolean succeedToSend = true;
				try {
					logger.log(Level.INFO, "send ack'd multicast from "
							+ selfIDAddress.getAddress() + " to " + childOnMulticastTree.getAddress()
							+ " with prefix len " + prefixLen);

					if (this.config.getReturnAckInAcknowledgedMulticast()) {
						Message repMsg = sender.sendAndReceive(childOnMulticastTree.getAddress(), reqMsg);
						if (!(repMsg instanceof MulticastAckMessage)) {
							succeedToSend = false;
						}
					}
					else {
						sender.send(childOnMulticastTree.getAddress(), reqMsg);
					}
				}
				catch (IOException e) {
					succeedToSend = false;
					logger.log(Level.WARNING, "Failed to send an ack'ed multicast message: " + childOnMulticastTree.getAddress(), e);
				}

				if (!succeedToSend)
					fail(childOnMulticastTree);
			}
		}
	}

	protected void prepareHandlers() {
		super.prepareHandlers();

		MessageHandler handler;

		// MULTICAST_JOINING_NODE
		handler = new AcknowledgedMulticastMessageHandler();
		runtime.addMessageHandler(MulticastJoiningNodeMessage.class, handler);

		// UPDATE_ROUTING_TABLE
		handler = new MessageHandler() {
			public Message process(final Message msg) {
				IDAddressPair[] nodes = ((UpdateRoutingTableMessage)msg).nodes;

				logger.log(Level.INFO, "UPDATE_ROUTING_TABLE received: # of nodes: " + nodes.length
						+ " on " + selfIDAddress.getAddress() + " from " + ((IDAddressPair)msg.getSource()).getAddress());

				routingTable.merge((IDAddressPair)msg.getSource());
				for (IDAddressPair p: nodes) {
					routingTable.merge(p);
				}
//System.out.println("UPDATE_ROUTING_TABLE received: # of nodes: " + nodes.length + " on " + selfIDAddress.getAddress() + " from " + msg.getSource().getAddress());
//System.out.println(Tapestry.this.getRoutingTableString(0));

				finishJoining();

				// send NOTIFY_JOINING_NODE messages to nodes in the received table
				for (IDAddressPair node: nodes) {
					if (!node.equals(selfIDAddress)) {
						logger.log(Level.INFO, selfIDAddress.getAddress() + " send a NOTIFY_JOINING msg to " + node.getAddress());

						boolean notifySucceeded = true;
						try {
							Message notifyMsg = Tapestry.this.notifyJoiningNodeMessage;
							sender.send(node.getAddress(), notifyMsg);
						}
						catch (IOException e) {
							notifySucceeded = false;
							logger.log(Level.WARNING, "An IOException thrown while sending NOTIFY_JOINING_NODE.", e);
						}

						if (!notifySucceeded) {
							forget(node);
						}
					}
				}

				return null;
			}
		};
		runtime.addMessageHandler(UpdateRoutingTableMessage.class, handler);

		// NOTIFY_JOINING_NODE
		handler = new MessageHandler() {
			public Message process(final Message msg) {
				routingTable.merge((IDAddressPair)msg.getSource());

				return null;
			}
		};
		runtime.addMessageHandler(NotifyJoiningNodeMessage.class, handler);
	}

	private class AcknowledgedMulticastMessageHandler implements ExtendedMessageHandler {
		public Message process(final Message msg) {
			// reply
			if (config.getReturnAckInAcknowledgedMulticast()) {
				return Tapestry.this.multicastAckMessage;
			}
			else {
				return null;
			}
		}

		public void postProcess(final Message msg) {
			final ID prefix = ((MulticastJoiningNodeMessage)msg).prefix;
			final int prefixLen = ((MulticastJoiningNodeMessage)msg).prefixLength;
			final IDAddressPair joiningNode = ((MulticastJoiningNodeMessage)msg).joiningNode;

//			Runnable r = new Runnable() {
//				public void run() {
//					try { this.process(); }
//					catch (Throwable e) {
//						logger.log(Level.WARNING, "A handler for MULTICAST_JOINING_NODE threw an Exception.", e);
//					}
//				}
//
//				private void process() {
					// forward the message to children
					sendAcknowledgedMulticast(prefix, prefixLen, joiningNode);

					// notify the routing algorithm of the joining node
					routingTable.merge(joiningNode);

					// notify the routing algorithm of the sender of the message
					// processes not described in the paper
					routingTable.merge((IDAddressPair)msg.getSource());
//				}
//			};
//
//			Thread t = new Thread(r);
//			t.setName("A handler for MULTICAST_JOINING_NODE message");
//			t.setDaemon(true);
//			t.start();
		}
	}

	//
	// Join status management
	//

	private enum JoinStatus { NOT_YET, JOINING, COMPLETED }
	private JoinStatus joinStatus = JoinStatus.NOT_YET;
	private Object joinCompletedLock = new Object();

	private void startJoining() {
		synchronized (this.joinCompletedLock) {
			if (this.joinStatus == JoinStatus.NOT_YET)
				this.joinStatus = JoinStatus.JOINING;
		}
	}

	private void finishJoining() {
		synchronized (this.joinCompletedLock) {
			this.joinStatus = JoinStatus.COMPLETED;
			joinCompletedLock.notifyAll();
		}
	}

	private void waitForJoinCompletion() {
		synchronized (this.joinCompletedLock) {
			if (this.joinStatus == JoinStatus.NOT_YET) {
				this.joinStatus = JoinStatus.COMPLETED;
				// On the 1st node, waitForJoinCompletion() is called before startJoining() called.
				// In that case, joinStatus should be set to COMPLETED here.
			}

			// wait for join completion
			if (this.joinStatus != JoinStatus.COMPLETED) {
				try {
					this.joinCompletedLock.wait(this.config.getWaitingTimeForJoinCompletes());
				}
				catch (InterruptedException e) {
					logger.log(Level.WARNING, "Interruted when waiting for this node itself completes the join process.", e);
				}
			}
		}

		if (this.joinStatus != JoinStatus.COMPLETED) {
			logger.log(Level.WARNING, "Joining status of " + selfIDAddress.getAddress() + " could not become COMPLETED.");
		}
	}
}

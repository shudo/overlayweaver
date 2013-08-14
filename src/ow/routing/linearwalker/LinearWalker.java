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

package ow.routing.linearwalker;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedFromSrcIDComparator;
import ow.id.comparator.AlgoBasedTowardTargetIDComparator;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingService;
import ow.routing.impl.AbstractRoutingAlgorithm;
import ow.routing.linearwalker.message.RepSuccAndPredMessage;
import ow.routing.linearwalker.message.ReqSuccAndPredMessage;
import ow.util.HTMLUtil;
import ow.util.Timer;

/**
 * An implementation of Consistent Hashing which tracks nodes linearly.
 * This is the base of Chord implementations, but works independently from Chord.
 */
public class LinearWalker extends AbstractRoutingAlgorithm {
	protected LinearWalkerConfiguration config;

	protected boolean stopped = false;
	protected boolean suspended = true;	// a created node is suspended.

	// constants
	protected final int idSizeInBit;
	protected final BigInteger sizeOfIDSpace;

	// routing table
	protected final SuccessorList successorList;
	protected IDAddressPair predecessor;

	// comparators
	protected Comparator<ID> towardSelfComparator;
	protected Comparator<ID> fromSelfComparator;

	// daemons
	private Stabilizer stabilizer;
	private Thread stabilizerThread = null;

	protected LinearWalker(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		try {
			this.config = (LinearWalkerConfiguration)config;
		}
		catch (ClassCastException e) {
			throw new InvalidAlgorithmParameterException("The given config is not ConsistentHashingConfiguration.");
		}

		// prepare constants
		this.idSizeInBit = this.config.getIDSizeInByte() * 8;
		this.sizeOfIDSpace = BigInteger.ONE.shiftLeft(this.idSizeInBit);

		this.towardSelfComparator =
			new AlgoBasedTowardTargetIDComparator(this, selfIDAddress.getID());
		this.fromSelfComparator =
			new AlgoBasedFromSrcIDComparator(this, selfIDAddress.getID());

		// initialize routing table
		this.successorList = new SuccessorList(
				this, selfIDAddress, this.config.getSuccessorListLength());
		this.predecessor = selfIDAddress;

		// initialize message handlers
		this.prepareHandlers();

		// does not invoke a stabilizer
		//this.startStabilizer();
	}

	private synchronized void startStabilizer() {
		if (this.config.getAggressiveJoiningMode()) return;

		if (this.stabilizer != null) return;	// to avoid multiple invocations

		this.stabilizer = new Stabilizer();

		if (this.config.getUseTimerInsteadOfThread()) {
			timer.schedule(this.stabilizer, Timer.currentTimeMillis(),
					true /*isDaemon*/, true /*executeConcurrently*/);
		}
		else if (this.stabilizerThread == null){
			this.stabilizerThread = new Thread(this.stabilizer);
			this.stabilizerThread.setName("Stabilizer on " + selfIDAddress.getAddress());
			this.stabilizerThread.setDaemon(true);
			this.stabilizerThread.start();
		}
	}

	private synchronized void stopStabilizer() {
		if (this.stabilizerThread != null) {
			this.stabilizerThread.interrupt();
			this.stabilizerThread = null;
		}
	}

	public synchronized void reset() {
		this.successorList.clear();
		this.predecessor = selfIDAddress;
	}

	public synchronized void stop() {
		this.stopped = true;
		this.stopStabilizer();

		// TODO: transfer indices
	}

	public synchronized void suspend() {
		this.suspended = true;
		this.stopStabilizer();
	}

	public synchronized void resume() {
		this.suspended = false;
		this.startStabilizer();
	}

	public RoutingContext initialRoutingContext(ID targetID) {
		return new LinearWalkerRoutingContext();
	}

	public BigInteger distance(ID to, ID from) {
		BigInteger toInt = to.toBigInteger();
		BigInteger fromInt = from.toBigInteger();

		BigInteger distance = toInt.subtract(fromInt);	// distance = to - from
		if (distance.compareTo(BigInteger.ZERO) <= 0) {
			distance = distance.add(this.sizeOfIDSpace);
				// distance = 2 ^ # of bit if to and from are the same ID
		}

		return distance;	// 1 <= distance <= 2 ^ # of bit
	}

	public IDAddressPair[] nextHopCandidates(ID target, ID lastHop, boolean joining,
			int maxNumber, RoutingContext cxt) {	// find_predecessor()
		LinearWalkerRoutingContext context = (LinearWalkerRoutingContext)cxt;

		// judge whether the routing is in last phase
		if (!context.routeToPredecessorOfTarget() && !context.inLastPhase()) {
			IDAddressPair succ;
			if (true) {	// optimized a bit
				succ = this.successorList.lastOtherNode();
			}
			else {			// basic
				succ = this.successorList.first();
				if (selfIDAddress.equals(succ)) succ = null;
			}

			if ((!joining && selfIDAddress.getID().equals(target)) ||
				 (succ != null && this.fromSelfComparator.compare(target, succ.getID()) <= 0)) {
				context.setLastPhase();
			}
//StringBuilder sb = new StringBuilder();
//sb.append("nextHopCands: " + target.toString(-1) + "\n");
//sb.append("  on " + selfIDAddress.toString(-1) + "\n");
//sb.append("  (lastHop: " + (lastHop != null ? lastHop.toString(-1) : "null") + ")\n");
//if (lastHop != null && this.towardSelfComparator.compare(lastHop, target) >= 0)
//sb.append("    (cover)\n");
//sb.append("  succ:    " + (succ != null ? succ.toString(-1) : "null") + "\n");
//if (succ != null && this.fromSelfComparator.compare(target, succ.getID()) <= 0)
//sb.append("    cover\n");
//sb.append("  last phase: " + context.inLastPhase() + "\n");
//System.out.print(sb.toString());
		}

		// calculate closest nodes
		SortedSet<IDAddressPair> closestSet = this.successorList.closestTo(target, true);
		if (this.predecessor != null)
			closestSet.add(this.predecessor);

		this.addToNextHopCandidatesSet(closestSet);	// call LinearWalker or AbstractChord

		if (joining) {
			for (Iterator<IDAddressPair> ite = closestSet.iterator(); ite.hasNext();) {
				IDAddressPair p = ite.next();
				if (target.equals(p.getID())) {
					ite.remove();
				}
			}
		}

		int len = Math.min(maxNumber, closestSet.size());
		IDAddressPair[] closestArray = new IDAddressPair[len];

		if (len > 0) {
			int i = 0;
			if (context.inLastPhase()) {
				closestArray[i++] = closestSet.last();
			}
			for (IDAddressPair p: closestSet) {
				if (i >= len) break;
				closestArray[i++] = p;
			}
		}

//System.out.print("  result:");
//for (IDAddressPair p: closestArray) System.out.print(" " + p.toString(-1));
//System.out.println();
		return closestArray;
	}

	protected void addToNextHopCandidatesSet(SortedSet<IDAddressPair> nextHopCandidatesSet) {}	// overridden by AbstractChord

	public IDAddressPair[] responsibleNodeCandidates(ID target, int maxNumber) {
		return this.successorList.responsibleNodeCandidates(target, maxNumber, this.predecessor);
	}

	public void join(IDAddressPair[] neighbors /* are to be successor list */) {
		this.successorList.addAll(neighbors);

		if (this.config.getAggressiveJoiningMode()) {
			this.requestSuccAndPred(true, false);
		}
		else {
			// get to know correct successor and predecessor when joining
			this.requestSuccAndPred(true, true);
			this.requestSuccAndPred(false, true);
		}

		this.resume();
	}

	public void touch(IDAddressPair from) {
		if (this.config.getUpdateRoutingTableByAllCommunications()) {
			this.successorList.add(from);

			synchronized (this) {
				if (towardSelfComparator.compare(from.getID(), this.predecessor.getID()) < 0)
					this.predecessor = from;
			}
		}
	}

	public void forget(IDAddressPair failedNode) {
		// successor list
		this.successorList.remove(failedNode);

		// predecessor
		synchronized (this) {
			if (this.predecessor != null && this.predecessor.equals(failedNode))
				this.predecessor = selfIDAddress;
		}
	}

	public boolean toReplace(IDAddressPair existingEntry, IDAddressPair newEntry) {
		// distance from this node is smaller -> better
		if (this.fromSelfComparator.compare(existingEntry.getID(), newEntry.getID()) <= 0)
			return false;
		else
			return true;
	}

	public void join(IDAddressPair joiningNode, IDAddressPair lastHop, boolean isFinalNode) {
		ID joiningNodeID = (joiningNode != null ? joiningNode.getID() : null);

		if (!this.selfIDAddress.getID().equals(joiningNodeID)) {
			// joining node is not this node itself
			this.resume();
		}
	}

	public String getRoutingTableString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append("predecessor:\n ");
		sb.append(this.predecessor.toString(verboseLevel));
		sb.append("\n");

		sb.append("successor list: [");
		IDAddressPair[] slArray = this.successorList.toArray();
		for (IDAddressPair entry: slArray) {
			sb.append("\n ");
			sb.append(entry.toString(verboseLevel));
		}
		sb.append("\n]");

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();
		String url;

		sb.append("<h4>Predecessor</h4>\n");
		sb.append("<table>\n");
		if (this.predecessor != null) {
			url = HTMLUtil.convertMessagingAddressToURL(this.predecessor.getAddress());
			sb.append("<tr><td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td><td>"
					+ HTMLUtil.stringInHTML(this.predecessor.getID().toString()) + "</td></tr>\n");
		}
		else {
			sb.append("<tr><td>null</td></tr>\n");
		}
		sb.append("</table>\n");

		sb.append("<h4>Successor List</h4>\n");
		IDAddressPair[] slArray = this.successorList.toArray();
		sb.append("<table>\n");
		for (IDAddressPair entry: slArray) {
			url = HTMLUtil.convertMessagingAddressToURL(entry.getAddress());
			sb.append("<tr><td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td><td>"
					+ HTMLUtil.stringInHTML(entry.getID().toString()) + "</td></tr>\n");
		}
		sb.append("</table>\n");

		return sb.toString();
	}

	protected boolean requestSuccAndPred(boolean contactIsSuccessor, boolean traverse) {
		boolean changed = false;

		IDAddressPair contact;
		IDAddressPair succContact = null;
		IDAddressPair predContact = null;

		IDAddressPair lastSuccessor = this.successorList.first();
		IDAddressPair lastPredecessor = this.predecessor;

		if (contactIsSuccessor) contact = lastSuccessor;
		else contact = lastPredecessor;

		while (true) {
			// update contact
			if (traverse && contact == null) {
				if (succContact != null) {
					contact = succContact;
					succContact = null;
					contactIsSuccessor = true;
				}
				else if (predContact != null) {
					contact = predContact;
					predContact = null;
					contactIsSuccessor = false;
				}
			}

			if (contact == null) break;

			if (contact.getID().equals(selfIDAddress.getID())) {
				contact = null;
				continue;
			}

			Message msg = new ReqSuccAndPredMessage();
			try {
				msg = sender.sendAndReceive(contact.getAddress(), msg);
				contact = null;
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "Failed to send/receive a REQ/REP_SUCC_AND_PRED msg.", e);
				this.fail(contact);
				contact = null;

				if (contactIsSuccessor)
					contact = this.successorList.first();

				continue;
			}

			if (!(msg instanceof RepSuccAndPredMessage)) {
				// NOTREACHED
				logger.log(Level.WARNING, "Expected message is REP_SUCC_AND_PRED: " + msg.getName());

				continue;
			}

			// parse the reply
			IDAddressPair[] succs = ((RepSuccAndPredMessage)msg).successors;
			IDAddressPair pred = ((RepSuccAndPredMessage)msg).lastPredecessor;

			// update routing table
			this.successorList.addAll(succs);
			this.successorList.add(pred);

			synchronized (this) {
				if (contactIsSuccessor) {
					if (towardSelfComparator.compare(this.predecessor.getID(), pred.getID()) > 0) {
						this.predecessor = pred;
					}
				}
				else {
					for (IDAddressPair succ: succs) {
						if (towardSelfComparator.compare(this.predecessor.getID(), succ.getID()) > 0) {
							this.predecessor = succ;
						}
					}
				}
			}

			// check changes
			IDAddressPair successor = this.successorList.first();
			IDAddressPair predecessor = this.predecessor;

			if (!lastSuccessor.equals(successor)) {
				changed = true;
				succContact = successor;
			}

			if (!lastPredecessor.equals(predecessor)) {
				changed = true;
				predContact = predecessor;
			}

//StringBuilder sb = new StringBuilder();
//sb.append("reqSuccAndPred(" + (contactIsSuccessor ? "succ" : "pred") + ") on " + this.selfIDAddress.toString(-1));
//sb.append(" succ: " + lastSuccessor.toString(-1));
//if (!lastSuccessor.equals(successor))
//sb.append(" -> " + successor.toString(-1));
//sb.append(" pred: " + lastPredecessor.toString(-1));
//if (!lastPredecessor.equals(predecessor))
//sb.append(" -> " + predecessor.toString(-1));
//System.out.println(sb.toString());
			lastSuccessor = successor;
			lastPredecessor = predecessor;
		}	// while (true)

		return changed;
	}

	protected void prepareHandlers() {
		this.prepareHandlers(false);
	}

	protected void prepareHandlers(boolean ignoreReqSuccAndPredMessage) {
		MessageHandler handler;

		// REQ_SUCC_AND_PRED
		// first half of init_finger_table(n')
		if (!ignoreReqSuccAndPredMessage) {
			handler = new ReqSuccAndPredMessageHandler();
			this.runtime.addMessageHandler(ReqSuccAndPredMessage.class, handler);
		}
	}

	// MessageHandler for REQ_SUCC_AND_PRED
	public class ReqSuccAndPredMessageHandler implements MessageHandler {
		public Message process(Message msg) {
			LinearWalker.this.touch((IDAddressPair)msg.getSource());	// notify the algorithm

			IDAddressPair[] lastSuccessors = LinearWalker.this.successorList.toArrayExcludingSelf();
			IDAddressPair lastPredecessor = LinearWalker.this.predecessor;

			// update successor and predecessor
			IDAddressPair msgSrc = (IDAddressPair)msg.getSource();

			LinearWalker.this.successorList.add(msgSrc);
				// try to add the predecessor to finger table and successor list
				// these does not exist in the Figure 6

			synchronized (LinearWalker.this) {
				if (config.getAggressiveJoiningMode()) {
					LinearWalker.this.predecessor = msgSrc;
				}
				else {
					// check the received predecessor
					if (msgSrc.getID().equals(predecessor.getID()) ||	// just perf optimization
						towardSelfComparator.compare(predecessor.getID(), msgSrc.getID()) > 0) {
						LinearWalker.this.predecessor = msgSrc;
					}
				}
			}

			// reply
			Message repMsg = new RepSuccAndPredMessage(
					lastSuccessors, lastPredecessor);

			return repMsg;
		}
	}

	private final class Stabilizer implements Runnable {
		private long interval = config.getStabilizeMinInterval();
		private int updatePredecessorFreq = config.getUpdatePredecessorFreq();

		public void run() {
			try {
				while (true) {
					synchronized (LinearWalker.this) {
						if (stopped || suspended) {
							LinearWalker.this.stabilizer = null;
							LinearWalker.this.stabilizerThread = null;
							break;
						}
					}

					boolean resetSleepPeriod = false;
					boolean changed;

					// check and update successor
					changed = requestSuccAndPred(true, true);
					resetSleepPeriod |= changed;

					// check and update predecessor
					// note: a node keeps an obsolete predecessor without such confirmation
					if (--this.updatePredecessorFreq <= 0) {
						this.updatePredecessorFreq = config.getUpdatePredecessorFreq();	// reset

						changed = requestSuccAndPred(false, true);
						resetSleepPeriod |= changed;
					}

					// sleep
					this.sleep(resetSleepPeriod);

					if (config.getUseTimerInsteadOfThread()) return;
				}	// while (true)
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "Stabilizer interrupted and die.", e);
			}
		}

		private void sleep(boolean resetSleepPeriod) throws InterruptedException {
			// determine the next interval
			if (resetSleepPeriod) {
				this.interval = config.getStabilizeMinInterval();
			}
			else {
				this.interval <<= 1;
				if (this.interval > config.getStabilizeMaxInterval()) {
					this.interval = config.getStabilizeMaxInterval();
				}
			}

			double playRatio = config.getStabilizeIntervalPlayRatio();
			double intervalRatio = 1.0 - playRatio + (playRatio * 2.0 * random.nextDouble());

			// sleep
			long sleepPeriod = (long)(this.interval * intervalRatio);

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

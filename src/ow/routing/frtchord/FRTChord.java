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

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Comparator;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedFromSrcIDComparator;
import ow.messaging.Message;
import ow.messaging.MessageDirectory;
import ow.messaging.MessageHandler;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingService;
import ow.routing.frtchord.RoutingTable.Entry;
import ow.routing.frtchord.message.RepStickyNodesMessage;
import ow.routing.frtchord.message.ReqStickyNodesMessage;
import ow.routing.impl.AbstractRoutingAlgorithm;
import ow.util.HTMLUtil;
import ow.util.Timer;

/**
 * A RoutingAlgorithm implementing FRT-Chord.
 */
public final class FRTChord extends AbstractRoutingAlgorithm  {
	protected final FRTChordConfiguration config;

	// constants
	protected final BigInteger sizeOfIDSpace;
	protected final int idSizeInBit;

	// flag
	protected boolean stopped = false;
	protected boolean suspended = true;	// a created node is suspended.

	// routing table
	private final RoutingTable routingTable;

	private Comparator<ID> fromSelfComparator;

	// daemon
	private Stabilizer stabilizer;
	private Thread stabilizerThread = null;

	protected FRTChord(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		try {
			this.config = (FRTChordConfiguration)config;
		}
		catch (ClassCastException e) {
			throw new InvalidAlgorithmParameterException("The given config is not FRTChordConfiguration.");
		}

		// prepare constants
		this.idSizeInBit = this.config.getIDSizeInByte() * 8;
		this.sizeOfIDSpace = BigInteger.ONE.shiftLeft(this.idSizeInBit);

		this.fromSelfComparator =
			new AlgoBasedFromSrcIDComparator(this, selfIDAddress.getID());

		// prepare routing table
		this.routingTable = new RoutingTable(this, selfIDAddress);

		// initialize message handlers
		this.prepareHandlers();
	}

	private synchronized void startStabilizer() {
		if (!this.config.getDoStabilization()
				|| this.stabilizer != null) return;	// to avoid multiple invocations

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

	public void reset() { this.routingTable.clear(); }

	public synchronized void stop() {
		this.stopped = true;
		this.stopStabilizer();
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
		return new FRTChordRoutingContext();
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

	public void join(IDAddressPair[] neighbors /* are to be successor list, but including self */) {
		this.routingTable.insertAll(neighbors);

		// note: The current implementation of FRT-Chord does not perform
		// a lookup randomly-chosen IDs like Chord's fix_fingers(),
		// and then a node has few opportunity to know various other nodes.
		// Because of it, without the following line,
		// it is highly possible for a node with an optimization in responsibleNodeCandidates()
		// not to get to know the correct successor.

		// get to know correct successor and predecessor when joining
		this.requestStickyNodes(true);
		this.requestStickyNodes(false);

		this.resume();
	}

	public IDAddressPair[] nextHopCandidates(ID target, ID lastHop, boolean joining,
			int maxNumber, RoutingContext cxt) {
		FRTChordRoutingContext context = (FRTChordRoutingContext)cxt;

//StringBuilder sb = new StringBuilder();
//sb.append("nextHopCands: " + target.toString(-1) + " on " + selfIDAddress.toString(-1) + "\n");
		// judge whether the routing is in last phase
		if (!context.routeToPredecessorOfTarget() && !context.inLastPhase()) {
			IDAddressPair succ;

			if (true) {	// optimized a bit
				succ = this.routingTable.getLastStickyNode();
			}
			else {			// basic
				succ = this.routingTable.getFirstNode();
			}

			if (// the current size of routing table is less than the maximum size
				(this.config.getOneHopDHT() && this.routingTable.size() < this.config.getRoutingTableSize()) ||
				// target is covered by the last sticky node
				(!joining && selfIDAddress.getID().equals(target)) ||
				(succ != null && this.fromSelfComparator.compare(target, succ.getID()) <= 0)) {
				context.setLastPhase();
			}
//if (this.config.getOneHopDHT()) {
//sb.append("  size: " + this.routingTable.size() + " (< " + this.config.getRoutingTableSize() + ")\n");
//if (this.routingTable.size() < this.config.getRoutingTableSize())
//sb.append("    cover\n");
//}
//sb.append("  succ: " + (succ != null ? succ.toString(-1) : "null") + "\n");
//if (succ != null && this.fromSelfComparator.compare(target, succ.getID()) <= 0)
//sb.append("    cover\n");
//sb.append("  last phase: " + context.inLastPhase() + "\n");
		}

		IDAddressPair[] nextHopCands = this.routingTable.closestTo(target, joining, context.inLastPhase());
//sb.append("  next hop:");
//for (IDAddressPair p: nextHopCands) sb.append(" ").append(p.toString(-1));
//sb.append("\n");
//System.out.print(sb.toString());
		return nextHopCands;
	}

	public IDAddressPair[] responsibleNodeCandidates(ID target, int maxNumber) {
		return this.routingTable.responsibleNodeCandidates(target, maxNumber);
	}

	public void touch(IDAddressPair from) {
		this.routingTable.insert(from);
	}

	public void forget(IDAddressPair node) {
		this.routingTable.remove(node);
	}

	public boolean toReplace(IDAddressPair existingEntry, IDAddressPair newEntry) {
		/* NOTREACHED */
		return false;
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

		sb.append("[");
		Entry[] rtArray = this.routingTable.toEntryArray();
		for (Entry entry: rtArray) {
			sb.append("\n ");
			sb.append(entry.toString(verboseLevel));
		}
		sb.append("\n]");

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();
		String url;

		Entry[] rtArray = this.routingTable.toEntryArray();
		sb.append("<table>\n");
		for (Entry entry: rtArray) {
			IDAddressPair idAddr = entry.getIDAddressPair();
			url = HTMLUtil.convertMessagingAddressToURL(idAddr.getAddress());
			sb.append("<tr><td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td><td>"
					+ HTMLUtil.stringInHTML(entry.getIDAddressPair().getID().toString()) + "</td><td>"
					+ HTMLUtil.stringInHTML(Double.toString(entry.getNormalizedID()))+ "</td></tr>\n");
		}
		sb.append("</table>\n");

		return sb.toString();
	}

	private boolean requestStickyNodes(boolean contactIsSuccessor) {
		boolean changed = false;

		IDAddressPair contact;
		IDAddressPair succContact = null;
		IDAddressPair predContact = null;

		IDAddressPair lastSuccessor = this.routingTable.getFirstNode();
		IDAddressPair lastPredecessor = this.routingTable.getLastNode();

		if (contactIsSuccessor) contact = lastSuccessor;
		else contact = lastPredecessor;

		while (true) {
			// update contact
			if (contact == null) {
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

			Message msg = new ReqStickyNodesMessage();
			try {
				msg = sender.sendAndReceive(contact.getAddress(), msg);	// throws IOException
				contact = null;
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "Failed to send/receive a REQ/REP_STICKY_NODES msg.", e);
				this.fail(contact);
				contact = null;

				if (contactIsSuccessor)
					succContact = lastSuccessor = this.routingTable.getFirstNode();
				else
					predContact = lastPredecessor = this.routingTable.getLastNode();

				continue;
			}

			if (!(msg instanceof RepStickyNodesMessage)) {
				// NOTREACHED
				String receivedMsgName = MessageDirectory.getName(msg.getTag());
				logger.log(Level.WARNING, "Expected message is REP_STICKY_NODES: " + receivedMsgName);

				continue;
			}

			// parse the reply
			IDAddressPair[] stickyNodes = ((RepStickyNodesMessage)msg).stickyNodes;

			// update routing table
			this.routingTable.insertAll(stickyNodes);

			// check changes
			IDAddressPair successor = this.routingTable.getFirstNode();
			IDAddressPair predecessor = this.routingTable.getLastNode();

			if (!lastSuccessor.equals(successor)) {
				changed = true;
				succContact = successor;
			}

			if (!lastPredecessor.equals(predecessor)) {
				changed = true;
				predContact = predecessor;
			}

//StringBuilder sb = new StringBuilder();
//sb.append("reqStkyN(" + (contactIsSuccessor ? "succ" : "pred") + ") on " + this.selfIDAddress.toString(-1));
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

	public void prepareHandlers() {
		MessageHandler handler;

		// REQ_STICKY_NODES
		handler = new MessageHandler() {
			public Message process(Message msg) {
				// update routing table
				FRTChord.this.routingTable.insert((IDAddressPair)msg.getSource());

				// reply
				return new RepStickyNodesMessage(
						FRTChord.this.routingTable.getStickyNodes());
			}
		};
		this.runtime.addMessageHandler(ReqStickyNodesMessage.class, handler);
	}

	private final class Stabilizer implements Runnable {
		private long interval = config.getStabilizeMinInterval();
		private int updateFartherStickyNodeFreq = config.getUpdateFartherStickyNodeFreq();

		public void run() {
			try {
				while (true) {
					synchronized (FRTChord.this) {
						if (stopped || suspended) {
							FRTChord.this.stabilizer = null;
							FRTChord.this.stabilizerThread = null;
							break;
						}
					}

					boolean resetSleepPeriod = false;

					// check and update successor
					resetSleepPeriod |= requestStickyNodes(true);

					// check and update predecessor
					// note: a node keeps an obsolete predecessor without such confirmation
					if (--this.updateFartherStickyNodeFreq <= 0) {
						this.updateFartherStickyNodeFreq = config.getUpdateFartherStickyNodeFreq();	// reset

						resetSleepPeriod |= requestStickyNodes(false);
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

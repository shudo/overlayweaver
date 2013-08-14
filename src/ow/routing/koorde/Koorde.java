/*
 * Copyright 2006-2011 Kazuyuki Shudo, and contributors.
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

package ow.routing.koorde;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedFromSrcIDComparator;
import ow.id.comparator.AlgoBasedTowardTargetIDComparator;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;
import ow.routing.koorde.message.RepPredecessorMessage;
import ow.routing.koorde.message.ReqPredecessorMessage;
import ow.routing.linearwalker.LinearWalker;
import ow.util.HTMLUtil;
import ow.util.Timer;

public final class Koorde extends LinearWalker {
	// messages
	private final Message reqPredecessorMessage;

	private KoordeConfiguration config;

	// configuration and edge
	private final int digitBits;	// k = 2 ^ digitBits
	private ID km;
	private IDAddressPair[] edges;
	private int numEdges;

	// daemon
	private EdgeFixer edgeFixer = null;
	private Thread edgeFixerThread = null;

	protected Koorde(RoutingAlgorithmConfiguration config, RoutingService routingSvc) throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		try {
			this.config = (KoordeConfiguration)config;
		}
		catch (ClassCastException e) {
			throw new InvalidAlgorithmParameterException("The given config is not KoordeConfiguration.");
		}

		// prepare parameters
		this.digitBits = this.config.getDigitBits();
		this.km = this.selfIDAddress.getID().shiftLeft(this.digitBits);
			// km = m << log_2(k)

		// edge and backups
		this.numEdges = this.config.getNumEdges();
		this.edges = new IDAddressPair[this.numEdges];

		// prepare messages
		this.reqPredecessorMessage = new ReqPredecessorMessage();

		// does not invoke an edge maintainer
		//startEdgeFixer();
	}

	private synchronized void startEdgeFixer() {
		if (this.edgeFixer != null) return;	// to avoid multiple invocations

		this.edgeFixer = new EdgeFixer(this.config, this.km);

		if (this.config.getUseTimerInsteadOfThread()) {
			timer.schedule(this.edgeFixer, Timer.currentTimeMillis(),
					true /*isDaemon*/, true /*executeConcurrently*/);
		}
		else if (this.edgeFixerThread == null){
			this.edgeFixerThread = new Thread(this.edgeFixer);
			this.edgeFixerThread.setName("EdgeFixer on " + selfIDAddress.getAddress());
			this.edgeFixerThread.setDaemon(true);
			this.edgeFixerThread.start();
		}
	}

	private synchronized void stopEdgeFixer() {
		if (this.edgeFixer != null) {
			this.edgeFixer = null;
		}

		if (this.edgeFixerThread != null) {
			this.edgeFixerThread.interrupt();
			this.edgeFixerThread = null;
		}
	}

	public synchronized void reset() {
		super.reset();
		this.edges = new IDAddressPair[this.config.getNumEdges()];
	}

	public synchronized void stop() {
		logger.log(Level.INFO, "Koorde#stop() called.");

		super.stop();
		this.stopEdgeFixer();
	}

	public synchronized void suspend() {
		super.suspend();
		this.stopEdgeFixer();
	}

	public synchronized void resume() {
		super.resume();
		this.startEdgeFixer();
	}

	public KoordeRoutingContext initialRoutingContext(ID targetID) {
		KoordeRoutingContext cxt = new KoordeRoutingContext(this.digitBits);
//int shiftWidth = this.initializeRoutingContext(this.selfIDAddress, cxt, targetID);
//System.out.println("initialRoutingontext(" + targetID.toString(-1) + ") shift width: " + shiftWidth + " i: " + cxt.getI().toString(-1));
		return cxt;
	}

	int initializeRoutingContext(IDAddressPair selfIDAddressPair, KoordeRoutingContext cxt, ID targetID) {
		int idSize = this.config.getIDSizeInByte();
		ID selfID = selfIDAddressPair.getID();
		ID successor = this.successorList.first().getID();

		BigInteger selfIDInteger, successorInteger, targetIDInteger;

		selfIDInteger = selfID.toBigInteger();
		successorInteger = successor.toBigInteger();
		targetIDInteger = targetID.toBigInteger();

		// in case that there is no successor
		if (selfIDAddressPair.getID().equals(successor)) {
			// there is no successor
			//return new KoordeRoutingContext(targetID, selfID, this.digitBits);
			cxt.kshift = targetID;
			cxt.i = selfID;

			return 0;
		}

		// initializes comparator
		Comparator<ID> fromSelfComparator = new AlgoBasedFromSrcIDComparator(this, selfID);

		// calculates the length of common bits of m and m.successor
		int i = 0;
		for (i = 0; i < this.idSizeInBit; i++) {
			if (selfIDInteger.testBit(this.idSizeInBit - 1 - i)
					!= successorInteger.testBit(this.idSizeInBit - 1 - i)) {
				break;
			}
		}
		i /= this.digitBits;

		int iter = (this.idSizeInBit - 1) / this.digitBits + 1;
		for (; i <= iter; i++) {
			int shiftWidth = this.idSizeInBit - this.digitBits * i;
			BigInteger mask = BigInteger.ONE.shiftLeft(shiftWidth).subtract(BigInteger.ONE);
			BigInteger topBitsOfK = targetIDInteger.shiftRight(this.digitBits * i);
//System.out.println("(" + i + ")");
//System.out.println("self:  " + selfIDInteger.toString(16));
//System.out.println("mask:  " + mask.toString(16));
//System.out.println("target:" + targetID);
//System.out.println("topBit:" + topBitsOfK.toString(16));
//System.out.println("succ:  " + successor);

			BigInteger baseID;
			ID imgID, kshift;

			baseID = selfIDInteger;	// m
			imgID = ID.getID(baseID.andNot(mask).or(topBitsOfK), idSize);
			if (fromSelfComparator.compare(imgID, successor) <= 0) {	// imgID is in (selfID, successor]
				kshift = ID.getID(targetIDInteger.shiftLeft(shiftWidth), idSize);
				//return new KoordeRoutingContext(kshift, imgID, this.digitBits);
				cxt.kshift = kshift;
				cxt.i = imgID;
				return shiftWidth;
			}

			baseID = successorInteger;		// m.successor
			imgID = ID.getID(baseID.andNot(mask).or(topBitsOfK), idSize);
			if (fromSelfComparator.compare(imgID, successor) <= 0) {
				kshift = ID.getID(targetIDInteger.shiftLeft(shiftWidth), idSize);
				//return new KoordeRoutingContext(kshift, imgID, this.digitBits);
				cxt.kshift = kshift;
				cxt.i = imgID;
				return shiftWidth;
			}

			baseID = selfIDInteger.add(BigInteger.ONE.shiftLeft(shiftWidth));	// m + 1
			imgID = ID.getID(baseID.andNot(mask).or(topBitsOfK), idSize);
			if (fromSelfComparator.compare(imgID, successor) <= 0) {
				kshift = ID.getID(targetIDInteger.shiftLeft(shiftWidth), idSize);
				//return new KoordeRoutingContext(kshift, imgID, this.digitBits);
				cxt.kshift = kshift;
				cxt.i = imgID;
				return shiftWidth;
			}

			baseID = successorInteger.subtract(BigInteger.ONE.shiftLeft(shiftWidth));	// m.successor - 1
			imgID = ID.getID(baseID.andNot(mask).or(topBitsOfK), idSize);
			if (fromSelfComparator.compare(imgID, successor) <= 0) {
				kshift = ID.getID(targetIDInteger.shiftLeft(shiftWidth), idSize);
				//return new KoordeRoutingContext(kshift, imgID, this.digitBits);
				cxt.kshift = kshift;
				cxt.i = imgID;
				return shiftWidth;
			}
		}

		// could not improve imaginary ID, and start routing with self ID
		//return new KoordeRoutingContext(targetID, selfID, this.digitBits);
		cxt.kshift = targetID;
		cxt.i = selfID;

		return 0;
	}

	public IDAddressPair[] nextHopCandidates(ID target /* k */, ID lastHop, boolean joining,
			int maxNum, RoutingContext cxt) {
		IDAddressPair[] ret;
		KoordeRoutingContext context = (KoordeRoutingContext)cxt;
//StringBuilder sb = new StringBuilder();
//boolean print = true; //target.equals(IDUtility.parseID("k28", this.config.getIDSizeInByte()));
//sb.append("nextHopCands: " + target.toString(-1) + " on " + selfIDAddress.toString(-1) + "\n");
//if (context.getI() != null) sb.append("  i: " + context.getI().toString(-1) + "\n");

		ID selfID = this.selfIDAddress.getID();

		if (context.inLastPhase()) {
			Comparator<ID> toSelfComparator = new AlgoBasedTowardTargetIDComparator(this, selfID);

			if (!this.predecessor.equals(this.selfIDAddress) &&
					toSelfComparator.compare(target, this.predecessor.getID()) >= 0) {
				// predecessor is better
				ret = new IDAddressPair[1];
				ret[0] = this.predecessor;
//sb.append("  pred is better.\n");
			}
			else {
				// this node is the responsible node
				ret = new IDAddressPair[2];
				ret[0] = this.selfIDAddress;
				ret[1] = this.predecessor;
//sb.append("  I'm in charge of the target.\n");
			}

//if (print) System.out.print(sb.toString());
			return ret;
		}

		if (!context.isInitialized()) {
			int shiftWidth = this.initializeRoutingContext(this.selfIDAddress, context, target);
//sb.append("  init'ed. shift width:" + shiftWidth + "\n");
//sb.append("  i: " + context.getI().toString(-1) + "\n");
		}

		if (context.getIgnoredNode() != null) {
			this.forget(context.getIgnoredNode());
		}

		IDAddressPair succ = this.successorList.first();
		Comparator<ID> toSuccComparator = new AlgoBasedTowardTargetIDComparator(this, succ.getID());

		if (this.selfIDAddress.equals(context.getIgnoredNode())) {
//sb.append("  ignored node\n");
		}
		else {
			if (target.equals(succ.getID()) ||
					toSuccComparator.compare(selfID, target /* k */) > 0) {	// order: selfID, target, succ
				// k is in (m,successor]
				boolean inclPred = (this.predecessor != null && !this.predecessor.equals(this.selfIDAddress));
				int retSize = 1;
				if (!context.routeToPredecessorOfTarget()) {
					retSize++;
					context.setLastPhase();
				}
				if (inclPred) retSize++;
				ret = new IDAddressPair[retSize];

				int j = 0;
				if (!context.routeToPredecessorOfTarget()) ret[j++] = succ;
				ret[j++] = this.selfIDAddress;
				if (inclPred) ret[j++] = this.predecessor;

//sb.append("  I'm closest to " + target.toString(-1) + "\n");
//sb.append("    self: " + selfID.toString(-1) + "\n");
//sb.append("    succ: " + succ.getID().toString(-1) + "\n");
//if (print) System.out.print(sb.toString());
				return ret;
			}
			else if (context.getI().equals(succ.getID())
					|| toSuccComparator.compare(selfID, context.getI()) > 0) {	// order: selfID, i
				// i is in (m,successor]
				if (edges[0] == null) {
//sb.append("  no edge!\n");
				}
				else {
					context.update();
						// kshift = kshift << 1, i = i.topBit(kshift)

					ret = new IDAddressPair[edges.length];
					for (int j = 0; j < edges.length; j++) {
						ret[j] = edges[j];
					}

//sb.append("  fwd via edge: " + (edges[0] != null ? edges[0].toString(-1) : "(null)") + "\n");
//sb.append("    i   : " + context.getI().toString(-1) + "\n");
//sb.append("    succ: " + succ.getID().toString(-1) + "\n");
//if (print) System.out.print(sb.toString());
					return ret;
				}
			}
			else {
				// forward to successor
				Comparator<ID> fromSelfComparator = new AlgoBasedFromSrcIDComparator(this, selfID);
				ID cutPoint;
				if (context.isInitialized() &&
						fromSelfComparator.compare(context.getI(), target) <= 0)
					cutPoint = context.getI();
				else
					cutPoint = target;

				SortedSet<IDAddressPair> succSet = this.successorList.closestTo(cutPoint, false);
					// optimized: next hop is the closest node to i or target, not just successor

				ret = new IDAddressPair[succSet.size()];
				int j = 0;
				for (IDAddressPair p: succSet) {
					ret[j++] = p;
				}

//sb.append("  fwd to succ: " + (ret.length > 0 ? ret[0].toString(-1) : "(null)") + "\n");
//if (print) System.out.print(sb.toString());
				return ret;
			}
		}	// if (!this.selfIDAddress.equals(context.getInogredNode()))

		// search the best next hop
		context.uninitialize();

		ret = this.successorList.toArray();
		SortedMap<Integer,IDAddressPair> shiftWidthNeighborMap = new TreeMap<Integer,IDAddressPair>();

		for (int i = 0; i < ret.length; i++) {
			int shiftWidth = this.initializeRoutingContext(ret[i], context, target);
			shiftWidthNeighborMap.put(shiftWidth,ret[i]);
		}
		context.uninitialize();

		int i = ret.length - 1;
		for (IDAddressPair p: shiftWidthNeighborMap.values()) {
			ret[i--] = p;
		}

//sb.append("  fwd to: " + (ret.length > 0 ? ret[0].toString(-1) : "(null)") + "\n");
//if (print) System.out.print(sb.toString());
		return ret;
	}

	public void join(IDAddressPair[] neighbors) {
		this.successorList.addAll(neighbors);	// fixEdges() requires routing table to be constructed

		// fix edges before stabilization
		this.fixEdges(this.selfIDAddress);

		super.join(neighbors);
	}

	private boolean fixEdges(IDAddressPair ignoredNode) {
		// update an edge
		KoordeRoutingContext cxt =
			(KoordeRoutingContext)Koorde.this.initialRoutingContext(Koorde.this.km);
		cxt.setRouteToPredecessorOfTarget();
		cxt.setIgnoredNode(ignoredNode);

		RoutingResult res = null;
		try {
			res = Koorde.this.runtime.route(Koorde.this.km, cxt, 1);	// route to target's predecessor
		}
		catch (RoutingException e) {
			logger.log(Level.WARNING, "In fixEdges() routing to " + Koorde.this.km.toString() + " failed.", e);
			return false;
		}

		RoutingHop[] route = res.getRoute();

		IDAddressPair oldEdge = Koorde.this.edges[0];
		Koorde.this.edges[0] = route[route.length - 1].getIDAddressPair();

		int nEdges = edges.length;
		boolean changed =
			(edges[0] != null)
			&& (!edges[0].equals(oldEdge) || edges[nEdges - 1] == null);

		if (changed) {
			// update backups
			Message reqMsg = Koorde.this.reqPredecessorMessage;

			// copy edges to update
			IDAddressPair[] updatedEdges = new IDAddressPair[Koorde.this.edges.length];
			synchronized (Koorde.this.edges) {
				System.arraycopy(Koorde.this.edges, 0, updatedEdges, 0, Koorde.this.edges.length);
			}

			// edges[i + 1] = edges[i].predecessor
			for (int i = 0; i < Koorde.this.edges.length - 1; i++) {
				Message repMsg = null;

				if (updatedEdges[i] == null) break;	// give up

				IDAddressPair pred;

				if (updatedEdges[i].equals(Koorde.this.selfIDAddress)) {
					// query receiver (edges[i]) is this node itself
					pred = Koorde.this.predecessor;
				}
				else {
					try {
						repMsg = Koorde.this.sender.sendAndReceive(
								updatedEdges[i].getAddress(), reqMsg);
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "Failed to send a REQ_PREDECESSOR msg or receive a REP_PREDECESSOR msg.", e);
						Koorde.this.forget(updatedEdges, updatedEdges[i]);
						i--;
						continue;
					}

					pred = ((RepPredecessorMessage)repMsg).pred;
				}

				if (updatedEdges[i].equals(pred)) {
					// query receiver itself is its predecessor
					break;
				}

				updatedEdges[i + 1] = pred;
			}

			// write back updated edges
			synchronized (Koorde.this.edges) {
				System.arraycopy(updatedEdges, 0, Koorde.this.edges, 0, Koorde.this.edges.length);
			}
		}	// if (backupToBeUpdated)

		return changed;
	}

	public void forget(IDAddressPair failedNode) {
		super.forget(failedNode);

		this.forget(this.edges, failedNode);
	}

	private void forget(IDAddressPair[] edges, IDAddressPair failedNode) {
		synchronized (this.edges) {
			// pack edges
			int nEdges = this.edges.length;
			for (int i = 0; i < nEdges; i++) {
				if (failedNode.equals(this.edges[i])) {
					int j = i;
					for (j = i; j < i - 1; j++)
						this.edges[j] = this.edges[j + 1];
					this.edges[j] = null;
				}
			}
		}
	}

	public String getRoutingTableString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append(super.getRoutingTableString(verboseLevel));	// successors and predecessor
		sb.append("\n");

		sb.append((1 << this.digitBits) + "m: ");
		sb.append(this.km.toString(verboseLevel));
		sb.append("\n");

		sb.append("de Bruijn edge and backups: [");
		for (int i = 0; i < this.config.getNumEdges(); i++) {
			if (this.edges[i] == null) break;

			sb.append("\n ");
			sb.append(this.edges[i].toString(verboseLevel));
		}
		sb.append("\n]");

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append(super.getRoutingTableHTMLString());	// successors and predecessor

		sb.append("<h4>" + HTMLUtil.stringInHTML(Integer.toString(1 << this.digitBits)) + "m</h4>\n");
		sb.append(HTMLUtil.stringInHTML(this.km.toString()) + "\n");

		sb.append("<h4>De Bruijn edges and backups</h4>\n");
		for (int i = 0; i < this.config.getNumEdges(); i++) {
			if (this.edges[i] == null) break;

			String url = HTMLUtil.convertMessagingAddressToURL(this.edges[i].getAddress());
			sb.append("<a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a><br>\n");
		}

		return sb.toString();
	}

	public void prepareHandlers() {
		super.prepareHandlers();

		// REQ_PREDECESSOR
		MessageHandler handler = new MessageHandler() {
			public Message process(Message msg) {
				return new RepPredecessorMessage(
						Koorde.this.predecessor);
			}
		};
		runtime.addMessageHandler(ReqPredecessorMessage.class, handler);
	}

	private class EdgeFixer implements Runnable {
		private long interval = config.getFixEdgeMinInterval();
		private ID km;

		EdgeFixer(KoordeConfiguration config, ID km) {
			this.km = km;	// km = m << log_2(k)
		}

		public void run() {
			try {
				while (true) {
					synchronized (Koorde.this) {
						if (stopped || suspended) {
							Koorde.this.edgeFixer = null;
							Koorde.this.edgeFixerThread = null;
							break;
						}
					}

					boolean changed = Koorde.this.fixEdges(null);

					// sleep
					if (this.sleep(changed)) return;
				}	// while (true)
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "EdgeFixer interrupted and die.", e);
			}
		}

		private boolean sleep(boolean backupsToBeUpdated) throws InterruptedException {
			if (backupsToBeUpdated) {
				this.interval = config.getFixEdgeMinInterval();
			}
			else {
				this.interval <<= 1;
				if (this.interval > config.getFixEdgeMaxInterval()) {
					this.interval = config.getFixEdgeMaxInterval();
				}
			}

			// sleep
			double playRatio = config.getFixEdgeIntervalPlayRatio();
			double intervalRatio = 1.0 - playRatio + (playRatio * 2.0 * random.nextDouble());

			long sleepPeriod = (long)(this.interval * intervalRatio);
			if (config.getUseTimerInsteadOfThread()) {
				timer.schedule(this, Timer.currentTimeMillis() + sleepPeriod,
						true /*isDaemon*/, true /*executeConcurrently*/);
				return true;
			}
			else {
				Thread.sleep(sleepPeriod);
				return false;
			}
		}
	}
}

/*
 * Copyright 2006-2008,2010-2011 National Institute of Advanced Industrial Science
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;
import ow.routing.chord.message.AckFingerTableMessage;
import ow.routing.chord.message.UpdateFingerTableMessage;
import ow.routing.linearwalker.LinearWalkerRoutingContext;
import ow.routing.linearwalker.message.ReqSuccAndPredMessage;

/**
 * A Chord implementation which completes construction of routing table when joining.
 * This algorithm is described in Figure 6 in the Chord paper
 * "Chord: A Scalable Peer-to-peer Lookup Service for Internet Applications".
 * Note that this algorithm does not allow concurrent joinings by multiple nodes.
 */
public final class ChordInAggressiveJoiningMode extends AbstractChord {
	// messages
	private final Message ackFingerTableMessage;

	protected ChordInAggressiveJoiningMode(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		// prepare messages
		this.ackFingerTableMessage = new AckFingerTableMessage();
	}

	public void join(IDAddressPair[] neighbors) {	// overriding LinearWalker#join
		IDAddressPair respNode = neighbors[0];	// should be successor

		// init_finger_table()

		// ask predecessor and successors of the responsible node (successor)
		super.join(neighbors);	// call this.connectoToSuccessor(respNode)

		// fill the finger table
		IDAddressPair respNodeOfFingerEnd = respNode;
		BigInteger respNodeDistance = distance(respNodeOfFingerEnd.getID(), selfIDAddress.getID());

		this.fingerTable.set(1, respNodeOfFingerEnd);
		this.successorList.add(respNodeOfFingerEnd);

		logger.log(Level.INFO, "join() performs \"init_finger_table()\".");
		logger.log(Level.INFO, "i=1: " + respNodeOfFingerEnd.getAddress());

		for (int i = 2; i <= this.idSizeInBit; i++) {
			BigInteger fingerEndDistance = BigInteger.ONE.shiftLeft(i - 1);	// 2 ^ (i-1)

			if (respNodeDistance.compareTo(fingerEndDistance) < 0) {
				// lastEntryDistance < fingerEndDistance
				// and update lastEntry

				BigInteger fingerEndIDValue = selfIDAddress.getID().toBigInteger().add(fingerEndDistance); 
				ID fingerEndID = ID.getID(fingerEndIDValue, config.getIDSizeInByte());

				// update respNodeOfFingerEnd
				try {
					RoutingResult res = runtime.route(fingerEndID, 1);

					RoutingHop[] routeToFingerEnd = res.getRoute();
					respNodeOfFingerEnd = routeToFingerEnd[routeToFingerEnd.length - 1].getIDAddressPair();
					respNodeDistance = distance(respNodeOfFingerEnd.getID(), selfIDAddress.getID());

					logger.log(Level.INFO, "i=" + i + ": " + respNodeOfFingerEnd.getAddress());
				}
				catch (RoutingException e) {
					logger.log(Level.WARNING, "Routing failed.", e);
				}
			}

			this.fingerTable.set(i, respNodeOfFingerEnd);
			this.successorList.add(respNodeOfFingerEnd);
		}

		// update_others()
		logger.log(Level.INFO, "join() performs \"update_others()\".");

		BigInteger selfIDBigInteger = selfIDAddress.getID().toBigInteger();

		IDAddressPair lastTarget = null;

		for (int i = 1; i <= this.idSizeInBit; i++) {
//System.out.println("  i=" + i);
			BigInteger targetIDBigInteger =
				selfIDBigInteger.subtract(BigInteger.ONE.shiftLeft(i - 1).subtract(BigInteger.ONE));
					// n - (2 ^ (i-1) - 1)
					// Figure 6: p = find_predecessor (n - 2 ^ (i-1)) is incorrect.
			ID targetID = ID.getID(targetIDBigInteger, config.getIDSizeInByte());

			IDAddressPair predecessorOfTarget;
			if (true) {
				// remote
				LinearWalkerRoutingContext cxt =
					(LinearWalkerRoutingContext)this.initialRoutingContext(targetID);
				cxt.setRouteToPredecessorOfTarget();

				RoutingResult res;
				try {
					res = runtime.route(targetID, cxt, 1);	// route te target's predecessor
				}
				catch (RoutingException e) {
					logger.log(Level.WARNING, "Routing failed.", e);
					continue;
				}

				RoutingHop[] route = res.getRoute();
				predecessorOfTarget = route[route.length - 1].getIDAddressPair();
			}
			else {
				// local
				IDAddressPair[] predecessorsToTarget = nextHopCandidates(targetID, null, true, 1, null);
				predecessorOfTarget = predecessorsToTarget[predecessorsToTarget.length - 1];
			}
//System.out.println("  predOfTarget: " + predecessorOfTarget);
//System.out.println("  lastTarget  : " + lastTarget);

			if (!predecessorOfTarget.equals(lastTarget)) {
				if (i > 2) {
					if (!selfIDAddress.equals(lastTarget)) {	// do not send to this node itself
//System.out.println("send UPDATE_FINGER_TABLE(" + lastTarget + ", " + (i - 1));
						this.sendUpdateFingerTableMessage(lastTarget, i - 1);
					}
				}
				lastTarget = predecessorOfTarget;
			}
		}

		if (!selfIDAddress.equals(lastTarget)) {	// do not send to this node itself
//System.out.println("send UPDATE_FINGER_TABLE(" + lastTarget + ", " + idSizeInBit);
			this.sendUpdateFingerTableMessage(lastTarget, this.idSizeInBit);
		}

		logger.log(Level.INFO, "join() completed.");

//		return succeed;
	}

	private void sendUpdateFingerTableMessage(IDAddressPair target, int largestIndex) {
		Message reqMsg = new UpdateFingerTableMessage(largestIndex);

		try {
			Message repMsg = sender.sendAndReceive(target.getAddress(), reqMsg);

			if (!(repMsg instanceof AckFingerTableMessage)) {
				logger.log(Level.SEVERE, "A reply to an UPDATE_FINGER_TABLE message is not an ACK_FINGER_TABLE.");
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send an UPDATE_FINGER_TABLE request or receive a reply.", e);
			this.fail(target);
		}
	}

	public void forget(IDAddressPair failedNode) {
		IDAddressPair oldSuccessor, newSuccessor;

		oldSuccessor = this.successorList.first();

		super.forget(failedNode);

		newSuccessor = this.successorList.first();

		if (!newSuccessor.equals(oldSuccessor)) {
			super.requestSuccAndPred(true, true);
		}
	}

	public void prepareHandlers() {
		super.prepareHandlers(true);

		MessageHandler handler;

		// REQ_SUCC_AND_PRED
		// first half of init_finger_table(n')
		handler = new ReqSuccAndPredMessageHandler();
		runtime.addMessageHandler(ReqSuccAndPredMessage.class, handler);

		// UPDATE_FINGER_TABLE
		// update_finger_table(s,i)
		handler = new MessageHandler() {
			public Message process(final Message msg) {
				ChordInAggressiveJoiningMode.this.touch((IDAddressPair)msg.getSource());	// notify the algorithm

				Runnable r = new Runnable() {
					public void run() {
						int largestIndex = ((UpdateFingerTableMessage)msg).largestIndex;
						IDAddressPair candidateNode = (IDAddressPair)msg.getSource();

						IDAddressPair existingNode = fingerTable.get(largestIndex);

						BigInteger distanceOfCandidate = distance(candidateNode.getID(), selfIDAddress.getID());
						BigInteger distanceOfExisting = distance(existingNode.getID(), selfIDAddress.getID());

//System.out.println("largestIndex: " + largestIndex);
//System.out.println("  existing: " + existingNode);
//System.out.println("    " + distanceOfExisting.toString(16));
//System.out.println("  candidate: " + candidateNode);
//System.out.println("    " + distanceOfCandidate.toString(16));
						if (distanceOfCandidate.compareTo(distanceOfExisting) < 0) {
//System.out.println("    candidate is nearer.");
							// candidate is nearer than existing

							fingerTable.put(candidateNode, largestIndex);
							successorList.add(candidateNode);
//System.out.println("On " + selfIDAddress.getAddress() + ", succ list.add: " + candidateNode);

							if (!predecessor.equals(candidateNode)) {
								// do not forward if the predecessor is the initiator of the message

								try {
									Message repMsg = sender.sendAndReceive(
											predecessor.getAddress(), msg);

									if (!(repMsg instanceof AckFingerTableMessage)) {
										logger.log(Level.SEVERE, "A reply to an UPDATE_FINGER_TABLE message is not an ACK.");
									}
								}
								catch (IOException e) {
									logger.log(Level.WARNING, "Failed to send an <NGER_TABLE request or receive a reply.", e);
									fail(predecessor);
								}
							}
						}
					}
				};

				if (config.getUseTimerInsteadOfThread()) {
					timer.schedule(r, timer.currentTimeMillis(),
							true /*isDaemon*/, true /*executeConcurrently*/);
				}
				else {
					Thread t = new Thread(r);
					t.setName("A MessageHandler of Chord (agg. joining mode) on " + selfIDAddress.getAddress());
					t.setDaemon(true);
					t.start();
				}

				// reply an ACK
				return ChordInAggressiveJoiningMode.this.ackFingerTableMessage;
			}
		};
		runtime.addMessageHandler(UpdateFingerTableMessage.class, handler);
	}

	class ReqSuccAndPredMessageHandler extends AbstractChord.ReqSuccAndPredMessageHandler {
		public Message process(Message msg) {
			Message repMsg = super.process(msg);

			// update predecessor
			predecessor  = (IDAddressPair)msg.getSource();

			return repMsg;
		}
	}
}

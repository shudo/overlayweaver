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

package ow.routing.chord;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.MessageHandler;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;
import ow.routing.linearwalker.LinearWalkerRoutingContext;
import ow.routing.linearwalker.message.ReqSuccAndPredMessage;
import ow.util.Timer;

/**
 * A Chord implementation which updates routing table gradually by stabilization.
 * This algorithm is described in Figure 7 in the Chord paper
 * "Chord: A Scalable Peer-to-peer Lookup Service for Internet Applications".
 */
public final class Chord extends AbstractChord {
	private ChordConfiguration config;

	// daemons
	private FingerTableFixer fingerTableFixer;
	private Thread fingerTableFixerThread = null;

	protected Chord(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		try {
			this.config = (ChordConfiguration)config;
		}
		catch (ClassCastException e) {
			throw new InvalidAlgorithmParameterException("The given config is not ChordConfiguration.");
		}

		// does not invoke a finger table fixer
		//startFingerTableFixer();
	}

	private synchronized void startFingerTableFixer() {
		if (!this.config.getDoFixFingers()) return;

		if (this.fingerTableFixer != null) return;	// to avoid multiple invocations

		this.fingerTableFixer = new FingerTableFixer();

		if (this.config.getUseTimerInsteadOfThread()) {
			timer.schedule(this.fingerTableFixer, timer.currentTimeMillis(),
					true /*isDaemon*/, true /*executeConcurrently*/);
		}
		else if (this.fingerTableFixerThread == null){
			this.fingerTableFixerThread = new Thread(this.fingerTableFixer);
			this.fingerTableFixerThread.setName("FingerTableFixer on " + selfIDAddress.getAddress());
			this.fingerTableFixerThread.setDaemon(true);
			this.fingerTableFixerThread.start();
		}
	}

	private synchronized void stopFingerTableFixer() {
		if (this.fingerTableFixerThread != null) {
			this.fingerTableFixerThread.interrupt();
			this.fingerTableFixerThread = null;
		}
	}

	public synchronized void stop() {
		logger.log(Level.INFO, "Chord#stop() called.");

		super.stop();
		this.stopFingerTableFixer();
	}

	public synchronized void suspend() {
		super.suspend();
		this.stopFingerTableFixer();
	}

	public synchronized void resume() {
		super.resume();
		this.startFingerTableFixer();
	}

	public void prepareHandlers() {
		super.prepareHandlers(true);

		// REQ_SUCC_AND_PRED
		// first half of init_finger_table(n')
		MessageHandler handler = new ReqSuccAndPredMessageHandler();
		runtime.addMessageHandler(ReqSuccAndPredMessage.class, handler);
	}

	class ReqSuccAndPredMessageHandler extends AbstractChord.ReqSuccAndPredMessageHandler {}

	private final class FingerTableFixer implements Runnable {
		public final static boolean OPTIMIZE = false;

		public void run() {
			boolean updated = true;

			try {
				// initial sleep
				if (!config.getUseTimerInsteadOfThread()) {
					Thread.sleep((long)(config.getFixFingersInitialInterval()));
				}

				while (true) {
					synchronized (Chord.this) {
						if (stopped || suspended) {
							Chord.this.fingerTableFixer = null;
							Chord.this.fingerTableFixerThread = null;
							break;
						}
					}

					// update a finger
					BigInteger fingerEdgeDistance;
					BigInteger fingerStartBigInteger;

					if (random.nextDouble() < config.getProbProportionalToIDSpace()) {
						if (OPTIMIZE) {
							fingerEdgeDistance = new BigInteger(Chord.this.idSizeInBit, random);
						}
						else {
							fingerEdgeDistance = new BigInteger(Chord.this.idSizeInBit - 1, random);
						}
					}
					else {
						int i = random.nextInt(idSizeInBit - 1) + 2;
							// from 2 to idSizeInBit (both inclusive)

						if (OPTIMIZE) {
							fingerEdgeDistance = BigInteger.ONE.shiftLeft(i);	// 2 ^ i
						}
						else {
							fingerEdgeDistance = BigInteger.ONE.shiftLeft(i - 1);	// 2 ^ (i-1)
						}
					}
					fingerStartBigInteger =
						selfIDAddress.getID().toBigInteger().add(fingerEdgeDistance);

					ID fingerEdge = ID.getID(fingerStartBigInteger, config.getIDSizeInByte());
						// fingerEdge is finger[k].start,
						// or finger[k+1].start in case that optimize is true.

					RoutingResult res;
					try {
						if (OPTIMIZE) {
							LinearWalkerRoutingContext cxt =
								(LinearWalkerRoutingContext)Chord.this.initialRoutingContext(fingerEdge);
							cxt.setRouteToPredecessorOfTarget();

							res = runtime.route(fingerEdge, cxt, 1);	// route to target's predecessor
						}
						else {
							res = runtime.route(fingerEdge, 1);		// responsible node for self + 2^(i-1)
						}
						RoutingHop[] route = res.getRoute();
						IDAddressPair respNode = route[route.length - 1].getIDAddressPair();

						for (int i = route.length - 1; i > 0; i--) {
							IDAddressPair node = route[i].getIDAddressPair();

							if (selfIDAddress.getID().equals(node.getID()))	// node is self
								continue;

							updated |= fingerTable.put(node);
							successorList.add(node);
						}

						if (updated) {
							logger.log(Level.INFO, "An entry incorporated to finger table: " + respNode);
						}
					}
					catch (RoutingException e) {
						logger.log(Level.WARNING, "Routing failed.", e);
					}

					// sleep
					if (this.sleep()) return;
				}	// while (true)
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "FingerTableFixer interrupted and die.", e);
			}
		}

		private boolean sleep() throws InterruptedException {
			long interval = config.getFixFingersMinInterval();

			// determine the next interval

			// according to the number of different entries in the finger table
			// parameters examples: min interval: 10, max interval: 120
			long minInterval = config.getFixFingersMinInterval();
			long maxInterval = config.getFixFingersMaxInterval();

			int numFingers = fingerTable.numOfDifferentEntries();   // 0 - 160
			double ratio = Math.log(numFingers + 1) / Math.log(idSizeInBit + 1);

			interval = minInterval + (long)((maxInterval - minInterval) * ratio);

			// extends interval in case that no new entry found.
			// parameters examples: min interval: 2, max interval: 128
//			if (updated) {
//				this.interval = config.getStabilizeMinInterval();
//			}
//			else {
//				this.interval <<= 1;
//				if (this.interval > config.getFixFingersMaxInterval()) {
//					this.interval = config.getFixFingersMaxInterval();
//				}
//			}

			double playRatio = config.getFixFingersIntervalPlayRatio();
			double intervalRatio = 1.0 - playRatio + (playRatio * 2.0 * random.nextDouble());

			// sleep
			long sleepPeriod = (long)(interval * intervalRatio);

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

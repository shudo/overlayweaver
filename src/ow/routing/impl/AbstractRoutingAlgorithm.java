/*
 * Copyright 2006-2009,2012 National Institute of Advanced Industrial Science
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

package ow.routing.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.MessageSender;
import ow.routing.RoutingAlgorithm;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingRuntime;
import ow.routing.RoutingService;
import ow.util.Timer;

public abstract class AbstractRoutingAlgorithm implements RoutingAlgorithm {
	protected final static Logger logger = Logger.getLogger("routing");

	protected final static Random random = new Random();
	protected static Timer timer = null;

	protected IDAddressPair selfIDAddress;
	protected final RoutingAlgorithmConfiguration config;
	protected final RoutingRuntime runtime;
	protected MessageSender sender;

	private final FailureTable failureTable;

	public AbstractRoutingAlgorithm(RoutingAlgorithmConfiguration config, RoutingService routingSvc) {
		this.config = config;

		this.runtime = (RoutingRuntime)routingSvc;
		if (routingSvc != null) {
			this.sender = this.runtime.getMessageSender();

			this.selfIDAddress = routingSvc.getSelfIDAddressPair();

			// connect this algorithm instance with the routing service
			try {
				((AbstractRoutingDriver)routingSvc).setRoutingAlgorithm(this);
			}
			catch (ClassCastException e) {/*ignore*/}
		}
//		else {
//			// to test a RoutingAlgorithm without RoutingService instance
//			int idSizeInByte = config.getIDSizeInByte();
//			this.selfIDAddress = IDAddressPair.getIDAddressPair(ID.getID(new byte[idSizeInByte], idSizeInByte), null);
//		}

		this.failureTable = new FailureTable(this.config.getFailureExpiration());

		if (config.getUseTimerInsteadOfThread()) {
			synchronized (AbstractRoutingAlgorithm.class) {
				if (AbstractRoutingAlgorithm.timer == null) {
					AbstractRoutingAlgorithm.timer = Timer.getSingletonTimer();
//						new Timer("A Timer in RoutingAlgorithm", Thread.NORM_PRIORITY);
				}
			}
		}
	}

	/**
	 * The default implementation of
	 * {@link RoutingAlgorithm#initialRoutingContext(ID) initialRoutingContext()}.
	 */
	public RoutingContext initialRoutingContext(ID target) {
		return null;
	}

	public final void fail(IDAddressPair failedNode) {
		int numOfFailures = this.failureTable.register(failedNode);

		String additionalMsg = "";
		if (numOfFailures >= this.config.getNumOfFailuresBeforeForgetNode()) {
			this.forget(failedNode);

			additionalMsg = ", and forget it";
		}

		logger.log(Level.WARNING, "RoutingAlgorithm#fail: " + failedNode.getAddress()
				+ " on " + selfIDAddress.getAddress()
				+ " " + numOfFailures + " time" + (numOfFailures > 1 ? "s":"")
				+ additionalMsg + ".");
	}

	public final RoutingAlgorithmConfiguration getConfiguration() {
		return this.config;
	}

	/**
	 * Table to count the number of sequential failures.
	 */
	private final static class FailureTable {
		private final static int GC_FREQ = 100;
		private int gcCountdown = GC_FREQ;

		private long expiration;
		private Map<IDAddressPair,Long> lastFailedTimeTable;
		private Map<IDAddressPair,Integer> numOfTimesTable;

		FailureTable(long expiration) {
			this.expiration = expiration;
			this.numOfTimesTable = new HashMap<IDAddressPair,Integer>();
			this.lastFailedTimeTable = new HashMap<IDAddressPair,Long>();
		}

		/**
		 * Registers a failed node to count the number of sequential failure.
		 *
		 * @return the number of sequential failures.
		 */
		public int register(IDAddressPair node) {
			long currentTime = Timer.currentTimeMillis();
			int numOfTimes;

			synchronized (this) {
				Long lastTimeObj = this.lastFailedTimeTable.get(node);
				long lastTime = (lastTimeObj == null ? 0 : lastTimeObj);

				Integer numOfTimesObj = this.numOfTimesTable.get(node);
				numOfTimes = (numOfTimesObj == null ? 0 : numOfTimesObj);

				if (currentTime >= lastTime + this.expiration) {
					// expire
					numOfTimes = 0;
				}

				numOfTimes++;

				this.lastFailedTimeTable.put(node, currentTime);
				this.numOfTimesTable.put(node, numOfTimes);

				// garbage collection
				if (--gcCountdown <= 0) {
					gcCountdown = GC_FREQ;

					Set<Map.Entry<IDAddressPair,Long>> entrySet =
						this.lastFailedTimeTable.entrySet();
					Map.Entry<IDAddressPair,Long>[] entryArray =
						new Map.Entry/*<IDAddressPair,Long>*/[entrySet.size()];
					entrySet.toArray(entryArray);

					for (Map.Entry<IDAddressPair,Long> e: entryArray) {
						lastTime = e.getValue();
						if (currentTime >= lastTime + this.expiration) {
							// expire
							IDAddressPair idAddr = e.getKey();
							this.lastFailedTimeTable.remove(idAddr);
							this.numOfTimesTable.remove(idAddr);
						}
					}
				}
			}	// synchronized (this)

			return numOfTimes;
		}
	}
}

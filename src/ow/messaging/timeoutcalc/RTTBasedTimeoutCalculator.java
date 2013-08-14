/*
 * Copyright 2007,2009 Kazuyuki Shudo, and contributors.
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

package ow.messaging.timeoutcalc;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.messaging.MessagingAddress;
import ow.messaging.MessagingConfiguration;
import ow.util.Timer;

/**
 * An instance of this class calculates timeout in TCP style.
 * The algorithm is based on what is described in a paper
 * "Congestion Avoidance and Control", Proc. SIGCOMM'88.
 */
public final class RTTBasedTimeoutCalculator implements TimeoutCalculator {
	private final static Logger logger = Logger.getLogger("messaging");

	private final MessagingConfiguration config;
	private Map<MessagingAddress,TargetRecord> targetTable = null;
	private SortedSet<TargetRecord> targetListInLRUOrder = null;

	public RTTBasedTimeoutCalculator(MessagingConfiguration config) {
		this.config = config;

		if (this.config.getDoTimeoutCalculation()) {
			this.targetTable = new HashMap<MessagingAddress,TargetRecord>();
			this.targetListInLRUOrder = new TreeSet<TargetRecord>();
		}
	}

	public int calculateTimeout(MessagingAddress target) {
		if (!this.config.getDoTimeoutCalculation()) {	// fixed timeout
			return this.config.getStaticTimeout();
		}

		TargetRecord record = null;
		synchronized (this.targetTable) {
			record = this.targetTable.get(target);
		}

		int timeout;
		if (record != null) {
			timeout = record.calculateTimeout();
		}
		else {
			timeout = this.config.getStaticTimeout();
		}

		return timeout;
	}

	public void updateRTT(MessagingAddress target, int rtt) {
		if (!this.config.getDoTimeoutCalculation()) {	// fixed timeout
			return;
		}

		TargetRecord record = null;
		synchronized (this.targetTable) {
			record = this.targetTable.get(target);
		}

		if (record != null) {
			// update estimated RTT and mean deviation of it in TCP style
			synchronized (record) {
				long m = rtt - record.rtt;
				record.rtt += m >> 3;	// RTT = 7/8 RTT + 1/8 new

				if (m < 0) {
					m = -m;
					m -= record.mdev;

					// blocks mean deviation updates when RTT decreases.
					m >>= 3;
				}
				else {
					m -= record.mdev;
				}

				record.mdev += m >> 2;	// mdev = 3/4 mdev + 1/4 new

				// updates mdev_max and rtt_var
				if (record.mdev > record.mdev_max) {
					record.mdev_max = record.mdev;
					if (record.mdev_max > record.rttvar) {
						record.rttvar = record.mdev_max;
						record.rttvarKeepingPeriod = config.getRTTKeepingPeriod();	// reset
					}
				}

				if (record.rttvarKeepingPeriod-- <= 0) {
					if (record.mdev_max < record.rttvar) {
						record.rttvar -= ((record.rttvar - record.mdev_max + 3) >> 2);
							// reduce 1/4 of the difference
					}

					record.mdev_max = config.getTimeoutMin() >> 2;	// reset

					record.rttvarKeepingPeriod = config.getRTTKeepingPeriod();	// reset
				}

				// removes and re-puts the record into tables
				record.touch();
			}
		}
		else {	// no previous measure
			record = new TargetRecord(target, rtt);

			synchronized (this.targetListInLRUOrder) {
				this.targetListInLRUOrder.add(record);

				if (this.targetListInLRUOrder.size() > this.config.getRTTTableSize()) {
					// RTT table overflows
					// remove a record in LRU policy
					TargetRecord oldestRecord = this.targetListInLRUOrder.last();
					this.targetListInLRUOrder.remove(oldestRecord);
					synchronized (this.targetTable) {
						this.targetTable.remove(oldestRecord.getTarget());
					}
				}
			}

			synchronized (this.targetTable) {
				this.targetTable.put(target, record);
			}
		}

		logger.log(Level.INFO, "To " + target + ": RTT: " + rtt + ", ave. RTT: " + record.rtt
				+ ", mdev: " + record.mdev + ", mdev_max: " + record.mdev_max + ", rttvar: " + record.rttvar
				+ ", timeout: " + record.calculateTimeout());
	}

	private class TargetRecord implements Comparable<TargetRecord> {
		private MessagingAddress target;
		int rtt;	// averaged RTT
		int mdev;	// mean deviation of RTT
		int mdev_max;
		int rttvar;
		private long lastUpdated;
		private long rttvarKeepingPeriod;

		public TargetRecord(MessagingAddress target, int rtt) {
			this.target = target;
			this.rtt = rtt;
			this.mdev = rtt >> 1;	// make sure timeout = 3 * RTT
			this.mdev_max = this.rttvar =
				Math.max(this.mdev, config.getTimeoutMin() >> 2);

			this.lastUpdated = Timer.currentTimeMillis();
			this.rttvarKeepingPeriod = config.getRTTKeepingPeriod();
		}

		public int calculateTimeout() {
			return Math.min(
					this.rtt + (this.rttvar << 2),
					config.getTimeoutMax());
		}

		public MessagingAddress getTarget() { return this.target; }

		public void touch() {
			// removes and re-puts this instance at the correct place
			this.lastUpdated = Timer.currentTimeMillis();

			synchronized (RTTBasedTimeoutCalculator.this.targetListInLRUOrder) {
				RTTBasedTimeoutCalculator.this.targetListInLRUOrder.remove(this);
				RTTBasedTimeoutCalculator.this.targetListInLRUOrder.add(this);
			}
		}

		// to implement Comperable
		public int compareTo(TargetRecord o) {
			// newer (larger number) is lesser (nearer to the top)
			return (int)(o.lastUpdated - this.lastUpdated);
		}
	}
}

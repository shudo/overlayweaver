/*
 * Copyright 2007 Kazuyuki Shudo, and contributors.
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

import ow.messaging.MessagingAddress;
import ow.messaging.MessagingConfiguration;

/**
 * A timeout calculator, which returns the static value
 * defined in {@link ow.messaging.MessagingConfiguration MessagingConfiguration}.
 */
public final class StaticTimeoutCalculator implements TimeoutCalculator {
	private final int timeout;

	public StaticTimeoutCalculator(MessagingConfiguration config) {
		this.timeout = config.getStaticTimeout();
	}

	public int calculateTimeout(MessagingAddress target) {
		return this.timeout;
	}

	public void updateRTT(MessagingAddress target, int rtt) {
		return;
	}
}

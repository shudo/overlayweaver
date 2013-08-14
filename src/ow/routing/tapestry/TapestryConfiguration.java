/*
 * Copyright 2006,2008 National Institute of Advanced Industrial Science
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

import ow.routing.plaxton.PlaxtonConfiguration;

/**
 * A class holding configuration for Tapestry.
 */
public final class TapestryConfiguration extends PlaxtonConfiguration {
	public final static long DEFAULT_ACKNOWLEDGED_MULTICAST_TIMEOUT = 30 * 1000L;
	public final static boolean DEFAULT_RETURN_ACK_IN_ACKNOWLEDGED_MULTICAST = true;

	protected TapestryConfiguration() {}

	private long ackedMulticastTimeout = DEFAULT_ACKNOWLEDGED_MULTICAST_TIMEOUT;
	public long getAcknowledgedMulticastTimeout() { return this.ackedMulticastTimeout; }
	public long setAcknowledgedMulticastTimeout(long timeout) {
		long old = this.ackedMulticastTimeout;
		this.ackedMulticastTimeout = timeout;
		return old;
	}

    private boolean returnAck = DEFAULT_RETURN_ACK_IN_ACKNOWLEDGED_MULTICAST;
    public boolean getReturnAckInAcknowledgedMulticast() { return this.returnAck; }
    public boolean setReturnAckInAcknowledgedMulticast(boolean ret) {
    	boolean old = this.returnAck;
    	this.returnAck = ret;
    	return old;
    }
}

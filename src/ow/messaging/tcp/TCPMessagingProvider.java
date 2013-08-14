/*
 * Copyright 2006-2009,2011 National Institute of Advanced Industrial Science
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

package ow.messaging.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import ow.messaging.AbstractMessagingProvider;
import ow.messaging.InetMessagingAddress;
import ow.messaging.MessageReceiver;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingConfiguration;
import ow.messaging.MessagingProvider;
import ow.messaging.timeoutcalc.RTTBasedTimeoutCalculator;
import ow.messaging.timeoutcalc.StaticTimeoutCalculator;
import ow.messaging.timeoutcalc.TimeoutCalculator;

/**
 * A messaging provider which uses TCP as the transport protocol.
 * Call MessagingFactory#getProvider() to obtain a provider.
 */
public class TCPMessagingProvider extends AbstractMessagingProvider {
	private final static String NAME = "TCP";

	private final Map<Integer,TCPMessageReceiver> receiverTable =
		new HashMap<Integer,TCPMessageReceiver>();

	private InetAddress selfAddress = null;
	private TimeoutCalculator timeoutCalculator = null;
	private MessagingAddress statCollectorAddress = null;

	public String getName() { return NAME; }
	public boolean isReliable() { return true; }

	public MessagingConfiguration getDefaultConfiguration() { return new TCPMessagingConfiguration(); }

	public MessageReceiver getReceiver(MessagingConfiguration config, int port, int portRange) throws IOException {
		synchronized (this) {
			if (this.timeoutCalculator == null) {
				if (config.getDoTimeoutCalculation()) {
					this.timeoutCalculator = new RTTBasedTimeoutCalculator(config);
				}
				else {
					this.timeoutCalculator = new StaticTimeoutCalculator(config);
				}
			}
		}

		TCPMessageReceiver receiver;

		synchronized (this.receiverTable) {
			receiver = this.receiverTable.get(port);

			if (receiver == null) {
				receiver = new TCPMessageReceiver(this.selfAddress, port, portRange,
						(TCPMessagingConfiguration)config, this);
				receiverTable.put(port, receiver);
				receiver.start();
			}
		}

		return receiver;
	}

	public InetMessagingAddress getMessagingAddress(String hostAndPort, int port)
			throws UnknownHostException {
		return new InetMessagingAddress(hostAndPort, port);
	}

	public InetMessagingAddress getMessagingAddress(String hostAndPort)
			throws UnknownHostException {
		return new InetMessagingAddress(hostAndPort);
	}

	public InetMessagingAddress getMessagingAddress(int port) {
		return new InetMessagingAddress(port);
	}

	public TimeoutCalculator getTimeoutCalculator() { return this.timeoutCalculator; }

	public MessagingAddress getMessagingCollectorAddress() { return this.statCollectorAddress; }
	public MessagingAddress setMessagingCollectorAddress(MessagingAddress addr) {
		MessagingAddress old;

		synchronized (this) {
			old = this.statCollectorAddress;
			this.statCollectorAddress = addr;
		}

		return old;
	}

	public MessagingProvider substitute() {
		return null;
	}

	public void setSelfAddress(String host) throws UnknownHostException {
		this.selfAddress = InetAddress.getByName(host);

		synchronized (this.receiverTable) {
			for (TCPMessageReceiver receiver: this.receiverTable.values()) {
				receiver.setSelfAddress(host);
			}
		}
	}
}

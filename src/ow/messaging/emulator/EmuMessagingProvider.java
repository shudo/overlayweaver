/*
 * Copyright 2006,2009,2011-2012 National Institute of Advanced Industrial Science
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

package ow.messaging.emulator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ow.messaging.AbstractMessagingProvider;
import ow.messaging.MessageReceiver;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingConfiguration;
import ow.messaging.MessagingFactory;
import ow.messaging.MessagingProvider;
import ow.messaging.util.MessagingUtility;

/**
 * A messaging provider which works on a single computer.
 * Note that all nodes have to be in a single ClassLoader to communicate each other. 
 * Call MessagingFactory#getProvider() to obtain a provider.
 *
 * EmuMessagingProvider is not singleton, and each instance can have its own EmuHostID.
 */
public final class EmuMessagingProvider extends AbstractMessagingProvider {
	private final static String NAME = "Emulator";

	private EmuHostID selfHostID;

	private final Map<Integer,EmuMessageReceiver> receiverTable =
		new HashMap<Integer,EmuMessageReceiver>();
			// Note: these tables prevent keys (threads) from being reclaimed.
//	private TimeoutCalculator timeoutCalculator = null;
	private MessagingAddress statCollectorAddress = null;

	public EmuMessagingProvider() {
		this.selfHostID = null;
	}

	public String getName() { return NAME; }
	public boolean isReliable() { return true; }

	public MessagingConfiguration getDefaultConfiguration() {
		EmuMessagingConfiguration config = new EmuMessagingConfiguration();
		config.setInitialID(MessagingFactory.INITIAL_EMULATOR_HOST_ID);
		return config;
	}

	public MessageReceiver getReceiver(MessagingConfiguration config, int port, int portRange)
			throws IOException {
//		synchronized (this) {
//			if (this.timeoutCalculator == null) {
//				if (config.getDoTimeoutCalculation()) {
//					this.timeoutCalculator = new RTTBasedTimeoutCalculator(config);
//				}
//				else {
//					this.timeoutCalculator = new StaticTimeoutCalculator(config);
//				}
//			}
//		}

		EmuMessagingConfiguration emuConfig = (EmuMessagingConfiguration)config;

		// set host id
		EmuHostID.setInitialID(emuConfig.getInitialID());

		synchronized (this) {
			if (this.selfHostID == null) {
				this.selfHostID = EmuHostID.getNewInstance();
			}
		}

		// provide a receiver
		EmuMessageReceiver receiver;

		synchronized (this.receiverTable) {
			receiver = this.receiverTable.get(port);
			if (receiver == null) {
				receiver = new EmuMessageReceiver(emuConfig, this.selfHostID, port, this);
				this.receiverTable.put(port, receiver);

				receiver.start();
			}
		}

		return receiver;
	}

	public MessagingAddress getMessagingAddress(String hostname, int port) {
		EmuHostID host = EmuHostID.resolve(hostname);

		if (host == null)
			return null;

		return new EmuMessagingAddress(host, port);
	}

	public MessagingAddress getMessagingAddress(String hostAndPortStr) {
		MessagingUtility.HostAndPort hostAndPort =
			MessagingUtility.parseHostnameAndPort(hostAndPortStr);

		EmuHostID host = EmuHostID.resolve(hostAndPort.getHostName());

		if (host == null)
			return null;

		return new EmuMessagingAddress(host, hostAndPort.getPort());
	}

	public EmuMessagingAddress getMessagingAddress(int port) {
		return new EmuMessagingAddress(this.selfHostID, port);
	}

//	public TimeoutCalculator getTimeoutCalculator() { return this.timeoutCalculator; }

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
		MessagingProvider newProvider = new EmuMessagingProvider();

		return newProvider;
	}

	public void setSelfAddress(String hostname) {
		synchronized (this.receiverTable) {
			for (EmuMessageReceiver receiver: this.receiverTable.values()) {
				receiver.setSelfAddress(hostname);
			}
		}
	}
}

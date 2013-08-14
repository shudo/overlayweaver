/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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

package ow.stat;

import java.io.IOException;

import ow.messaging.MessageReceiver;
import ow.messaging.MessagingConfiguration;
import ow.messaging.MessagingFactory;
import ow.messaging.MessagingProvider;
import ow.messaging.Signature;

public final class StatConfiguration {
	// for Messaging
	private final static String DEFAULT_MESSAGING_TRANSPORT = "UDP";
//	private final static String DEFAULT_MESSAGING_TRANSPORT = "TCP";
		// "UDP" or "TCP"
	private final static int DEFAULT_SELF_PORT = 3997;
	private final static int DEFAULT_SELF_PORT_RANGE = 100;
	private final static int DEFAULT_CONTACT_PORT = 3997;
	public final static boolean DEFAULT_DO_UPNP_NAT_TRAVERSAL = true;

	// for StatCollector
	private final static int DEFAULT_PING_FREQUENCY = 25;
	private final static int DEFAULT_NUM_OF_FAILURES_BEFORE_FORGET_COLLECTOR = 2;
	private final static int DEFAULT_NUM_OF_NODES_NODE_COLLECTOR_REQUESTS = 40;
	private final static int DEFAULT_NODE_COLLECTOR_CONCURRENCY = 20;
	private final static long DEFAULT_PERIODIC_COLLECTION_INTERVAL = 10 * 1000L;


	protected StatConfiguration() {}
		// prohibits instantiation directly by other classes


	private String messagingTransport = DEFAULT_MESSAGING_TRANSPORT;
	public String getMessagingTransport() { return this.messagingTransport; }
	public String setMessagingTransport(String transport) {
		String old = this.messagingTransport;
		this.messagingTransport = transport;
		return old;
	}

	private int selfPort = DEFAULT_SELF_PORT;
	public int getSelfPort() { return this.selfPort; }
	public int setSelfPort(int port) {
		int old = this.selfPort;
		this.selfPort = port;
		return old;
	}

	private int selfPortRange = DEFAULT_SELF_PORT_RANGE;
	public int getSelfPortRange() { return this.selfPortRange; }
	public int setSelfPortRange(int range) {
		int old = this.selfPortRange;
		this.selfPortRange = range;
		return old;
	}

	private int contactPort = DEFAULT_CONTACT_PORT;
	public int getContactPort() { return this.contactPort; }
	public int setContactPort(int port) {
		int old = this.contactPort;
		this.contactPort = port;
		return old;
	}

	private boolean doUPnPNATTraversal = DEFAULT_DO_UPNP_NAT_TRAVERSAL;
	public boolean getDoUPnPNATTraversal() { return this.doUPnPNATTraversal; }
	public boolean setDoUPnPNATTraversal(boolean flag) {
		boolean old = this.doUPnPNATTraversal;
		this.doUPnPNATTraversal = flag;
		return old;
	}

	private int pingFrequency = DEFAULT_PING_FREQUENCY;
	public int getPingFrequency() { return this.pingFrequency; }
	public int setPingFrequency(int freq) {
		int old = this.pingFrequency;
		this.pingFrequency = freq;
		return old;
	}

	private int numOfFailuresBeforeForgetCollector = DEFAULT_NUM_OF_FAILURES_BEFORE_FORGET_COLLECTOR;
	public int getNumOfFailuresBeforeForgetCollector() { return this.numOfFailuresBeforeForgetCollector; }
	public int setNumOfFailuresBeforeForgetCollector(int num) {
		int old = this.numOfFailuresBeforeForgetCollector;
		this.numOfFailuresBeforeForgetCollector = num;
		return old;
	}

	private int numOfNodesNodeCollectorRequests = DEFAULT_NUM_OF_NODES_NODE_COLLECTOR_REQUESTS;
	public int getNumOfNodesNodeCollectorRequests() { return this.numOfNodesNodeCollectorRequests; }
	public int setNumOfNodesNodeCollectorRequests(int num) {
		int old = this.numOfNodesNodeCollectorRequests;
		this.numOfNodesNodeCollectorRequests = num;
		return old;
	}

	private int nodeCollectorConcurrency = DEFAULT_NODE_COLLECTOR_CONCURRENCY;
	public int getNodeCollectorConcurrency() { return this.nodeCollectorConcurrency; }
	public int setNodeCollectorConcurrency(int concurrency) {
		int old = this.nodeCollectorConcurrency;
		this.nodeCollectorConcurrency = concurrency;
		return old;
	}

	private long periodicCollectionInterval = DEFAULT_PERIODIC_COLLECTION_INTERVAL;
	public long getPeriodicCollectionInterval() { return this.periodicCollectionInterval; }
	public long setPeriodicCollectionInterval(long interval) {
		long old = this.periodicCollectionInterval;
		this.periodicCollectionInterval = interval;
		return old;
	}

	private String selfHost = null;
		// does not have the default value and could be set by an application
	public String getSelfAddress() { return this.selfHost; }
	public String setSelfAddress(String host) {
		String old = this.selfHost;
		this.selfHost = host;
		return old;
	}

	// Utility methods
	public MessagingProvider deriveMessagingProvider()
			throws Exception {
		MessagingProvider provider = MessagingFactory.getProvider(
				this.getMessagingTransport(),
				Signature.getAllAcceptingSignature());

		if (this.getSelfAddress() != null) {
			provider.setSelfAddress(this.getSelfAddress());
		}

		return provider;
	}

	public MessageReceiver deriveMessageReceiver(MessagingProvider provider)
			throws IOException {
		MessagingConfiguration msgConfig = provider.getDefaultConfiguration();
		msgConfig.setDoUPnPNATTraversal(this.getDoUPnPNATTraversal());

		MessageReceiver receiver = provider.getReceiver(msgConfig,
				this.getSelfPort(), this.getSelfPortRange());
		this.setSelfPort(receiver.getPort());	// correct config

		return receiver;
	}
}

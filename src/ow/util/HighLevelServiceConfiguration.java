/*
 * Copyright 2009-2010 Kazuyuki Shudo, and contributors.
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

package ow.util;

public class HighLevelServiceConfiguration {
	// for Routing
	public final static String DEFAULT_ROUTING_STYLE = "Iterative";
//	public final static String DEFAULT_ROUTING_STYLE = "Recursive";
		// "Iterative" or "Recursive"
	public final static String DEFAULT_ROUTING_ALGORITHM = "Chord";
//	public final static String DEFAULT_ROUTING_ALGORITHM = "FRT-Chord";
//	public final static String DEFAULT_ROUTING_ALGORITHM = "Kademlia";
//	public final static String DEFAULT_ROUTING_ALGORITHM = "Koorde";
//	public final static String DEFAULT_ROUTING_ALGORITHM = "LinearWalker";
//	public final static String DEFAULT_ROUTING_ALGORITHM = "Pastry";
//	public final static String DEFAULT_ROUTING_ALGORITHM = "Tapestry";
		// "Chord", "Kademlia", "Koorde", "LinearWalker", "Tapestry" or "Pastry"

	// for Messaging
	public final static String DEFAULT_MESSAGING_TRANSPORT = "UDP";
//	public final static String DEFAULT_MESSAGING_TRANSPORT = "TCP";
		// "UDP" or "TCP"
	public final static int DEFAULT_SELF_PORT = 3997;
	public final static int DEFAULT_SELF_PORT_RANGE = 100;
	public final static int DEFAULT_CONTACT_PORT = 3997;
	public final static boolean DEFAULT_DO_UPNP_NAT_TRAVERSAL = true;


	private String routingStyle = DEFAULT_ROUTING_STYLE;
	public String getRoutingStyle() { return this.routingStyle; }
	public String setRoutingStyle(String type) {
		String old = this.routingStyle;
		this.routingStyle = type;
		return old;
	}

	private String algo = DEFAULT_ROUTING_ALGORITHM;
	public String getRoutingAlgorithm() { return this.algo; }
	public String setRoutingAlgorithm(String algo) {
		String old = this.algo;
		this.algo = algo;
		return old;
	}

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


	private String selfHost = null;
		// does not have the default value and could be set by an application
	public String getSelfAddress() { return this.selfHost; }
	public String setSelfAddress(String host) {
		String old = this.selfHost;
		this.selfHost = host;
		return old;
	}
}

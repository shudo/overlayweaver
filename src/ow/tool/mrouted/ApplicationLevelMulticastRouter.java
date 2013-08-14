/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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

package ow.tool.mrouted;

import java.io.IOException;
import java.io.Serializable;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.ipmulticast.Group;
import ow.ipmulticast.IPMulticast;
import ow.ipmulticast.igmpd.IGMPDaemon;
import ow.mcast.Mcast;
import ow.mcast.McastCallback;
import ow.mcast.McastConfiguration;
import ow.mcast.SpanningTreeChangedCallback;
import ow.messaging.MessagingAddress;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingException;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;

/**
 * Application-level multicast (ALM) router,
 * which is usually instantiated by a
 * {@link ow.tool.mrouted.Main Main} class.
 */
public class ApplicationLevelMulticastRouter implements Mcast {
	private static Logger logger = Logger.getLogger("mrouted");

	// configuration object
	private ApplicationLevelMulticastRouterConfiguration config;

	// sub-components
	private IGMPDaemon igmpd;
	private IPMulticast ipmcast;
	private Mcast mcast;

	private DatagramSocket sockForOverlay;
	private int idSizeInByte;

	private GroupTable groupTable;

	ApplicationLevelMulticastRouter(ApplicationLevelMulticastRouterConfiguration config,
			IGMPDaemon igmpd, IPMulticast ipmcast, Mcast mcast) throws IOException {
		this.config = config;

		this.igmpd = igmpd;
		this.ipmcast = ipmcast;
		this.mcast = mcast;

		int forwarderPort = this.mcast.getConfiguration().getSelfPort()
			+ this.config.getPortDiffFromMcast();
		InetAddress selfInetAddr = null;
		if (this.mcast.getConfiguration().getSelfAddress() != null) {
			selfInetAddr = InetAddress.getByName(this.mcast.getConfiguration().getSelfAddress());
		}
		else {
			selfInetAddr = InetAddress.getLocalHost();
		}
		InetSocketAddress selfSockAddr = new InetSocketAddress(selfInetAddr, forwarderPort);

		this.sockForOverlay = new DatagramSocket(selfSockAddr);

		this.idSizeInByte = this.mcast.getRoutingAlgorithmConfiguration().getIDSizeInByte();

		this.groupTable = new GroupTable(this.config, this.idSizeInByte,
				this.sockForOverlay, ipmcast);
	}

	/**
	 * Gets this ALM router instance started.
	 */
	public void start() throws IOException {
		// create observers
		MulticastGroupObserver mGroupObserver;
		MulticastTrafficForwarder mTrafficForwarder;
		OverlayGroupObserver overlayObserver;

		try {
			mGroupObserver = new MulticastGroupObserver(this.config, mcast, this.groupTable, this.igmpd, this.ipmcast, this.idSizeInByte);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Could not instantiate an instance of MulticastGroupObserver.", e);
			throw e;
		}
		mTrafficForwarder = new MulticastTrafficForwarder(this.sockForOverlay, this.config.getDatagramBufferSize(), this.config.getTTLOnOverlay(), this.groupTable, this.idSizeInByte);
		overlayObserver = new OverlayGroupObserver(this.config, this.groupTable);

		// start components
		this.igmpd.start(mGroupObserver);

		this.ipmcast.start(mTrafficForwarder);

		this.mcast.addSpanningTreeChangedCallback(overlayObserver);
	}

	/**
	 * Stop this ALM router.
	 */
	public synchronized void stop() {
		if (this.igmpd != null) {
			this.igmpd.stop();
			this.igmpd = null;
		}

		if (this.ipmcast != null) {
			this.ipmcast.stop();
			this.ipmcast = null;
		}

		if (this.mcast != null) {
			this.mcast.stop();
			this.mcast = null;
		}

		if (this.sockForOverlay != null) {
			this.sockForOverlay.close();
			this.sockForOverlay = null;
		}

		this.groupTable = null;
	}

	public synchronized void suspend() {
		if (this.igmpd != null) {
			this.igmpd.suspend();
		}

		if (this.ipmcast != null) {
			this.ipmcast.suspend();
		}

		if (this.mcast != null) {
			this.mcast.suspend();
		}
	}

	public synchronized void resume() {
		if (this.igmpd != null) {
			this.igmpd.resume();
		}

		if (this.ipmcast != null) {
			this.ipmcast.suspend();
		}

		if (this.mcast != null) {
			this.mcast.resume();
		}
	}

	//
	// for commands
	//

	public Group[] getJoinedMulticastGroups() {
		return this.groupTable.getJoinedGroupSet();
	}

	//
	// to implements Mcast
	//
	public MessagingAddress joinOverlay(String hostname, int port) throws UnknownHostException, RoutingException {
		return this.mcast.joinOverlay(hostname, port);
	}
	public MessagingAddress joinOverlay(String hostAndPort) throws UnknownHostException, RoutingException {
		return this.mcast.joinOverlay(hostAndPort);
	}
	public void clearRoutingTable() { this.mcast.clearRoutingTable(); }
	public void clearMcastState() { this.mcast.clearMcastState(); }
	public void joinGroup(ID groupID) throws RoutingException { this.mcast.joinGroup(groupID); }
	public boolean leaveGroup(ID groupID) { return this.mcast.leaveGroup(groupID); }
	public void leaveAllGroups() { this.mcast.leaveAllGroups(); }
	public void addSpanningTreeChangedCallback(SpanningTreeChangedCallback callback) {
		this.mcast.addSpanningTreeChangedCallback(callback);
	}
	public void addMulticastCallback(McastCallback callback) {
		this.mcast.addMulticastCallback(callback);
	}
	public void multicast(ID groupID, Serializable content) throws RoutingException {
		this.mcast.multicast(groupID, content);
	}
	public ID[] getJoinedGroups() { return this.mcast.getJoinedGroups(); }
	public ID[] getGroupsWithSpanningTree() {
		return this.mcast.getGroupsWithSpanningTree();
	}
	public IDAddressPair getParent(ID groupID) { return this.mcast.getParent(groupID); }
	public IDAddressPair[] getChildren(ID groupID) { return this.mcast.getChildren(groupID); }
	public RoutingService getRoutingService() { return this.mcast.getRoutingService(); }
	public McastConfiguration getConfiguration() { return this.mcast.getConfiguration(); }
	public RoutingAlgorithmConfiguration getRoutingAlgorithmConfiguration() {
		return this.mcast.getRoutingAlgorithmConfiguration();
	}
	public IDAddressPair getSelfIDAddressPair() { return this.mcast.getSelfIDAddressPair(); }
	public void setStatCollectorAddress(String host, int port) throws UnknownHostException {
		this.mcast.setStatCollectorAddress(host, port);
	}
	public ID[] getLastKeys() { return this.mcast.getLastKeys(); }
	public RoutingResult[] getLastRoutingResults() { return this.mcast.getLastRoutingResults(); }
	public String getRoutingTableString(int verboseLevel) { return this.mcast.getRoutingTableString(verboseLevel); }
}

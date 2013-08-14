/*
 * Copyright 2006-2012 National Institute of Advanced Industrial Science
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

package ow.mcast.impl;

import java.io.IOException;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.mcast.Mcast;
import ow.mcast.McastCallback;
import ow.mcast.McastConfiguration;
import ow.mcast.SpanningTreeChangedCallback;
import ow.mcast.impl.message.AckConnectMessage;
import ow.mcast.impl.message.ConnectMessage;
import ow.mcast.impl.message.DisconnectAndRefuseMessage;
import ow.mcast.impl.message.DisconnectMessage;
import ow.mcast.impl.message.MulticastMessage;
import ow.mcast.impl.message.NackConnectMessage;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingConfiguration;
import ow.messaging.MessagingFactory;
import ow.messaging.MessagingProvider;
import ow.messaging.Signature;
import ow.routing.CallbackOnNodeFailure;
import ow.routing.CallbackOnRoute;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingAlgorithmFactory;
import ow.routing.RoutingAlgorithmProvider;
import ow.routing.RoutingException;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;
import ow.routing.RoutingServiceFactory;
import ow.routing.RoutingServiceProvider;
import ow.stat.MessagingReporter;
import ow.util.ExpiringMap;
import ow.util.ExpiringSet;
import ow.util.Timer;

/**
 * An application-level multicast (ALM) implementation.
 * Delivery trees are constructed in the same way as Scribe,
 * but message dissemination is bi-directional, not one-way from a rendezvous point.
 */
public final class McastImpl implements Mcast {
	private final static Logger logger = Logger.getLogger("mcast");

	// messages
	private Message ackConnectMessage;
	private Message nackConnectMessage;

	// members common to higher level services (DHT and Mcast)

	private McastConfiguration config;
	private RoutingAlgorithmConfiguration algoConfig;

	private RoutingService routingSvc;
	private MessageSender sender;
	private MessagingReporter msgReporter;

	private boolean stopped = false;
	private boolean suspended = false;

	// members specific to McastImpl

	private NeighborTable neighborTable;
	protected GroupSet joinedGroupSet;
	private ExpiringSet<ID> connectRefuseGroupSet;
	private ExpiringMap<ID,MessagingAddress> connectProhibitedNeighborMap;

	private static Timer timer = Timer.getSingletonTimer();
	private boolean groupRefresherRunning = false;
	private Runnable groupRefresherTask = null;

	// methods

	public McastImpl(McastConfiguration config, ID selfID /* possibly null */)
			throws Exception {
		this(Signature.getAllAcceptingApplicationID(), Signature.getAllAcceptingApplicationVersion(),
				config, selfID);
	}

	public McastImpl(short applicationID, short applicationVersion,
			McastConfiguration config, ID selfID /* possibly null */)
				throws Exception {
		// obtain messaging service
		byte[] messageSignature = Signature.getSignature(
				RoutingServiceFactory.getRoutingStyleID(config.getRoutingStyle()),
				RoutingAlgorithmFactory.getAlgorithmID(config.getRoutingAlgorithm()),
				applicationID, applicationVersion);

		MessagingProvider msgProvider = MessagingFactory.getProvider(config.getMessagingTransport(), messageSignature);
		if (config.getSelfAddress() != null) {
			msgProvider.setSelfAddress(config.getSelfAddress());
		}

		MessagingConfiguration msgConfig = msgProvider.getDefaultConfiguration();
		msgConfig.setDoUPnPNATTraversal(config.getDoUPnPNATTraversal());

		// obtain routing service
		RoutingAlgorithmProvider algoProvider = RoutingAlgorithmFactory.getProvider(config.getRoutingAlgorithm());
		RoutingAlgorithmConfiguration algoConfig = algoProvider.getDefaultConfiguration();

		RoutingServiceProvider svcProvider = RoutingServiceFactory.getProvider(config.getRoutingStyle());
		RoutingService routingSvc = svcProvider.getService(
				svcProvider.getDefaultConfiguration(),
				msgProvider, msgConfig, config.getSelfPort(), config.getSelfPortRange(),
				algoProvider, algoConfig, selfID);

		config.setSelfPort(routingSvc.getMessageReceiver().getPort());	// correct config

		// instantiate a RoutingAlgorithm in the routing service
		algoProvider.initializeAlgorithmInstance(algoConfig, routingSvc);

		init(config, routingSvc);
	}

	public McastImpl(McastConfiguration config, RoutingService routingSvc)
			throws Exception {
		init(config, routingSvc);
	}

	private void init(McastConfiguration config, RoutingService routingSvc)
			throws Exception {
		this.config = config;
		this.routingSvc = routingSvc;
		this.sender = routingSvc.getMessageSender();
		this.msgReporter = routingSvc.getMessagingReporter();
		this.algoConfig = routingSvc.getRoutingAlgorithm().getConfiguration();

		// initialize tables
		this.neighborTable = new NeighborTable(this, this.msgReporter, config.getNeighborExpiration());
		this.joinedGroupSet = new GroupSet();
		this.connectRefuseGroupSet = new ExpiringSet<ID>(config.getConnectRefuseDuration());
		this.connectProhibitedNeighborMap = new ExpiringMap<ID,MessagingAddress>(config.getConnectRefuseDuration());

		// prepare messages
		this.ackConnectMessage = new AckConnectMessage();
		this.nackConnectMessage = new NackConnectMessage();

		// initialize message handlers and callbacks
		prepareHandlers(this.routingSvc);
		prepareCallbacks(this.routingSvc);
	}

	private synchronized void startGroupRefresher() {
		if (this.groupRefresherRunning) return;
		this.groupRefresherRunning = true;

		// initialize a Refresher

		if (config.getRefreshInterval() > 0) {
			if (config.getUseTimerInsteadOfThread()) {
				this.groupRefresherTask = new GroupRefresher();
				timer.schedule(this.groupRefresherTask, Timer.currentTimeMillis(), true /*isDaemon*/);
			}
			else {
				Thread t = new Thread(new GroupRefresher());
				t.setName("Refresher");
				t.setDaemon(true);
				t.start();
			}
		}
	}

	private synchronized void stopGroupRefresher() {
		if (!this.groupRefresherRunning) return;
		this.groupRefresherRunning = false;

		if (this.groupRefresherTask != null) {
//System.out.println("[GroupRefresher stopped]");
			timer.cancel(this.groupRefresherTask);
			this.groupRefresherTask = null;
		}
	}

	public MessagingAddress joinOverlay(String hostAndPort, int defaultPort)
			throws UnknownHostException, RoutingException {
		MessagingAddress addr = this.routingSvc.getMessagingProvider().getMessagingAddress(hostAndPort, defaultPort);
		this.initialize(addr);

		return addr;
	}

	public MessagingAddress joinOverlay(String hostAndPort)
			throws UnknownHostException, RoutingException {
		MessagingAddress addr = this.routingSvc.getMessagingProvider().getMessagingAddress(hostAndPort);
		this.initialize(addr);

		return addr;
	}

	private void initialize(MessagingAddress addr)
			throws RoutingException {
		this.lastKeys[0] = this.getSelfIDAddressPair().getID();
		this.lastRoutingResults[0] = this.routingSvc.join(addr);
	}

	public void clearRoutingTable() {
		this.routingSvc.leave();

		this.lastKeys[0] = null;
		this.lastRoutingResults[0] = null;
	}

	public void clearMcastState() {
		this.neighborTable.clear();
		this.joinedGroupSet.clear();
		this.connectRefuseGroupSet.clear();
		this.connectProhibitedNeighborMap.clear();

		this.stopGroupRefresher();
	}

	//
	// methods to maintain groups
	//

	public synchronized void joinGroup(ID groupID)
			throws RoutingException {
		this.joinedGroupSet.add(groupID);

		this.lastKeys[0] = groupID;
		this.lastRoutingResults[0] =
			this.routingSvc.invokeCallbacksOnRoute(
					groupID,	/* target */
					1,		/* numNeighbors */
					null,	/* returnedValueContainer */
					0,		/* tag */
					null	/* args */);

		this.startGroupRefresher();
	}

	public synchronized boolean leaveGroup(ID groupID) {
		// remove from the group set
		// and wait for expiration to trim branches
		boolean ret = this.joinedGroupSet.remove(groupID);

		synchronized (this.neighborTable) {
			if (!this.neighborTable.hasChild(groupID)) {	// there is no child
				this.disconnectParent(groupID);
			}

			if (ret) {
				invokeSpanningTreeChangedCallbacks(groupID);
			}
		}

		if (this.joinedGroupSet.size() <= 0) {
			this.stopGroupRefresher();
		}

		return ret;
	}

	public void leaveAllGroups() {
		ID[] joinedGroups = this.joinedGroupSet.toArray();
		if (joinedGroups != null) {
			for (ID groupID: joinedGroups) {
				this.leaveGroup(groupID);
			}
		}
	}

	//
	// methods for multicast
	//

	List<SpanningTreeChangedCallback> spanningTreeChangedCallbacks = new ArrayList<SpanningTreeChangedCallback>(1);

	public void addSpanningTreeChangedCallback(SpanningTreeChangedCallback callback) {
		this.spanningTreeChangedCallbacks.add(callback);
	}

	List<McastCallback> multicastCallbacks = new ArrayList<McastCallback>(1);

	public void addMulticastCallback(McastCallback callback) {
		synchronized (this.multicastCallbacks) {
			this.multicastCallbacks.add(callback);
		}
	}

	public void multicast(ID groupID, Serializable payload)
			throws RoutingException {
		if (!this.joinedGroupSet.contains(groupID)) {	// has not joined
			this.joinGroup(groupID);
		}

		Message msg = new MulticastMessage(
				groupID, config.getMulticastTTL(), payload);

		this.floodMessage(groupID, msg, null);

		invokeMulticastCallbacks(groupID, payload);
	}

	public synchronized void stop() {
		logger.log(Level.INFO, "Mcast#stop() called.");

		this.connectRefuseGroupSet.clear();
		this.connectProhibitedNeighborMap.clear();

		// stop threads
		this.stopGroupRefresher();
		this.neighborTable.stopExpiringTask();

		// stop sub-services
		if (this.routingSvc != null) {
			this.routingSvc.stop();
			this.routingSvc = null;
		}

		this.stopped = true;
	}

	public synchronized void suspend() {
		this.connectRefuseGroupSet.clear();
		this.connectProhibitedNeighborMap.clear();

		// stop threads
		this.stopGroupRefresher();
		this.neighborTable.stopExpiringTask();

		// suspend sub-services
		this.routingSvc.suspend();

		this.suspended = true;
	}

	public synchronized void resume() {
		this.suspended = false;

		// resume sub-services
		this.routingSvc.resume();

		// start threads
		this.startGroupRefresher();
		this.neighborTable.initExpiringTask(Timer.currentTimeMillis());
	}

	//
	// methods specific to Mcast
	//

	public ID[] getJoinedGroups() {
		return this.joinedGroupSet.toArray();
	}

	public ID[] getGroupsWithSpanningTree() {
		ID[] ret = null;	

		Set<ID> groups = neighborTable.getGroupsWithSpanningTree();
		int n = groups.size();
		if (n > 0) {
			ret = new ID[n];
			groups.toArray(ret);
		}

		return ret;
	}

	public IDAddressPair getParent(ID groupID) {
		return neighborTable.getParent(groupID);
	}

	public IDAddressPair[] getChildren(ID groupID) {
		return neighborTable.getChildren(groupID);
	}

	//
	// methods are common to DHT and Mcast
	//

	public RoutingService getRoutingService() {
		return this.routingSvc;
	}

	public McastConfiguration getConfiguration() {
		return this.config;
	}

	public RoutingAlgorithmConfiguration getRoutingAlgorithmConfiguration() {
		return this.algoConfig;
	}

	public IDAddressPair getSelfIDAddressPair() {
		return this.routingSvc.getSelfIDAddressPair();
	}

	public void setStatCollectorAddress(String host, int port) throws UnknownHostException {
		MessagingAddress addr = this.routingSvc.getMessagingProvider().getMessagingAddress(host, port);

		this.routingSvc.setStatCollectorAddress(addr);
	}

	private ID[] lastKeys = new ID[1];
	public ID[] getLastKeys() { return this.lastKeys; }

	private RoutingResult[] lastRoutingResults = new RoutingResult[1];
	public RoutingResult[] getLastRoutingResults() { return this.lastRoutingResults; }

	public String getRoutingTableString(int verboseLevel) {
		return this.routingSvc.getRoutingAlgorithm().getRoutingTableString(verboseLevel);
	}

	private void prepareHandlers(RoutingService routingSvc) {
		MessageHandler handler;

		// CONNECT
		handler = new MessageHandler() {
			public Message process(Message msg) {
				final IDAddressPair selfIDAddress = McastImpl.this.getSelfIDAddressPair();
				final IDAddressPair from = (IDAddressPair)msg.getSource();
				final ID groupID = ((ConnectMessage)msg).groupID;

				Message repMsg;
				if (connectRefuseGroupSet.contains(groupID)) {
					// refuse
					logger.log(Level.INFO, "Refuse to be connected on " + selfIDAddress.getAddress() + ". group ID: " + groupID);
					repMsg = McastImpl.this.nackConnectMessage;
				}
				else {
					// accept
					Runnable r = new Runnable() {
						public void run() {
							boolean parentChanged = false;

							synchronized (neighborTable) {
								// disconnect parent if parent changes
								IDAddressPair oldParent = neighborTable.getParent(groupID);
								if (!from.equals(oldParent)) {
									if (oldParent != null) {
										disconnectParent(groupID, oldParent);
									}
								}

								// register new parent
								parentChanged = neighborTable.registerParent(groupID, from);
							}

							if (parentChanged) {
								invokeSpanningTreeChangedCallbacks(groupID);

								msgReporter.notifyStatCollectorOfConnectNodes(
										selfIDAddress.getID(), from.getID(),
										groupID.hashCode());
							}
						}
					};
					if (config.getUseTimerInsteadOfThread()) {
						timer.schedule(r, Timer.currentTimeMillis(), true /*isDaemon*/);
					}
					else {
						Thread t = new Thread(r);
						t.setName("CONNECT handler");
						t.setDaemon(true);
						t.start();
					}

					repMsg = McastImpl.this.ackConnectMessage;
				}

				return repMsg;
			}
		};
		routingSvc.addMessageHandler(ConnectMessage.class, handler);

		// DISCONNECT
		handler = new MessageHandler() {
			public Message process(final Message msg) {
				final ID groupID = ((DisconnectMessage)msg).groupID;

				synchronized (neighborTable) {
					boolean removed =
						neighborTable.removeChild(groupID, (IDAddressPair)msg.getSource());

					if (removed) {
						invokeSpanningTreeChangedCallbacks(groupID);
					}
				}

				return null;
			}
		};
		routingSvc.addMessageHandler(DisconnectMessage.class, handler);

		// DISCONNECT_AND_REFUSE
		handler = new MessageHandler() {
			public Message process(Message msg) {
				ID groupID = ((DisconnectAndRefuseMessage)msg).groupID;

				IDAddressPair source = (IDAddressPair)msg.getSource();

				connectProhibitedNeighborMap.put(groupID, source.getAddress());

				synchronized (neighborTable) {
					boolean removed =
						neighborTable.removeChild(groupID, source);

					if (removed) {
						invokeSpanningTreeChangedCallbacks(groupID);
					}
				}

				return null;
			}
		};
		routingSvc.addMessageHandler(DisconnectAndRefuseMessage.class, handler);

		// MULTICAST
		handler = new MessageHandler() {
			public Message process(Message msg) {
				ID groupID = ((MulticastMessage)msg).groupID;
				int ttl = ((MulticastMessage)msg).ttl;
				Serializable payload = ((MulticastMessage)msg).payload;

				logger.log(Level.INFO, "MULTICAST msg received. groupID: " + groupID
						+ ", ttl: " + ttl + ", from: " + ((IDAddressPair)msg.getSource()).getAddress());

				// forward
				if (ttl > 0) {
					IDAddressPair source = (IDAddressPair)msg.getSource();

					Message newMsg = new MulticastMessage(
							groupID, ttl - 1, payload);

					floodMessage(groupID, newMsg, source.getAddress());
				}

				// invoke callbacks
				invokeMulticastCallbacks(groupID, payload);

				return null;
			}
		};
		routingSvc.addMessageHandler(MulticastMessage.class, handler);
	}

	private void prepareCallbacks(RoutingService routingSvc) {
		routingSvc.addCallbackOnRoute(new CallbackOnRoute() {
			public Serializable process(final ID groupID, int tag, Serializable[] args, final IDAddressPair lastHop, final boolean onResponsibleNode) {
				if (lastHop != null) {
					Runnable r = new Runnable() {
						public void run() {
							// disconnect parent if connected
							// to avoid loop
							// because being a parent is prior to being a child
							if (onResponsibleNode) { 
								connectRefuseGroupSet.add(groupID);

								synchronized (neighborTable) {
									disconnectAndRefuseParent(groupID);
								}
							}
								// this technique solely could not prevent loop

							// connect with last hop
							// unless last hop is parent 
//							if (!lastHop.equals(neighborTable.getParent(groupID))) {
								connectWithChild(groupID, lastHop);
//							}
						}
					};

					if (config.getUseTimerInsteadOfThread()) {
						timer.schedule(r, Timer.currentTimeMillis(), true /*isDaemon*/);
					}
					else {
						Thread t = new Thread(r);
						t.setName("Connector");
						t.setDaemon(true);
						t.start();
					}
				}

				return null;
			}
		});

		routingSvc.addCallbackOnNodeFailure(new CallbackOnNodeFailure() {
			public void fail(IDAddressPair failedNode) {
				Set<ID> changedGroups = new HashSet<ID>();

				synchronized (neighborTable) {
					changedGroups.addAll(neighborTable.removeChild(failedNode));
					changedGroups.addAll(neighborTable.removeParent(failedNode));

					for (ID changedGroup: changedGroups) {
						invokeSpanningTreeChangedCallbacks(changedGroup);
					}
				}

				// TODO: maintenance ???
			}
		});
	}

	//
	// Utility methods for multicast
	//

	private void floodMessage(ID groupID, Message msg, MessagingAddress from /* could be null */) {
		logger.log(Level.INFO, "floodMessage() called on "
				+ this.getSelfIDAddressPair().getAddress() + ". from: " + from);

		// forward to parent
		IDAddressPair parentIDAddr = neighborTable.getParent(groupID);
		if (parentIDAddr != null) {
			MessagingAddress parent = parentIDAddr.getAddress();
			if (!parent.equals(from) &&
					!parent.equals(this.getSelfIDAddressPair().getAddress())) {
				try {
					this.sender.send(parent, msg);
				}
				catch (IOException e) {
					logger.log(Level.WARNING, "Faild to flood to the parent.", e);
				}
			}
		}

		// forward to children
		IDAddressPair[] children = neighborTable.getChildren(groupID);
		if (children != null) {
			for (IDAddressPair child: children) {
				MessagingAddress childAddress = child.getAddress();
				if (!childAddress.equals(from) &&
						!childAddress.equals(this.getSelfIDAddressPair().getAddress())) {
					try {
						this.sender.send(childAddress, msg);
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "Failed to flood to a child.", e);
					}
				}
			}
		}
	}

	private void invokeMulticastCallbacks(ID groupID, Serializable payload) {
		// invoke callbacks
		if (this.joinedGroupSet.contains(groupID)) {
			synchronized (this.multicastCallbacks) {
				for (McastCallback cb: this.multicastCallbacks) {
					cb.received(groupID, payload);
				}
			}
		}
	}

	//
	// Utility methods to maintain parent-child relationships
	//

	private void connectWithChild(ID groupID, IDAddressPair child) {
		MessagingAddress childAddress = child.getAddress();

		if (childAddress.equals(connectProhibitedNeighborMap.get(groupID))) {
			// prohibited to connect with the child
			return;
		}

		// send a CONNECT message
		Message connectMsg = new ConnectMessage(groupID);

		try {
			Message ackMsg = sender.sendAndReceive(childAddress, connectMsg);

			if (ackMsg instanceof AckConnectMessage) {
				logger.log(Level.INFO, "connectWithChild succeeded: " + child);

				synchronized (this.neighborTable) {
					boolean added =
						this.neighborTable.registerChild(groupID, child);

					if (added) {
						this.invokeSpanningTreeChangedCallbacks(groupID);
					}
				}
			}
			else if (ackMsg instanceof NackConnectMessage) {
				connectProhibitedNeighborMap.put(groupID, childAddress);
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to connect to " + child);
		}
	}

	protected void disconnectParent(ID groupID) {
		IDAddressPair parent = this.neighborTable.getParent(groupID);
		if (parent == null)
			return;

		disconnectParent(groupID, parent);
	}

	protected void disconnectParent(ID groupID, IDAddressPair parent) {
		IDAddressPair selfIDAddress = this.getSelfIDAddressPair();

		// anyway, remove the parent from the table
		synchronized (this.neighborTable) {
			this.neighborTable.removeParent(groupID, parent);
		}

		// send a DISCONNECT message
		Message msg = new DisconnectMessage(groupID);

		try {
			sender.send(parent.getAddress(), msg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to disconnect from the parent: " + parent);
		}

		this.msgReporter.notifyStatCollectorOfDisconnectNodes(
				selfIDAddress.getID(), parent.getID(),
				groupID.hashCode());
	}

	private void disconnectAndRefuseParent(ID groupID) {
		IDAddressPair selfIDAddress = this.getSelfIDAddressPair();

		IDAddressPair parent = this.neighborTable.getParent(groupID);
		if (parent == null)
			return;
//System.out.println("[disconnectAndRefuse: " + parent.getAddress() + "]");

		// anyway, remove the parent from the table
		synchronized (this.neighborTable) {
			this.neighborTable.removeParent(groupID, parent);

			this.invokeSpanningTreeChangedCallbacks(groupID);
		}

		// send a DISCONNECT_AND_REFUSE message
		Message msg = new DisconnectAndRefuseMessage(groupID);

		try {
			sender.send(parent.getAddress(), msg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to disconnect_and_refuse a parent: " + parent);
		}

		this.msgReporter.notifyStatCollectorOfDisconnectNodes(
				selfIDAddress.getID(), parent.getID(),
				groupID.hashCode());
	}

	protected void invokeSpanningTreeChangedCallbacks(ID groupID) {
		IDAddressPair parent = this.neighborTable.getParent(groupID);
		IDAddressPair[] children = this.neighborTable.getChildren(groupID);

		synchronized (this.spanningTreeChangedCallbacks) {
			for (SpanningTreeChangedCallback cb: this.spanningTreeChangedCallbacks) {
				cb.topologyChanged(groupID, parent, children);
			}
		}
	}

	//
	// Utility methods to maintain groups which this node has joined
	//

	//
	// Daemons
	//

	private class GroupRefresher implements Runnable {
		public void run() {
			try {
				// initial sleep
				if (!config.getUseTimerInsteadOfThread()) {
					Thread.sleep(config.getRefreshInterval());
				}

				while (true) {
					if (stopped || suspended) break;
//System.out.println("[GroupRefresher started]");

					// refresh
					ID[] groups = getJoinedGroups();

					if (groups == null) break;	// stop the Refresher

					for (ID group: groups) {
						try {
							joinGroup(group);
						}
						catch (RoutingException e) {
							logger.log(Level.WARNING, "Routing failed when joining " + group, e);
						}
					}

					// sleep
					long sleepPeriod = config.getRefreshInterval();

//System.out.println("[GroupRefresher rescheduled: " + sleepPeriod + "]");
					if (config.getUseTimerInsteadOfThread()) {
						timer.schedule(this, Timer.currentTimeMillis() + sleepPeriod, true /*isDaemon*/);
						return;
					}
					else {
						Thread.sleep(sleepPeriod);
					}
				}
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "GroupRefresher interrupted and die.", e);
			}

//System.out.println("[GroupRefresher finished]");
		}
	}
}

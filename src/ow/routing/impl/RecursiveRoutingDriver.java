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

package ow.routing.impl;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.ExtendedMessageHandler;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingConfiguration;
import ow.messaging.MessagingProvider;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingAlgorithmProvider;
import ow.routing.RoutingContext;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingServiceConfiguration;
import ow.routing.impl.message.AbstractRecRouteMessage;
import ow.routing.impl.message.NullMessage;
import ow.routing.impl.message.RecAckMessage;
import ow.routing.impl.message.RecResultMessage;
import ow.routing.impl.message.RecRouteInvokeMessage;
import ow.routing.impl.message.RecRouteJoinMessage;
import ow.routing.impl.message.RecRouteNoneMessage;
import ow.stat.MessagingReporter;
import ow.util.Timer;
import ow.util.concurrent.ExecutorBlockingMode;
import ow.util.concurrent.SingletonThreadPoolExecutors;

/**
 * A routing driver which performs recursive forwarding/routing/lookup.
 *
 * @see ow.routing.RoutingServiceProvider#getService(RoutingServiceConfiguration, MessagingProvider, ID, int, long)
 * @see ow.routing.impl.IterativeRoutingDriver
 */
public final class RecursiveRoutingDriver extends AbstractRoutingDriver {
	// messages
	private final Message recAckMessage;

	protected RecursiveRoutingDriver(RoutingServiceConfiguration conf,
			MessagingProvider msgProvider, MessagingConfiguration msgConfig, int port, int portRange,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConfig,
			ID selfID)
				throws IOException {
		super(conf, msgProvider, msgConfig, port, portRange, algoProvider, algoConfig, selfID);

		// prepare messages
		this.recAckMessage = new RecAckMessage();

		// register message handlers
		prepareHandlers();
	}

	public RoutingResult[] route(ID[] target, int numResponsibleNodeCands) {
		return route(target, null, numResponsibleNodeCands);
	}

	public RoutingResult[] route(ID[] targets, RoutingContext[] initialRoutingContexts, int numResponsibleNodeCands) {
		return route0(RecRouteNoneMessage.class,
				targets, initialRoutingContexts, numResponsibleNodeCands,
				null, -1, null,
				null);
	}

	public RoutingResult[] invokeCallbacksOnRoute(ID[] target, int numResponsibleNodeCands,
			Serializable[][] returnedValueContainer,
			int tag, Serializable[][] args) {
		return route0(RecRouteInvokeMessage.class,
				target, null, numResponsibleNodeCands,
				returnedValueContainer, tag, args,
				null);
	}

	public RoutingResult join(MessagingAddress initialContact)
			throws RoutingException {
		ID[] tgts = { this.getSelfIDAddressPair().getID() };

		RoutingResult[] res = route0(RecRouteJoinMessage.class,
				tgts, null, this.config.getNumOfResponsibleNodeCandidatesRequestedWhenJoining(),
				null, -1, null,
				initialContact);			// joinInitialContact

		if (res == null || res[0] == null)
			throw new RoutingException();

		algorithm.join(res[0].getResponsibleNodeCandidates());
			// the algorithm instance performs the joining process 

		return res[0];
	}

	// utility
	public static AbstractRecRouteMessage getRecRouteMessage(
			Class<? extends Message> msgClass,
			int routingID, ID[] target, RoutingContext[] cxt, int numRespNodeCands, IDAddressPair initiator, int ttl, RoutingHop[] route, IDAddressPair[] blackList,
			int callbackTag, Serializable[][] callbackArgs) {
		AbstractRecRouteMessage msg;

		if (msgClass.equals(RecRouteNoneMessage.class)) {
			msg = new RecRouteNoneMessage(
					routingID, target, cxt, numRespNodeCands,
					initiator, ttl, route, blackList);
		}
		else if (msgClass.equals(RecRouteJoinMessage.class)) {
			msg = new RecRouteJoinMessage(
					routingID, target, cxt, numRespNodeCands,
					initiator, ttl, route, blackList);
		}
		else {	// REC_ROUTE_INVOKE
			msg = new RecRouteInvokeMessage(
					routingID, target, cxt, numRespNodeCands,
					initiator, ttl, route, blackList,
					callbackTag, callbackArgs);
		}

		return msg;
	}

	private Map<Integer,Message> routeResultMsgTable = new HashMap<Integer,Message>();

	private RoutingResult[] route0(Class<? extends Message> msgClass,
			ID[] target, RoutingContext[] routingContexts,
			int numResponsibleNodeCands,
			Serializable[][] resultingCallbackResult, int callbackTag, Serializable[][] callbackArgs,
			MessagingAddress joinInitialContact) {
		IDAddressPair[][] nextHopCands = new IDAddressPair[target.length][];
		IDAddressPair[] blackList = null;

		if (numResponsibleNodeCands < 1) numResponsibleNodeCands = 1;

		int routingID = Thread.currentThread().hashCode();

		RoutingContext[] lastRoutingContexts = new RoutingContext[target.length];
		if (routingContexts == null) routingContexts = new RoutingContext[target.length];
		if (!msgClass.equals(RecRouteJoinMessage.class)) {
			for (int i = 0; i < target.length; i++) {
				if (routingContexts[i] == null)
					routingContexts[i] = algorithm.initialRoutingContext(target[i]);
			}
		}

		// notify messaging visualizer
		if (!msgClass.equals(RecRouteJoinMessage.class)) {
			MessagingReporter msgReporter = receiver.getMessagingReporter();

			msgReporter.notifyStatCollectorOfEmphasizeNode(this.getSelfIDAddressPair().getID());
			msgReporter.notifyStatCollectorOfMarkedID(target, 0);
		}

		// forward
		if (msgClass.equals(RecRouteJoinMessage.class)) {
			for (int i = 0; i < target.length; i++) {
				nextHopCands[i] = new IDAddressPair[1];
				nextHopCands[i][0] = IDAddressPair.getIDAddressPair(null, joinInitialContact);
			}
		}
		else {	// REC_ROUTE_NONE || REC_ROUTE_INVOKE
			for (int i = 0; i < target.length; i++) {
				nextHopCands[i] =
					algorithm.nextHopCandidates(target[i], null, false, this.config.getNumOfNextHopCandidatesRequested(), routingContexts[i]);
			}
		}

		// put a null Message as a marker
		Message nullMsg = new NullMessage();
		synchronized (this.routeResultMsgTable) {
			for (int i = 0; i < target.length; i++) {
				this.routeResultMsgTable.put(target[i].hashCode() ^ routingID, nullMsg);
			}
		}

		Message msg = RecursiveRoutingDriver.getRecRouteMessage(msgClass,
				routingID, target, routingContexts, numResponsibleNodeCands,
				this.getSelfIDAddressPair(), config.getTTL(), new RoutingHop[0], blackList,
				callbackTag, callbackArgs);

		forwardOrReturnResult(msg, lastRoutingContexts, nextHopCands);

		// wait for REC_RESULT messages
		RoutingResult[] ret = new RoutingResult[target.length];
		Set<Integer> failedIndexSet = new HashSet<Integer>();
		long sleepLimit = Timer.currentTimeMillis() + config.getRoutingTimeout();

		waitForResults:
		while (true) {
			Message resultMsg = null;
			RoutingResult[] result;

			retrieveMessage:
			while (true) {
				// peek a received message
				synchronized (this.routeResultMsgTable) {
					for (int i = 0; i < target.length; i++) {
						if (ret[i] == null) 
							resultMsg = this.routeResultMsgTable.get(target[i].hashCode() ^ routingID);

						if (resultMsg != null && !(resultMsg instanceof NullMessage)) {
							break retrieveMessage;
						}

						resultMsg = null;
					}
				}

				// sleep
				long sleepPeriod = sleepLimit - Timer.currentTimeMillis();
				if (sleepPeriod <= 0L) {
					// clean up result message table
					synchronized (this.routeResultMsgTable) {
						for (ID id: target)
							this.routeResultMsgTable.remove(id.hashCode() ^ routingID);
					}

					break waitForResults;
				}

				try {
					synchronized (nullMsg) {
						nullMsg.wait(sleepPeriod);
					}
				}
				catch (InterruptedException e) {
					sleepLimit = Timer.currentTimeMillis();
				}
			}	// retrieveMessage: while (true)

			int rtID = ((RecResultMessage)resultMsg).routingID;
			boolean succeed = ((RecResultMessage)resultMsg).succeed;
			ID[] tgt = ((RecResultMessage)resultMsg).target;
			result = ((RecResultMessage)resultMsg).routingRes;
			Serializable[] callbackResult = ((RecResultMessage)resultMsg).callbackResult;

			if (rtID != routingID) continue waitForResults;

			// prepare RoutingResult and callback results
			for (int i = 0; i < tgt.length; i++) {
				boolean match = false;

				for (int j = 0; j < target.length; j++) {
					if (tgt[i].equals(target[j])) {
						match = true;
						ret[j] = result[i];
						synchronized (this.routeResultMsgTable) {
							this.routeResultMsgTable.remove(target[j].hashCode() ^ routingID);
						}

						if (!succeed) failedIndexSet.add(j);

						if (resultingCallbackResult != null && resultingCallbackResult[j] != null) {
							resultingCallbackResult[j][0] = callbackResult[i];
						}
					}
				}

				if (!match) {
					logger.log(Level.WARNING,
							"Received REC_RESULT message is not for an expected target: " + tgt[i]);
				}
			}

			// notify the routing algorithm of nodes on the route
			for (RoutingResult res: result) {
				RoutingHop[] route = res.getRoute();

				IDAddressPair selfIDAddress = this.getSelfIDAddressPair();
				for (RoutingHop h: route) {
					IDAddressPair p = h.getIDAddressPair();
					if (p == null || selfIDAddress.equals(p)) continue;
					algorithm.touch(p);
				}
			}

			// break if filled
			boolean filled = true;
			for (int i = 0; i < target.length; i++) {
				if (ret[i] == null) filled = false;
			}
			if (filled) break;
		}	// waitForResults: while (true)

		Set<ID> noResultTarget = new HashSet<ID>();
		for (int i = 0; i < target.length; i++) {
			if (ret[i] == null) {
				noResultTarget.add(target[i]);
			}
		}
		if (!noResultTarget.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			sb.append("Could not receive a REC_RESULT message for the target");
			for (ID id: noResultTarget) {
				sb.append(" ").append(id);
			}

			logger.log(Level.WARNING, sb.toString());
		}

		for (int index: failedIndexSet) {
			ret[index] = null;
		}

		return ret;
	}

	private boolean forwardOrReturnResult(
			Message msg,
			RoutingContext[] lastRoutingContexts,
			final IDAddressPair[][] nextHopCands) {
		IDAddressPair lastHop = (IDAddressPair)msg.getSource();

		int routingID = ((AbstractRecRouteMessage)msg).routingID;
		final ID[] targets = ((AbstractRecRouteMessage)msg).target;
		RoutingContext[] routingContexts = ((AbstractRecRouteMessage)msg).cxt;
		int numResponsibleNodeCands = ((AbstractRecRouteMessage)msg).numRespNodeCands;
		IDAddressPair initiator = ((AbstractRecRouteMessage)msg).initiator;
		int ttl = ((AbstractRecRouteMessage)msg).ttl;
		RoutingHop[] route = ((AbstractRecRouteMessage)msg).route;
		IDAddressPair[] blackList = ((AbstractRecRouteMessage)msg).blackList;

		int callbackTag = -1;
		Serializable[][] callbackArgs = null;
		if (msg instanceof RecRouteInvokeMessage) {
			callbackTag = ((RecRouteInvokeMessage)msg).callbackTag;
			callbackArgs = ((RecRouteInvokeMessage)msg).callbackArgs;
		}
//{
//StringBuilder sb = new StringBuilder();
//sb.append("forRetResult called:\n");
//for (ID id: targets) sb.append(" " + id.toString(-1));
//sb.append("\n");
//sb.append("  on " + getSelfIDAddressPair().getAddress() + "\n");
//sb.append("  msg: " + msg.getName() + "\n");
//sb.append("  route:");
//for (RoutingHop h: route) sb.append(" " + h.toString(-1));
//sb.append("\n");
//System.out.print(sb.toString());
//}

		boolean ttlExpired = false;
		boolean succeed = true;
		Message newMsg;
		boolean[] forwarded = new boolean[targets.length];
		for (int i = 0; i < forwarded.length; i++) forwarded[i] = false;

		Set<IDAddressPair> blackListSet = new HashSet<IDAddressPair>();
		if (blackList != null) {
			for (IDAddressPair a: blackList) {
				blackListSet.add(a);
			}
		}

		// add this node itself to the resulting route
		RoutingHop[] lastRoute = route;
		route = new RoutingHop[lastRoute.length + 1];
		System.arraycopy(lastRoute, 0, route, 0, lastRoute.length);
		route[route.length - 1] = RoutingHop.newInstance(getSelfIDAddressPair());

		// TTL check
		if (ttl < 0) {
			StringBuilder sb = new StringBuilder();
			sb.append("TTL expired (target");
			for (ID t: targets) { sb.append(" ").append(t.toString(-1)); }
			sb.append("):");
			for (RoutingHop h: route) {
				if (h == null) break;

				sb.append(" ");
				sb.append(h.getIDAddressPair().toString(-1));
			}

			logger.log(Level.WARNING, sb.toString(), new Throwable());

			ttlExpired = true;
			if (!(msg instanceof RecRouteJoinMessage)) {	// allow joining node to succeed
				succeed = false;
			}
		}

		IDAddressPair[] nextHops = new IDAddressPair[targets.length];

		forward:
		while (true) {
			if (ttlExpired) break;

			do {	// ... } while (false)
				Set<IDAddressPair> contactSet = new HashSet<IDAddressPair>();
				boolean allContactsAreNull = true;
				boolean aContactIsNull = false;

				for (int i = 0; i < targets.length; i++) {
					if (nextHopCands[i] == null || nextHopCands[i].length <= 0) {
						nextHops[i] = null;
					}

					nextHops[i] = nextHopCands[i][0];

					if (nextHops[i] == null) continue;

					if (blackListSet.contains(nextHops[i].getAddress())) {
						// next hop is in the black list
						nextHops[i] = null;
						System.arraycopy(nextHopCands[i], 1, nextHopCands[i], 0, nextHopCands[i].length - 1);
						continue;
					}

					if (msg instanceof RecRouteJoinMessage
							&& nextHops[i].getAddress().equals(initiator.getAddress())) {
						// next hop is initiator of routing
						nextHops[i] = null;
						System.arraycopy(nextHopCands[i], 1, nextHopCands[i], 0, nextHopCands[i].length - 1);
						i--;

						logger.log(Level.WARNING, "Next hop is the joining node "
								+ initiator.getAddress()
								+ ". RoutingAlgorithm#touch() has been called too early?");

						continue;
					}

//StringBuilder sb = new StringBuilder();
//sb.append("judge to terminate[" + i + "]: " + targets[i].toString(-1) + "\n");
//sb.append("  on:          " + this.getSelfIDAddressPair().toString(-1) + ":\n");
//sb.append("  nextHop:     " + nextHops[i].toString(-1) + "\n");
//sb.append("    " + nextHops[i].getAddress().equals(this.getSelfIDAddressPair().getAddress()) + "\n");
//sb.append("  context:     " + (routingContexts[i] != null ? routingContexts[i].toString(-1) : "null") + "\n");
//sb.append("  lastContext: " + (lastRoutingContexts[i] != null ? lastRoutingContexts[i].toString(-1) : "null") + "\n");
//if (routingContexts[i] != null && lastRoutingContexts[i] != null)
//sb.append("    " + routingContexts[i].equals(lastRoutingContexts[i]) + "\n");
					if (nextHops[i].getAddress().equals(this.getSelfIDAddressPair().getAddress()) &&
							(routingContexts[i] == null ||
							 routingContexts[i].equals(lastRoutingContexts[i]))) {
						// next hop is this node itself
						nextHops[i] = null;	//terminates routing
//sb.append("    terminate.\n");
					}
//System.out.print(sb.toString());

					if (nextHops[i] != null) {
						contactSet.add(nextHops[i]);
						allContactsAreNull = false;
					}
					else {
						contactSet.add(null);
						aContactIsNull = true;
					}
				}

				if (allContactsAreNull) {	// this node is the responsible node
					break forward;
				}

				// fork
				if (contactSet.size() > 1 || aContactIsNull) {
//System.out.println("fork on " + getSelfIDAddressPair().getAddress());
					Set<Forwarder> forkedForwarder = new HashSet<Forwarder>();
					List<Integer> contactIndexList = new ArrayList<Integer>();

					for (IDAddressPair c: contactSet) {
						contactIndexList.clear();

						if (c == null) { 
							for (int i = 0; i < targets.length; i++)
								if (nextHops[i] == null) contactIndexList.add(i);
						}
						else {
							for (int i = 0; i < targets.length; i++)
								if (c.equals(nextHops[i])) contactIndexList.add(i);
						}

						int nTgts = contactIndexList.size();
						final ID[] forkedTarget = new ID[nTgts];
						final RoutingContext[] forkedRoutingContext = new RoutingContext[nTgts];
						final RoutingContext[] forkedLastRoutingContext = new RoutingContext[nTgts];
						final IDAddressPair[][] forkedNextHopCands = new IDAddressPair[nTgts][];
						for (int i = 0; i < nTgts; i++) {
							int index = contactIndexList.get(i);
							forkedTarget[i] = targets[index];
							forkedRoutingContext[i] = routingContexts[index];
							forkedLastRoutingContext[i] = lastRoutingContexts[index];
							forkedNextHopCands[i] = nextHopCands[index];
						}
						Serializable[][] forkedCallbackArgs = null;
						if (callbackArgs != null) {
							forkedCallbackArgs = new Serializable[nTgts][];
							for (int i = 0; i < nTgts; i++) {
								int index = contactIndexList.get(i);
								forkedCallbackArgs[i] = callbackArgs[index];
							}
						}
						RoutingHop[] copiedRoute = new RoutingHop[route.length];
						System.arraycopy(route, 0, copiedRoute, 0, route.length);

						Forwarder f = new Forwarder(
								RecursiveRoutingDriver.getRecRouteMessage(msg.getClass(),
										routingID, forkedTarget, forkedRoutingContext, numResponsibleNodeCands,
										initiator, ttl, copiedRoute, blackList,
										callbackTag, forkedCallbackArgs),
								forkedLastRoutingContext, forkedNextHopCands);
						forkedForwarder.add(f);
					}

					// execute
					boolean ret = true;

					if (config.getUseThreadPool()) {
						Set<Future<Boolean>> fSet = new HashSet<Future<Boolean>>();
						Forwarder firstForwarder = null;

						ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
								ExecutorBlockingMode.CONCURRENT_REJECTING, Thread.currentThread().isDaemon());

						for (Forwarder forwarder: forkedForwarder) {
							if (firstForwarder == null) { firstForwarder = forwarder; continue; }

							try {
								Future<Boolean> f = ex.submit((Callable<Boolean>)forwarder);
								fSet.add(f);
							}
							catch (RejectedExecutionException e) {
								// invoke directly if rejected
								// Note that this is required to avoid deadlocks
								ret &= forwarder.call();
							}
						}

						ret &= firstForwarder.call();	// direct invocation

						for (Future<Boolean> f: fSet) {
							try {
								ret &= f.get();
							}
							catch (Exception e) {/*ignore*/}
						}
					}
					else {
						Set<Thread> tSet = new HashSet<Thread>();
						for (Runnable r: forkedForwarder) {
							Thread t = new Thread(r);
							t.setName("Forwarder");
							t.setDaemon(Thread.currentThread().isDaemon());
							tSet.add(t);
							t.start();
						}
						for (Thread t: tSet) {
							try { t.join(); } catch (InterruptedException e) {/*ignore*/}
						}
						for (Forwarder f: forkedForwarder) {
							ret &= f.getResult();
						}
					}

					return ret;
				}	// if (contactSet.size() > 1 || aContactIsNull) {	// fork

//System.out.println("forward or reply on " + getSelfIDAddressPair().getAddress());
				IDAddressPair nextHop = nextHops[0];
					// assert: all nextHops[i].getIDAddressPair() is the same value
//System.out.println("On " + getSelfIDAddressPair().getAddress() + ", nextHop: " + nextHop);
//System.out.println("  target: " + target[0]);

				// prepare a Message
				newMsg = RecursiveRoutingDriver.getRecRouteMessage(msg.getClass(),
						routingID, targets, routingContexts, numResponsibleNodeCands,
						initiator, ttl - 1, route, blackList,
						callbackTag, callbackArgs);

				try {
					Message ack = sender.sendAndReceive(nextHop.getAddress(), newMsg);
						// throws IOException
//System.out.println("On " + getSelfIDAddressPair().getAddress() + ", forwarded " + Tag.getNameByNumber(oldMsgTag) + " from " + getSelfIDAddressPair().getAddress() + " to " + nextHop.getAddress());

					// fill ID of nextHop
					for (int i = 0; i < targets.length; i++) {
						if (nextHops[i].getID() == null) {
							// this is the case in the first iteration of joining
							nextHops[i].setID(((IDAddressPair)ack.getSource()).getID());
						}
					}

					// notify the routing algorithm
					if (algorithm != null) {
						algorithm.touch((IDAddressPair)ack.getSource());
					}

					if (ack instanceof RecAckMessage) {
						for (int i = 0; i < forwarded.length; i++) forwarded[i] = true;

						break forward;
					}
					else {
						logger.log(Level.SEVERE, "Received message is not REC_ACK.");
					}
				}
				catch (IOException e) {
//System.out.println("  failed.");
					// sending failure and try the next node
					logger.log(Level.WARNING, "Failed to forward a request to "
							+ nextHop.getAddress()
							+ " on " + getSelfIDAddressPair().getAddress(), e);
				}

				// fail to send/receive
				if (nextHop.getID() != null) {	// nextHop.getID() is null when joining
					super.fail(nextHop);

					if (blackList != null) {
						IDAddressPair[] oldBlackList = blackList;
						blackList = new IDAddressPair[oldBlackList.length + 1];
						System.arraycopy(oldBlackList, 0, blackList, 0, oldBlackList.length);
					}
					else {
						blackList = new IDAddressPair[1];
					}
					blackList[blackList.length - 1] = nextHop;

					blackListSet.add(nextHop);

					logger.log(Level.INFO, nextHop.getAddress() + " is added to blacklist on " + this.getSelfIDAddressPair().getAddress());
				}
			} while (false);

			// shift nextHopCands[i]
			shiftNextHopCands:
			for (int i = 0; i < targets.length; i++) {
				if (nextHopCands[i] == null) continue;

				System.arraycopy(nextHopCands[i], 1, nextHopCands[i], 0, nextHopCands[i].length - 1);

				for (int j = 0; j < nextHopCands[i].length; j++) {
					if (nextHopCands[i][j] != null) continue shiftNextHopCands;
				}
				nextHopCands[i] = null;
			}
		}	// forward: while (true)

		// notify the routing algorithm
		// but, do not touch initiator while it is joining
		if (lastHop != null &&
			(this.insertJoiningNodeIntoRoutingTables || !lastHop.equals(initiator)))
			algorithm.touch(lastHop);
			// this is an additional call to touch() compared with iterative lookup
		if (!this.getSelfIDAddressPair().equals(initiator) &&
			(this.insertJoiningNodeIntoRoutingTables || !(msg instanceof RecRouteJoinMessage)))
			algorithm.touch(initiator);			// initiator of the routing request

		// message dependent processes
		Serializable[] callbackResult = new Serializable[targets.length];
		if (msg instanceof RecRouteInvokeMessage) {
			// invoke callbacks
			for (int i = 0; i < targets.length; i++) {
				callbackResult[i] = invokeCallbacks(targets[i], callbackTag, callbackArgs[i], lastHop, !forwarded[i]);
				if (callbackResult[i] != null) {
					logger.log(Level.INFO, "A callback returned non-null object: " + callbackResult[i]);
				}
			}
		}
		else if (msg instanceof RecRouteJoinMessage) {
			final IDAddressPair copiedJoiningNode = initiator;
			final IDAddressPair copiedLastHop = lastHop;
			final boolean[] copiedForwarded = new boolean[forwarded.length];
			System.arraycopy(forwarded, 0, copiedForwarded, 0, copiedForwarded.length);

			Runnable r = new Runnable() {
				public void run() {
					for (int i = 0; i < targets.length; i++) {
						algorithm.join(copiedJoiningNode, copiedLastHop, !copiedForwarded[i]);
					}
				}
			};

			try {
				if (config.getUseThreadPool()) {
					ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.CONCURRENT_NON_BLOCKING, Thread.currentThread().isDaemon());
					ex.submit(r);
				}
				else {
					Thread t = new Thread(r);
					t.setName("Message type specific processes");
					t.setDaemon(Thread.currentThread().isDaemon());
					t.start();
				}
			}
			catch (OutOfMemoryError e) {
				logger.log(Level.SEVERE, "# of threads: " + Thread.activeCount(), e);

//				Thread[] tarray = new Thread[Thread.activeCount()];
//				Thread.enumerate(tarray);
//				for (Thread t: tarray) System.out.println("Th: " + t.getName());
//				System.out.flush();

				throw e;
			}
		}

		// reports the routing result to the initiator
		List<Integer> notForwardedIndexList = new ArrayList<Integer>();

		for (int i = 0; i < targets.length; i++) {
			if (!forwarded[i]) notForwardedIndexList.add(i);
		}

		if (!notForwardedIndexList.isEmpty()) {
			// get candidates for the responsible node
			ID[] partOfTarget = new ID[notForwardedIndexList.size()];
			RoutingResult[] partOfResult = new RoutingResult[notForwardedIndexList.size()];
			Serializable[] partOfCallbackResult = new Serializable[notForwardedIndexList.size()];

			for (int i = 0; i < notForwardedIndexList.size(); i++) {
				// target
				partOfTarget[i] = targets[notForwardedIndexList.get(i)];

				// routing result
				IDAddressPair[] respCands = algorithm.responsibleNodeCandidates(partOfTarget[i], numResponsibleNodeCands);

				if (msg instanceof RecRouteJoinMessage
						&& initiator.equals(respCands[0])) {
					// remove initiator from the first place on the responsible node candidates list
					IDAddressPair[] orig = respCands;
					respCands = new IDAddressPair[respCands.length - 1];
					System.arraycopy(orig, 1, respCands, 0, respCands.length);
				}

				partOfResult[i] = new RoutingResult(route, respCands);

				// callback result
				partOfCallbackResult[i] = callbackResult[notForwardedIndexList.get(i)];
			}

			// this node is the destination, or failed to send
			Message repMsg = new RecResultMessage(
					routingID, succeed, partOfTarget, partOfResult, blackList, partOfCallbackResult);

			try {
				sender.send(initiator.getAddress(), repMsg);
//System.out.println("replied from " + getSelfIDAddressPair().getAddress() + " to " + initiator.getAddress()
//+ " for " + targets[0].toString(-1) + "..");

				for (int i: notForwardedIndexList) { 
					forwarded[i] = true;
				}
			}
			catch (IOException e) {
				// sending failure
				logger.log(Level.WARNING, "Failed to report to the initiator: " + initiator.getAddress()
						+ " on " + getSelfIDAddressPair().getAddress());

				super.fail(initiator);
			}
		}	// if (!notForwardedIndexList.isEmpty())

		boolean ret = true;
		for (boolean b: forwarded) ret &= b;

		return ret;
	}

	private final class Forwarder implements Callable<Boolean>, Runnable {
		private final Message msg;
		private RoutingContext[] lastRoutingContexts;
		private final IDAddressPair[][] nextHopCands;

		private boolean result;

		Forwarder(Message msg,
				RoutingContext[] lastRoutingContexts,
				IDAddressPair[][] nextHopCands) {
			this.msg = msg;
			this.lastRoutingContexts = lastRoutingContexts;
			this.nextHopCands = nextHopCands;
		}

		public void run() {
			try {
				this.call();
			}
			catch (Exception e) {
				logger.log(Level.SEVERE, "A Querier threw an exception.", e);
			}
		}

		public Boolean call() {
			result = RecursiveRoutingDriver.this.forwardOrReturnResult(
					this.msg, this.lastRoutingContexts, this.nextHopCands);

			return result;
		}

		public boolean getResult() { return this.result; }
	}

	/**
	 * Prepare message handlers for received messages.
	 */
	private void prepareHandlers() {
		MessageHandler handler;

		// REC_ROUTE_{NONE,INVOKE,JOIN}
		handler = new ExtendedMessageHandler() {
			public Message process(Message msg) {
				return RecursiveRoutingDriver.this.recAckMessage;
			}

			public void postProcess(Message msg) {
				// parse the incoming message
				ID[] targets = ((AbstractRecRouteMessage)msg).target;
				RoutingContext[] routingContexts = ((AbstractRecRouteMessage)msg).cxt;
				IDAddressPair[] blackList = ((AbstractRecRouteMessage)msg).blackList;

				for (int i = 0; i < targets.length; i++) {
					if (routingContexts[i] == null) 
						routingContexts[i] = algorithm.initialRoutingContext(targets[i]);
						// In case of joining, routing contexts are initialized here.
				}

				// remove nodes in blacklist from routing table
				// Note: this removes nodes which this node itself has not contacted
				// and prone to be abused by a malicious node.
				if (blackList != null) {
					for (IDAddressPair p: blackList) {
						logger.log(Level.INFO, "REC_*calls fail: " + p.getAddress() + " on " + getSelfIDAddressPair().getAddress());

						fail(p);	// AbstractRoutingDriver#fail()
					}
				}

				// calculate next hop
				RoutingContext[] lastRoutingContexts = new RoutingContext[targets.length];

				boolean joining = (msg instanceof RecRouteJoinMessage);

				IDAddressPair[][] nextHopCands = new IDAddressPair[targets.length][];
				for (int i = 0; i < targets.length; i++) {
					if (routingContexts[i] != null)
						lastRoutingContexts[i] = routingContexts[i].clone();	// preserve last routing context

					nextHopCands[i] = algorithm.nextHopCandidates(
							targets[i], ((IDAddressPair)msg.getSource()).getID(), joining,
							config.getNumOfNextHopCandidatesRequested(), routingContexts[i]);
				}

				// forward
				RecursiveRoutingDriver.this.forwardOrReturnResult(
						msg, lastRoutingContexts, nextHopCands);
			}
		};
		addMessageHandler(RecRouteNoneMessage.class, handler);
		addMessageHandler(RecRouteInvokeMessage.class, handler);
		addMessageHandler(RecRouteJoinMessage.class, handler);

		// REC_RESULT
		handler = new MessageHandler() {
			public Message process(Message msg) {
				int routingID = ((RecResultMessage)msg).routingID;
				ID[] target = ((RecResultMessage)msg).target;
				IDAddressPair[] blackList = ((RecResultMessage)msg).blackList;

				synchronized (routeResultMsgTable) {
					if (target != null && target.length > 0) {
						for (ID id: target) {
							Message nullMsg = routeResultMsgTable.get(id.hashCode() ^ routingID);
							if (nullMsg != null && nullMsg instanceof NullMessage) {
								routeResultMsgTable.put(id.hashCode() ^ routingID, msg);
								synchronized (nullMsg) {
									nullMsg.notify();
								}
							}
						}
					}
				}

				// remove nodes in blacklist from routing table
				// Note: this removes nodes which this node itself has not contacted
				// and prone to be abused by a malicious node.
				if (blackList != null) {
					for (IDAddressPair p: blackList) {
						logger.log(Level.INFO, "REC_RESULT calls fail: " + p.getAddress()
								+ " on " + getSelfIDAddressPair().getAddress());

						fail(p);	// AbstractRoutingDriver#fail()
					}
				}

				// notify the routing algorithm
				//algorithm.touch(msg.getSource());
					// not necessary to call touch() because
					// the last entry in the returned route is same as msg.getSource()

				return null;
			}
		};
		addMessageHandler(RecResultMessage.class, handler);
	}
}

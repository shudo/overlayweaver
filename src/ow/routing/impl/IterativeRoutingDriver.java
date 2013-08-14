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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
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
import ow.routing.impl.message.AbstractIteRouteMessage;
import ow.routing.impl.message.IteReplyMessage;
import ow.routing.impl.message.IteRouteInvokeMessage;
import ow.routing.impl.message.IteRouteJoinMessage;
import ow.routing.impl.message.IteRouteNoneMessage;
import ow.stat.MessagingReporter;
import ow.util.concurrent.ExecutorBlockingMode;
import ow.util.concurrent.SingletonThreadPoolExecutors;

/**
 * A routing driver which performs iterative forwarding/routing/lookup.
 *
 * @see ow.routing.RoutingServiceProvider#getService(RoutingServiceConfiguration, MessagingProvider, ID, int, long)
 * @see ow.routing.impl.RecursiveRoutingDriver
 */
public final class IterativeRoutingDriver extends AbstractRoutingDriver {
	protected IterativeRoutingDriver(RoutingServiceConfiguration conf,
			MessagingProvider msgProvider, MessagingConfiguration msgConfig, int port, int portRange,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConfig,
			ID selfID)
				throws IOException {
		super(conf, msgProvider, msgConfig, port, portRange, algoProvider, algoConfig, selfID);

		// register message handlers
		prepareHandlers();
	}

	public RoutingResult[] route(ID[] targets, int numResponsibleNodeCands) {
		return route(targets, null, numResponsibleNodeCands);
	}

	public RoutingResult[] route(ID[] targets, RoutingContext[] initialRoutingContexts, int numResponsibleNodeCands) {
		return route0(IteRouteNoneMessage.class,
				targets, initialRoutingContexts, numResponsibleNodeCands,
				null, -1, null, null);
	}

	public RoutingResult[] invokeCallbacksOnRoute(ID[] target, int numResponsibleNodeCands,
			Serializable[][] returnedValue,
			int tag, Serializable[][] args) {
		return route0(IteRouteInvokeMessage.class,
				target, null, numResponsibleNodeCands,
				returnedValue, tag, args, null);
	}

	public RoutingResult join(MessagingAddress initialContact)
			throws RoutingException{
		ID[] target = { this.getSelfIDAddressPair().getID() };

		RoutingResult[] res = route0(IteRouteJoinMessage.class,
				target, null, this.config.getNumOfResponsibleNodeCandidatesRequestedWhenJoining(),
				null, -1, null, initialContact);

		if (res == null || res[0] == null)
			throw new RoutingException();

		algorithm.join(res[0].getResponsibleNodeCandidates());
			// the algorithm instance performs the joining process 

		return res[0];
	}

	private RoutingResult[] route0(
			Class<? extends Message> msgClass,
			ID[] target, RoutingContext[] routingContexts, int numResponsibleNodeCands,
			Serializable[][] resultingCallbackResult,
			int callbackTag, Serializable[][] callbackArgs, MessagingAddress joinInitialContact) {
//System.out.print("route:");
//System.out.println("  msg: " + Tag.getNameByNumber(msgType.getNumber()));
//for (ID t: target) System.out.print(" " + t.toString().substring(0, 4) + "..");
//System.out.println();
//System.out.flush();
		if (numResponsibleNodeCands < 1) numResponsibleNodeCands = 1;

		if (routingContexts == null) routingContexts = new RoutingContext[target.length];
		if (!msgClass.equals(IteRouteJoinMessage.class)) {
			for (int i = 0; i < target.length; i++) {
				if (routingContexts[i] == null)
					routingContexts[i] = algorithm.initialRoutingContext(target[i]);
			}
		}

		// calculate query concurrency
//		int queryConcurrency = this.config.getQueryConcurrency();
//		if ((!this.queryToAllContacts)
//				|| (msgType == Tag.ITE_ROUTE_JOIN)
//				|| (queryConcurrency <= 0)) {
//			queryConcurrency = 1;
//		}

		// initialize route
		List<RoutingHop>[] route = new List/*<RoutingHop>*/[target.length];
		for (int i = 0; i < target.length; i++) {
			route[i] = new ArrayList<RoutingHop>();
//			if (queryConcurrency > 2) {
//				route[i] = Collections.synchronizedList(route[i]);
//			}

			route[i].add(RoutingHop.newInstance(getSelfIDAddressPair()));
		}
		Set<MessagingAddress> blackList =
			Collections.synchronizedSet(new HashSet<MessagingAddress>());

		// notify messaging visualizer
		if (!msgClass.equals(IteRouteJoinMessage.class)) {
			MessagingReporter msgReporter = receiver.getMessagingReporter();

			msgReporter.notifyStatCollectorOfEmphasizeNode(getSelfIDAddressPair().getID());
			msgReporter.notifyStatCollectorOfMarkedID(target, 0);
		}

		// initialize contact list
		ContactList[] contactList = new ContactList[target.length];
		for (int i = 0; i < target.length; i++) {
			contactList[i] = (this.queryToAllContacts ?
				new SortedContactList(target[i], algorithm, config.getNumOfNodesMaintained()) :
				new InsertedOrderContactList());
		}

		// initial set of nodes
		if (msgClass.equals(IteRouteJoinMessage.class)) {
			contactList[0].add(IDAddressPair.getIDAddressPair(
								getSelfIDAddressPair().getID(), // temporary, cannot be null
								joinInitialContact));
		}
		else {
			for (int i = 0; i < target.length; i++) {
				IDAddressPair[] nextHopCands =
					algorithm.nextHopCandidates(target[i], null, false, config.getNumOfNextHopCandidatesRequested(), routingContexts[i]);
						// updates routingContexts[i]

				for (IDAddressPair elem: nextHopCands) {
					if (elem == null) continue;

					contactList[i].add(elem);
				}
			}
		}

		// refine contact list iteratively

		// query
		RoutingResultTable<IDAddressPair[]> responsibleNodeCandsTable =
			new RoutingResultTable<IDAddressPair[]>();
		RoutingResultTable<Serializable> callbackResultTable = null;
		if (msgClass.equals(IteRouteInvokeMessage.class))
			callbackResultTable = new RoutingResultTable<Serializable>();

//		if (queryConcurrency <= 1) {
		if (true) {
			// serial query
			Querier querier = new Querier(msgClass,
					target, routingContexts, numResponsibleNodeCands,
					config.getTTL(), route,
					contactList, null, blackList, joinInitialContact,
					callbackTag,
					callbackArgs, responsibleNodeCandsTable,
					callbackResultTable);

			Future<Boolean> f = null;
			Thread t = null;
			boolean timeout = false;

			try {
				// invoke a Thread to timeout
				if (config.getUseThreadPool()) {
					ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.CONCURRENT_NON_BLOCKING, Thread.currentThread().isDaemon());
					f = ex.submit((Callable<Boolean>)querier);

					f.get(config.getRoutingTimeout(), TimeUnit.MILLISECONDS);
				}
				else {
					t = new Thread(querier);
					t.setName("Querier");
					t.setDaemon(Thread.currentThread().isDaemon());
					t.start();

					t.join(config.getRoutingTimeout());	// does not throw TimeoutException

					if (t.isAlive()) {
						// timed out
						timeout = true;
					}
				}
			}
			catch (ExecutionException e) {
				Throwable cause = e.getCause();
				logger.log(Level.WARNING, "A Querier threw an Exception.", (cause != null ? cause : e));
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "Querier#call() interrupted on " + getSelfIDAddressPair().getAddress());
			}
			catch (TimeoutException e) {	// timed out
				logger.log(Level.WARNING, "Routing timeout on " + getSelfIDAddressPair().getAddress());
				timeout = true;
			}
			catch (OutOfMemoryError e) {
				logger.log(Level.SEVERE, "# of threads: " + Thread.activeCount(), e);
				throw e;
			}

			if (timeout) {
				// interrupt a Querier
				if (f != null)
					f.cancel(true);
				else if (t != null)
					t.interrupt();

				responsibleNodeCandsTable.clear();
			}

			for (int i = 0; i < target.length; i++) {
				IDAddressPair contact = contactList[i].first();

				if (msgClass.equals(IteRouteJoinMessage.class)) {
					while (contact != null && getSelfIDAddressPair().equals(contact)) {
						// joining and destination is this node itself
						// it happens in case that this.queryToAllContacts is true.
						contactList[i].remove(contact);
						contact = contactList[i].first();
					}
				}
			}
		}
// concurrent queries disabled to implement collective routing (April 17, 2008)
//		else {
//			// concurrent queries
//			...
//		}	// if (queryConcurrency <= 1)

		// return
		RoutingResult[] ret = new RoutingResult[target.length];
		for (int i = 0; i < target.length; i++) {
			IDAddressPair goal;
			if (this.queryToAllContacts)
				goal = contactList[i].first();	// required for Kademlia
			else
				goal = route[i].get(route[i].size() - 1).getIDAddressPair();

			// message dependent processes
			boolean isFinalNode = getSelfIDAddressPair().equals(goal);

			if (msgClass.equals(IteRouteInvokeMessage.class)) {
				Serializable res =
					invokeCallbacks(target[i], callbackTag, callbackArgs[i], null, isFinalNode);

				callbackResultTable.put(target[i], getSelfIDAddressPair(), res);
			}
			else if (msgClass.equals(IteRouteJoinMessage.class)) {
				algorithm.join(this.getSelfIDAddressPair(), null, isFinalNode);
			}

			// prepare RoutingResult and callback results
			IDAddressPair[] respCands = responsibleNodeCandsTable.get(target[i], goal);
			if (respCands != null) {
				RoutingHop[] routeArray = new RoutingHop[route[i].size()];
				route[i].toArray(routeArray);

				ret[i] = new RoutingResult(routeArray, respCands);

				if (callbackResultTable != null
						&& resultingCallbackResult != null && resultingCallbackResult[i] != null) {
					resultingCallbackResult[i][0] = callbackResultTable.get(target[i], goal);
				}
			}
		}

		return ret;
	}

	private final class Querier implements Runnable, Callable<Boolean> {
		private final Class<? extends Message> msgClass;
		private ID[] target;
		private RoutingContext[] routingContexts;
		private final int numResponsibleNodeCands;

		private int ttl;

		private final List<RoutingHop>[] route;
		private ContactList[] contactList;
		private IDAddressPair[] lastContacts;
		private final Set<MessagingAddress> blackList;

		// for ITE_ROUTE_JOIN
		private final MessagingAddress joinInitialContact;

		// for ITE_ROUTE_INVOKE
		private final int callbackTag;
		private final Serializable[][] callbackArgs;

		// routing results tables
		private final RoutingResultTable<IDAddressPair[]> respCandsTable;
		private final RoutingResultTable<Serializable> callbackResultTable;

		Querier(Class<? extends Message> msgClass,
				ID[] target, RoutingContext[] routingContexts, int numResponsibleNodeCands,
				int ttl, List<RoutingHop>[] route,
				ContactList[] contactList, IDAddressPair[] lastContacts, Set<MessagingAddress> blackList, MessagingAddress joinInitialContact,
				int callbackTag,
				Serializable[][] callbackArgs, RoutingResultTable<IDAddressPair[]> responsibleNodeCandsTable,
				RoutingResultTable<Serializable> callbackResultTable) {
			this.msgClass = msgClass;
			this.target = target;
			this.routingContexts = routingContexts;
			this.numResponsibleNodeCands = numResponsibleNodeCands;

			this.ttl = ttl;

			this.route = route;
			this.contactList = contactList;
			this.lastContacts = lastContacts;
			this.blackList = blackList;

			this.joinInitialContact = joinInitialContact;

			this.callbackTag = callbackTag;
			this.callbackArgs = callbackArgs;

			this.respCandsTable = responsibleNodeCandsTable;
			this.callbackResultTable = callbackResultTable;
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
//System.out.println("Querier#call on " + getSelfIDAddressPair().getAddress());
			boolean succeed = true;

			IDAddressPair[] contacts = new IDAddressPair[this.target.length];
			IDAddressPair[] lastContacts = this.lastContacts;
			RoutingContext[] lastRoutingContexts = new RoutingContext[this.target.length];
			int ttl = this.ttl;

			if (lastContacts == null) lastContacts = new IDAddressPair[this.target.length];

			// iterative routing
			iterativeRouting:
			while (true) {
				IDAddressPair[][] nextHopCands = null;

				// TTL check
				if (ttl <= 0) {
					StringBuilder sb = new StringBuilder();
					sb.append("TTL expired (target");
					for (ID t: this.target) { sb.append(" ").append(t.toString(-1)); }
					sb.append("):");
					for (RoutingHop n: this.route[0]) {
						sb.append(" ");
						sb.append(n.getIDAddressPair().toString(-1));
					}

					logger.log(Level.WARNING, sb.toString(), new Throwable());

					if (!this.msgClass.equals(IteRouteJoinMessage.class)) { // allow joining node to succeed
						succeed = false;

						for (int i = 0; i < target.length; i++) {
							this.respCandsTable.clear(target[i]);

							if (this.callbackResultTable != null)
								this.callbackResultTable.clear(target[i]);
						}
					}

					break iterativeRouting;
				}

				ttl--;

				// preserve last contacts and last routing contexts
				for (int i = 0; i < target.length; i++) {
					lastContacts[i] = contacts[i];
				}

				// query
				Message replyMsg = null;
				do {	// while (replyMsg == null)
					Set<IDAddressPair> contactSet = new HashSet<IDAddressPair>();
					boolean allContactsAreNull = true;
					boolean aContactIsNull = false;

					for (int i = 0; i < this.target.length; i++) {
						contacts[i] = (IterativeRoutingDriver.this.queryToAllContacts ?
								this.contactList[i].inspectExceptContactedNode() :
								this.contactList[i].inspect());


						if (contacts[i] != null) {
//StringBuilder sb = new StringBuilder();
//sb.append("judge to terminate[" + i + "]:\n");
//sb.append("  contacts:     " + (contacts[i] != null ? contacts[i].toString(-1) : "null") + "\n");
//sb.append("  lastContacts: " + (lastContacts[i] != null ? lastContacts[i].toString(-1) : "null") + "\n");
//sb.append("  context:      " + (this.routingContexts[i] != null ? this.routingContexts[i].toString(-1) : "null") + "\n");
//sb.append("  lastContext:  " + (lastRoutingContexts[i] != null ? lastRoutingContexts[i].toString(-1) : "null") + "\n");
							if (contacts[i].equals(lastContacts[i]) &&
									(this.routingContexts[i] == null ||
									 this.routingContexts[i].equals(lastRoutingContexts[i]))) {
//sb.append("    terminate.\n");
								contacts[i] = null;
							}
							else if (blackList.contains(contacts[i].getAddress())) {
								contactList[i].remove(contacts[i]);
								continue;
							}
//System.out.print(sb.toString());
						}

						if (contacts[i] != null) {
							contactSet.add(contacts[i]);
							allContactsAreNull = false;
						}
						else {
							contactSet.add(null);
							aContactIsNull = true;
						}

						// initialize lastContact
						if (lastContacts[i] == null) lastContacts[i] = getSelfIDAddressPair();
					}

					if (allContactsAreNull) break iterativeRouting;	// finish

					// fork
					if (contactSet.size() > 1 || aContactIsNull) {
						Map<IDAddressPair,Querier> forkedQueriers =
							new HashMap<IDAddressPair,Querier>();

						List<Integer> contactIndexList = new ArrayList<Integer>();

						for (IDAddressPair c: contactSet) {
							contactIndexList.clear();

							for (int i = 0; i < this.target.length; i++) {
								if (c == null) {
									if (contacts[i] == null) {
										contactIndexList.add(i);
									}
								}
								else if (c.equals(contacts[i])) {
									contactIndexList.add(i);
								}
							}

							int nTgts = contactIndexList.size();
							ID[] forkedTarget = new ID[nTgts];
							RoutingContext[] forkedRoutingContext = new RoutingContext[nTgts];
							List<RoutingHop>[] forkedRoute = new List/*<RoutingHop>*/[nTgts];
							ContactList[] forkedContactList = new ContactList[nTgts];
							IDAddressPair[] forkedLastContact = new IDAddressPair[nTgts];
							for (int i = 0; i < nTgts; i++) {
								int index = contactIndexList.get(i);
								forkedTarget[i] = this.target[index];
								if (this.routingContexts[index] != null)
									forkedRoutingContext[i] = this.routingContexts[index].clone();
								forkedRoute[i] = this.route[index];
								forkedContactList[i] = this.contactList[index];
								forkedLastContact[i] = lastContacts[index];
							}
							Serializable[][] forkedCallbackArgs = null;
							if (callbackArgs != null) {
								forkedCallbackArgs = new Serializable[nTgts][];
								for (int i = 0; i < nTgts; i++) {
									int index = contactIndexList.get(i);
									forkedCallbackArgs[i] = callbackArgs[index];
								}
							}

							Querier q = new Querier(this.msgClass,
									forkedTarget, forkedRoutingContext, this.numResponsibleNodeCands,
									this.ttl, forkedRoute,
									forkedContactList, forkedLastContact, this.blackList, this.joinInitialContact,
									this.callbackTag,
									forkedCallbackArgs, this.respCandsTable,
									this.callbackResultTable);
							forkedQueriers.put(c, q);
						}

						// execute
						boolean querierResult = true;
						Set<Future<Boolean>> fSet = null;
						Set<Thread> tSet = null;

						try {
							if (config.getUseThreadPool()) {
								fSet = new HashSet<Future<Boolean>>();
								Querier firstQuerier = null;

								ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
										ExecutorBlockingMode.CONCURRENT_REJECTING, Thread.currentThread().isDaemon());

								for (Querier q: forkedQueriers.values()) {
									if (firstQuerier == null) { firstQuerier = q; continue; }

									try {
										Future<Boolean> f = ex.submit((Callable<Boolean>)q);
										fSet.add(f);
									}
									catch (RejectedExecutionException e) {
										// invoke directly if rejected
										// Note that this is required to avoid deadlocks
										querierResult &= firstQuerier.call();
									}
								}

								querierResult &= firstQuerier.call();	// direct invocation (an optimization)

								for (Future<Boolean> f: fSet) {
									querierResult &= f.get();
								}
								// Note that this Querier can be blocked here
								// in case that the number of threads is limited.
								// Above ...submit()s consume a thread and this Querier own also keeps a thread.
								// If threads runs out, a submitted Querier cannot run and f.get() is blocked.
							}
							else {
								tSet = new HashSet<Thread>();
								Querier firstQuerier = null;

								for (Querier q: forkedQueriers.values()) {
									if (firstQuerier == null) { firstQuerier = q; continue; }

									Thread t = new Thread(q);
									t.setName("Querier");
									t.setDaemon(Thread.currentThread().isDaemon());
									tSet.add(t);
									t.start();
								}

								firstQuerier.call();	// direct invocation (an optimization)

								for (Thread t: tSet) t.join();
							}
						}
						catch (ExecutionException e) {
							Throwable cause = e.getCause();
							logger.log(Level.WARNING, "A Querier threw an Exception.", (cause != null ? cause : e));
							querierResult = false;
						}
						catch (InterruptedException e) {
							logger.log(Level.WARNING, "Querier#call() interrupted on " + getSelfIDAddressPair().getAddress());
							querierResult = false;

							// interrupt sub Queriers
							if (fSet != null)
								for (Future<Boolean> f: fSet) f.cancel(true);
							else if (tSet != null)
								for (Thread t: tSet) t.interrupt();
						}
						catch (OutOfMemoryError e) {
							logger.log(Level.SEVERE, "# of threads: " + Thread.activeCount(), e);
							throw e;
						}

						return querierResult;
					}	// if (contactSet.size() > 1)	// fork

					// preserve last routing contexts
					for (int i = 0; i < target.length; i++) {
						if (this.routingContexts[i] != null) 
							lastRoutingContexts[i] = this.routingContexts[i].clone();
						else
							lastRoutingContexts[i] = null;
					}

					// update contact
					for (int i = 0; i < this.target.length; i++) {
						contacts[i] = (IterativeRoutingDriver.this.queryToAllContacts ?
								this.contactList[i].firstExceptContactedNode() :
								this.contactList[i].first());
					}

					// in case of join,
					// skip the next hop if it is the destination (this node itself)
					if (this.msgClass.equals(IteRouteJoinMessage.class)
							&& getSelfIDAddressPair().getAddress().equals(contacts[0].getAddress())) {
						// remove
						contactList[0].remove(contacts[0]);

						// update next contact
						contacts[0] = (IterativeRoutingDriver.this.queryToAllContacts ?
								this.contactList[0].inspectExceptContactedNode() :
								this.contactList[0].inspect());

						if (contacts[0].equals(lastContacts[0]) &&
								(this.routingContexts[0] == null ||
								 this.routingContexts[0].equals(lastRoutingContexts[0])))
							contacts[0] = null;

						if (contacts[0] == null)
							break iterativeRouting;

						continue;
					}

					// send a query and receive a reply
					Message requestMsg = null;
					if (this.msgClass.equals(IteRouteNoneMessage.class)) {
						requestMsg = new IteRouteNoneMessage(
								this.target, this.routingContexts, lastContacts, config.getNumOfNextHopCandidatesRequested(), numResponsibleNodeCands);
					}
					else if (this.msgClass.equals(IteRouteInvokeMessage.class)) {
						requestMsg = new IteRouteInvokeMessage(
								this.target, this.routingContexts, lastContacts, config.getNumOfNextHopCandidatesRequested(), numResponsibleNodeCands,
								this.callbackTag, this.callbackArgs);
					}
					else {	// ITE_ROUTE_JOIN
						requestMsg = new IteRouteJoinMessage(
								this.target, this.routingContexts, lastContacts, config.getNumOfNextHopCandidatesRequested(), numResponsibleNodeCands);
					}

					replyMsg = null;
					try {
						replyMsg = sender.sendAndReceive(contacts[0].getAddress(), requestMsg);
										// throws IOException
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "Sending or receiving failed: "
								+ contacts[0].getAddress()
								+ " on " + getSelfIDAddressPair().getAddress());
					}

					if (replyMsg != null) {
						// fill ID of contact
						for (int i = 0; i < target.length; i++){
							synchronized (contacts[i]) {
								if (getSelfIDAddressPair().getID().equals(contacts[i].getID()) &&
									!getSelfIDAddressPair().getAddress().equals(contacts[i].getAddress())) {
									// this is the case in the first iteration of joining
									contacts[i].setID(((IDAddressPair)replyMsg.getSource()).getID());

									contactList[i].addAsContacted(contacts[i]);
								}
							}
						}

						// notify the routing algorithm
						algorithm.touch((IDAddressPair)replyMsg.getSource());
							// should be lazy (delayed and/or unified)?

						if (!(replyMsg instanceof IteReplyMessage)) {
							logger.log(Level.SEVERE, "Received message is not ITE_REPLY: " + replyMsg.getName());

							replyMsg = null;
							break;
						}

						// parse the reply
						nextHopCands = ((IteReplyMessage)replyMsg).nextHopCandidates;
						IDAddressPair[][] respCands = ((IteReplyMessage)replyMsg).responsibleNodeCands;
						routingContexts = ((IteReplyMessage)replyMsg).routingContexts;
						Serializable[] callbackResult = ((IteReplyMessage)replyMsg).callbackResult;

						for (int i = 0; i < target.length; i++) {
							this.respCandsTable.put(target[i], (IDAddressPair)replyMsg.getSource(), respCands[i]);

							if (this.callbackResultTable != null)
								this.callbackResultTable.put(target[i], (IDAddressPair)replyMsg.getSource(), callbackResult[i]);
						}

						break;
					}
					else {
						// communication failure
						if (contacts[0].getID() != null) { // In an initial join iteration, ID is null.
							IterativeRoutingDriver.super.fail(contacts[0]);
								// tell the algorithm of a failure of the best node

							this.blackList.add(contacts[0].getAddress());
						}

						for (int i = 0; i < target.length; i++) {
							synchronized (this.contactList[i]) {
								this.contactList[i].remove(contacts[i]);
							}
						}
					}
				} while (replyMsg == null);	// query

				for (int i = 0; i < target.length; i++) {
					this.route[i].add(RoutingHop.newInstance(contacts[0]));
				}

				// add the nodes in the reply to contact list
				for (int i = 0; i < target.length; i++) {
					if (nextHopCands[i] != null) {
						synchronized (this.contactList[i]) {
							if (!IterativeRoutingDriver.this.queryToAllContacts) {
								// refresh contact list
								this.contactList[i].clear();
							}

							for (IDAddressPair p: nextHopCands[i]) {
								if (p == null || this.blackList.contains(p.getAddress())
									|| this.msgClass.equals(IteRouteJoinMessage.class) && getSelfIDAddressPair().equals(p)) continue;

								this.contactList[i].add(p);
							}
						}
					}
				}
			}	// iterativeRouting: while

			return succeed;
		}
	}

	private final static class RoutingResultTable<V> {
		private final class Entry {
			private final ID target; private final IDAddressPair node;
			Entry(ID target, IDAddressPair node) { this.target = target; this.node = node; }
			public int hashCode() { return this.target.hashCode() ^ this.node.hashCode(); }
			public boolean equals(Object o) {
				if (o == null || !(o instanceof RoutingResultTable<?>.Entry)) return false;
				Entry ent = (Entry)o;
				return this.target.equals(ent.target) && this.node.equals(ent.node);
			} 
		}

		private Map<Entry,V> table = new HashMap<Entry,V>();

		public synchronized void put(ID target, IDAddressPair node, V v) {
			this.table.put(new Entry(target, node), v);
		}

		public synchronized V get(ID target, IDAddressPair node) {
			return this.table.get(new Entry(target, node));
		}

		public synchronized void clear(ID target) {
			Set<Entry> keySet = this.table.keySet();
			Entry[] keyArray = new RoutingResultTable/*<V>*/.Entry[keySet.size()];
			this.table.keySet().toArray(keyArray);

			for (Entry e: keyArray) {
				if (target.equals(e.target)) {
					this.table.remove(e);
				}
			}
		}

		public synchronized void clear() { this.table.clear(); }
	}

	/**
	 * Prepare message handlers for received messages.
	 */
	private void prepareHandlers() {
		MessageHandler handler;

		// ITE_ROUTE_{NONE,INVOKE,JOIN}
		handler = new MessageHandler() {
			public Message process(final Message msg) {
				// parse the Message
				final ID[] targets;
				RoutingContext[] routingContexts = null;
				IDAddressPair[] lastHops = null;
				int numNextHopCands = 1, numResponsibleNodeCands = 1;
				int tag = -1;
				Serializable[][] args = null;
				IDAddressPair joiningNode = (IDAddressPair)msg.getSource();

				targets = ((AbstractIteRouteMessage)msg).target;
				routingContexts = ((AbstractIteRouteMessage)msg).cxt;
				lastHops = ((AbstractIteRouteMessage)msg).lastHop;
				numNextHopCands = ((AbstractIteRouteMessage)msg).numNextHopCandidates;
				numResponsibleNodeCands = ((AbstractIteRouteMessage)msg).numRespNodeCands;
				if (msg instanceof IteRouteInvokeMessage) {
					tag = ((IteRouteInvokeMessage)msg).callbackTag;
					args = ((IteRouteInvokeMessage)msg).callbackArgs;
				}

				for (int i = 0; i < targets.length; i++) {
					if (routingContexts[i] == null) 
						routingContexts[i] = algorithm.initialRoutingContext(targets[i]);
						// In case of joining, routing contexts are initialized here.
				}

				// routing
				boolean[] isFinalNode = new boolean[targets.length];

				IDAddressPair[][] nextHopCands = new IDAddressPair[targets.length][];

				boolean joining = (msg instanceof IteRouteJoinMessage);

				for (int i = 0; i < targets.length; i++) {
					RoutingContext lastContext = null;
					if (routingContexts[i] != null)
						lastContext = routingContexts[i].clone();	// preserve to compare later

					nextHopCands[i] = algorithm.nextHopCandidates(targets[i],
							(lastHops[i] != null ? lastHops[i].getID() : null),
							joining, numNextHopCands, routingContexts[i]);
						// updates routingContexts[i]

					isFinalNode[i] =
						(nextHopCands[i] == null ||
						 nextHopCands[i].length <= 0 ||
						 nextHopCands[i][0] == null ||
						 nextHopCands[i][0].equals(getSelfIDAddressPair())) &&
						(routingContexts[i] == null ||
						 routingContexts[i].equals(lastContext));
//System.out.println("isFinalNode: " + isFinalNode[i]);
//System.out.println("  self   : " + getSelfIDAddressPair());
//for (int j = 0; j < nextHopCands[i].length; j++) {
//	if (nextHopCands[i][j] != null)
//		System.out.println("  nextHop[" + j + "]: " + nextHopCands[i][i]);
//}
				}	// for (int i = 0; i < target.length; i++)

				// notify the routing algorithm
				// but, do not touch the message sender while it is joining
				if (IterativeRoutingDriver.this.insertJoiningNodeIntoRoutingTables || !joining) {
					algorithm.touch((IDAddressPair)msg.getSource());
				}
				if (lastHops != null) {
					for (IDAddressPair p: lastHops) {
						if (p == null || p.equals(getSelfIDAddressPair()) ||
							(!IterativeRoutingDriver.this.insertJoiningNodeIntoRoutingTables && joining && p.equals((IDAddressPair)msg.getSource()))) continue;
						algorithm.touch(p);
					}
				}

				// message type specific process
				Serializable[] callbackResult = new Serializable[targets.length];
				if (msg instanceof IteRouteInvokeMessage) {
					// invoke callbacks
					for (int i = 0; i < targets.length; i++) {
						callbackResult[i] = invokeCallbacks(targets[i], tag, args[i], lastHops[i], isFinalNode[i]);
						if (callbackResult[i] != null) {
							logger.log(Level.INFO, "A callback returned non-null object: " + callbackResult);
						}
					}
				}
				else if (msg instanceof IteRouteJoinMessage) {
					final IDAddressPair copiedJoiningNode = joiningNode;
					final IDAddressPair[] copiedLastHop = new IDAddressPair[lastHops.length];
					System.arraycopy(lastHops, 0, copiedLastHop, 0, lastHops.length);
					final boolean[] copiedIsFinalNode = isFinalNode;
					Runnable r = new Runnable() {
						public void run() {
							for (int i = 0; i < copiedLastHop.length; i++) {
								algorithm.join(copiedJoiningNode, copiedLastHop[i], copiedIsFinalNode[i]);
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

//						Thread[] tarray = new Thread[Thread.activeCount()];
//						Thread.enumerate(tarray);
//						for (Thread t: tarray) System.out.println("Th: " + t.getName());
//						System.out.flush();

						throw e;
					}
				}

				// get responsible node candidates
				IDAddressPair[][] responsibleNodeCands = new IDAddressPair[targets.length][];
				for (int i = 0; i < targets.length; i++)
					responsibleNodeCands[i] = algorithm.responsibleNodeCandidates(targets[i], numResponsibleNodeCands);

				return new IteReplyMessage(nextHopCands, responsibleNodeCands, routingContexts, callbackResult);
			}
		};
		addMessageHandler(IteRouteNoneMessage.class, handler);
		addMessageHandler(IteRouteInvokeMessage.class, handler);
		addMessageHandler(IteRouteJoinMessage.class, handler);
	}
}

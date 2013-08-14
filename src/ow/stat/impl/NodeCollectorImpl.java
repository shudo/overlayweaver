/**
 * Copyright 2007-2009,2011 Kazuyuki Shudo, and contributors.
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

package ow.stat.impl;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.routing.impl.message.RepNeighborsMessage;
import ow.routing.impl.message.ReqNeighborsMessage;
import ow.stat.NodeCollector;
import ow.stat.NodeCollectorCallback;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;
import ow.util.concurrent.ExecutorBlockingMode;
import ow.util.concurrent.SingletonThreadPoolExecutors;

/**
 * An utility this class collects all nodes on an overlay.
 */
public class NodeCollectorImpl implements NodeCollector {
	private final static Logger logger = Logger.getLogger("statcollector");

	private NodeCollectorCallback callback;
	private MessageSender sender;
	private StatConfiguration config;

	private int numOfCollectionThread;

	private Set<IDAddressPair> contactNodes, processedNodes, runningNodes, failedNodes;
	// Relationships between those sets:
	// all_nodes = (contactNodes || processedNodes), contactNodes && processedNodes = null,
	// processedNodes )= (runningNodes || failedNodes), runningNodes && failedNodes = null

	public NodeCollectorImpl(
			MessagingAddress initialContact, NodeCollectorCallback cb,
			StatConfiguration config,
			MessageReceiver receiver) throws Exception {
		this.callback = cb;

		if (config != null) {
			this.config = config;
		}
		else {
			this.config = StatFactory.getDefaultConfiguration();
		}

		if (receiver == null) {
			MessagingProvider msgProvider = this.config.deriveMessagingProvider();
			receiver = this.config.deriveMessageReceiver(msgProvider);
		}

		this.sender = receiver.getSender();

		this.numOfCollectionThread = 0;

		// initialize a set of nodes
		contactNodes = new HashSet<IDAddressPair>();
		processedNodes = new HashSet<IDAddressPair>();
		runningNodes = new HashSet<IDAddressPair>();
		failedNodes = new HashSet<IDAddressPair>();

		IDAddressPair[] neighbors = requestNeighbors(initialContact,
				this.config.getNumOfNodesNodeCollectorRequests());	// throws IOException

		if (neighbors != null) {
			synchronized (contactNodes) {
				for (IDAddressPair n: neighbors) {
//System.out.println("initial contact: " + n);
					contactNodes.add(n); }
			}
		}
	}

	public Set<IDAddressPair> investigate() {
		// initialize sets
		synchronized (runningNodes) {
			synchronized (contactNodes) {
				contactNodes.addAll(runningNodes);
			}
		}

		// eager collection
		this.investigate0();

		return this.runningNodes;
	}

	private void investigate0() {
		// initialize sets
		processedNodes.clear();
		failedNodes.clear();

		// eager collection
		while (true) {
			IDAddressPair contact = null;

			synchronized (contactNodes) {
				for (IDAddressPair p: contactNodes) { contact = p; break; }

				if (contact != null) {
					contactNodes.remove(contact);
					processedNodes.add(contact);

					numOfCollectionThread++;
//System.out.println("num++: " + numOfCollectionThread);

					SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.CONCURRENT_NON_BLOCKING, true).submit(new Collector(contact));
				}
				else {
					if (numOfCollectionThread <= 0) break;

					try {
						contactNodes.wait();
					}
					catch (InterruptedException e) {}
				}
			}
		}
	}

	private int threadCount = 1;

	private class Collector implements Runnable {
		private final IDAddressPair contact;

		private Collector(IDAddressPair contact) {
			this.contact = contact;
		}

		public void run() {
			Thread th = Thread.currentThread();
			String origName = th.getName();
			th.setName("NodeCollector-" + (NodeCollectorImpl.this.threadCount++));

//System.out.println("contact: " + this.contact.getAddress() + " on " + Thread.currentThread());
			IDAddressPair[] neighbors = null;
			neighbors = requestNeighbors(this.contact.getAddress(),
					config.getNumOfNodesNodeCollectorRequests());

			synchronized (runningNodes) {
				if (neighbors != null) {
					runningNodes.add(this.contact);

					callback.addNode(this.contact.getID(), this.contact.getAddress());
				}
				else {
					failedNodes.add(this.contact);

					callback.removeNode(this.contact.getID());
				}

				synchronized (contactNodes) {
					if (neighbors != null) {
						boolean added = false;

						for (IDAddressPair n: neighbors) {
							if (n != null && !processedNodes.contains(n)) {
//if (!contacts.contains(n)) System.out.println("  new node found: " + n.getAddress());
								added |= contactNodes.add(n);
							}
						}

						if (added) contactNodes.notify();
					}
				}
			}

			synchronized (contactNodes) {
				numOfCollectionThread--;
//System.out.println("num--: " + numOfCollectionThread);

				if (numOfCollectionThread <= 0) {
					contactNodes.notify();
				}
			}

			th.setName(origName);
		}
	}

	public void run() {
		while (true) {
			// eager collection
//System.out.println("eager collection.");
			this.investigate0();

			// periodic collection
//System.out.println("periodic collection.");
			Random rnd = new Random();

			while (true) {
				// sleep
				try {
					Thread.sleep(this.config.getPeriodicCollectionInterval());
				}
				catch (InterruptedException e) { /* ignore */ }

				// choose a living node
				IDAddressPair[] livNodeArray;
				int size;
				synchronized (runningNodes) {
					size = runningNodes.size();
					livNodeArray = new IDAddressPair[size];
					runningNodes.toArray(livNodeArray);
				}

				int index = rnd.nextInt(size);
				IDAddressPair contact = livNodeArray[index];

//System.out.println("contact (periodic): " + contact.getAddress());
				IDAddressPair[] neighbors = requestNeighbors(contact.getAddress(),
						this.config.getNumOfNodesNodeCollectorRequests());

				synchronized (runningNodes) {
					if (neighbors == null) {
						runningNodes.remove(contact);
						failedNodes.add(contact);

						this.callback.removeNode(contact.getID());
					}

					synchronized (contactNodes) {
						if (neighbors != null) {
							for (IDAddressPair n: neighbors) {
								if (n != null && !processedNodes.contains(n)) {
									contactNodes.add(n);
//System.out.println("  new node found: " + n);
								}
							}
						}
						else {
							processedNodes.remove(contact);
						}

						if (!contactNodes.isEmpty()) break;	// go to eager collection
					}
				}
			}	// periodic collection
		}
	}

	private IDAddressPair[] requestNeighbors(MessagingAddress contact, int num) {
		IDAddressPair[] neighbors = null;

		Message req = new ReqNeighborsMessage(num);

		try {
			Message rep = this.sender.sendAndReceive(contact, req);

			neighbors = ((RepNeighborsMessage)rep).neighbors;
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to contact: " + contact);
			//throw e;
		}

		return neighbors;
	}
}

/*
 * Copyright 2006-2011 National Institute of Advanced Industrial Science
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

package ow.dht.impl;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.DHTConfiguration;
import ow.dht.ValueInfo;
import ow.dht.impl.message.DHTReplyMessage;
import ow.dht.impl.message.GetMessage;
import ow.dht.impl.message.PutMessage;
import ow.dht.impl.message.PutValueInfoMessage;
import ow.dht.impl.message.RemoveMessage;
import ow.dht.impl.message.ReqTransferMessage;
import ow.directory.DirectoryConfiguration;
import ow.directory.DirectoryFactory;
import ow.directory.DirectoryProvider;
import ow.directory.MultiValueAdapterForSingleValueDirectory;
import ow.directory.MultiValueDirectory;
import ow.directory.SingleValueDirectory;
import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessagingAddress;
import ow.routing.RoutingAlgorithm;
import ow.routing.RoutingException;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;
import ow.util.Timer;

/**
 * A churn-tolerant implementation of DHT service.
 * This implementations adds churn tolerance techniques to the basic implementation.
 * Those techniques include replication, join-time transfer, multiple get and repeated implicit put.
 */
public class ChurnTolerantDHTImpl<V extends Serializable> extends BasicDHTImpl<V> {
	private final static String LOCAL_DB_NAME = "local";

	// members specific to DHT

	private MultiValueDirectory<ID,ValueInfo<V>> localDir = null;	// just for reputting
	private static Timer timer = null;
	private boolean daemonsRunning = false;

	public ChurnTolerantDHTImpl(short applicationID, short applicationVersion,
			DHTConfiguration config, ID selfID /* possibly null */)
				throws Exception {
		super(applicationID, applicationVersion, config, selfID);
		this.init(config, routingSvc);
	}

	public ChurnTolerantDHTImpl(DHTConfiguration config, RoutingService routingSvc)
			throws Exception {
		super(config, routingSvc);
		this.init(config, routingSvc);
	}

	private void init(DHTConfiguration config, RoutingService routingSvc) throws Exception {
		// initialize directories
		DirectoryProvider dirProvider = DirectoryFactory.getProvider(config.getDirectoryType());
		DirectoryConfiguration dirConfig = DirectoryConfiguration.getDefaultConfiguration();

		if (config.getDoExpire())
			dirConfig.setExpirationTime(config.getDefaultTTL());
		else
			dirConfig.setExpirationTime(-1);

		if (this.config.getDoReputOnRequester()) {
			if (config.getMultipleValuesForASingleKey()) {
				this.localDir = dirProvider.openMultiValueDirectory(
						ID.class, ValueInfo.class, config.getWorkingDirectory(), LOCAL_DB_NAME,
						dirConfig);
			}
			else {
				SingleValueDirectory<ID,ValueInfo<V>> singleValueDir;
					 singleValueDir = dirProvider.openSingleValueDirectory(
							 ID.class, config.getValueClass(), config.getWorkingDirectory(), LOCAL_DB_NAME,
							 dirConfig);

				this.localDir = new MultiValueAdapterForSingleValueDirectory<ID, ValueInfo<V>>(singleValueDir);
			}
		}

		// initialize a Reputter
		this.startDaemons();
	}

	private void startDaemons() {
		synchronized (this) {
			if (this.daemonsRunning) return;
			this.daemonsRunning = true;
		}

		if (config.getReputParameters()[0] > 0
				&& (this.config.getDoReputOnRequester()
					|| this.config.getDoReputOnReplicas())) {
			if (config.getUseTimerInsteadOfThread()) {
				synchronized (BasicDHTImpl.class) {
					if (timer == null) timer = Timer.getSingletonTimer();
				}

				Runnable r = new Reputter();
				timer.schedule(r, Timer.currentTimeMillis(), true /*isDaemon*/);
			}
			else {
				Thread t = new Thread(new Reputter());
				t.setName("Reputter on " + this.getSelfIDAddressPair().getAddress());
				t.setDaemon(true);
				t.start();
			}
		}
	}

	private synchronized void stopDaemons() {
		if (!this.daemonsRunning) return;
		this.daemonsRunning = false;
	}

	public void clearDHTState() {
		super.clearDHTState();

		if (localDir != null) {
			synchronized (localDir) {
				localDir.clear();
			}
		}
	}

	public Set<ValueInfo<V>>[] get(ID[] keys) {
		Set<ValueInfo<V>>[] results = new Set/*<ValueInfo<V>>*/[keys.length];

		RoutingResult[] routingRes = super.getRemotely(keys, results);

		// get replicas from responsible node candidates
		int numTimesGets = config.getNumTimesGets() - 1;
		if (numTimesGets > 0) {
			Queue<IDAddressPair>[] respCands = new Queue/*<IDAddressPair>*/[keys.length];

			for (int i = 0; i < keys.length; i++) {
				if (routingRes[i] == null) continue;

				for (IDAddressPair p: routingRes[i].getResponsibleNodeCandidates()) {
					if (respCands[i] == null)	// skip the first element
						respCands[i] = new LinkedList<IDAddressPair>();
					else
						respCands[i].add(p);
				}
			}

			this.requestReplicas(results, keys, respCands, numTimesGets);
		}

		return results;
	}

	private void requestReplicas(
			Set<ValueInfo<V>>[] resultSet, ID[] keys, Queue<IDAddressPair>[] respCands, int numTimesGets) {
//System.out.print("requestReplicas:");
//for (ID k: keys) System.out.print(" " + k);
//System.out.println();
		int succeed[] = new int[keys.length];

		retry:
		while (true) {
			Set<IDAddressPair> contactSet = new HashSet<IDAddressPair>();
			for (int i = 0; i < keys.length; i++) {
				if (respCands[i] == null) continue;

				IDAddressPair p = respCands[i].peek();
				if (p == null)
					respCands[i] = null;
				else {
					contactSet.add(p);
				}
			}

			if (contactSet.isEmpty()) break;

			for (IDAddressPair contact: contactSet) {
//System.out.println("  contact: " + contact);
				List<Integer> indexList = new ArrayList<Integer>();
				for (int i = 0; i < keys.length; i++) {
					if (respCands[i] == null) continue;

					if (contact.equals(respCands[i].peek())) {
						indexList.add(i);
						respCands[i].poll();
					}
				}

				int size = indexList.size();
				ID[] packedKeys = new ID[size];
				for (int i = 0; i < indexList.size(); i++) {
					packedKeys[i] = keys[indexList.get(i)];
				}

				Message request = new GetMessage(packedKeys);
				Message reply = null;
				try {
					reply = sender.sendAndReceive(contact.getAddress(), request);
				}
				catch (IOException e) {
					continue retry;
				}

				Set<ValueInfo<V>>[] s;
				try {
					s = ((DHTReplyMessage<V>)reply).existedValues;
				}
				catch (ClassCastException e) {
					logger.log(Level.WARNING, "Reply to a GET req is not DHT_REPLY: "
							+ reply.getName()
							+ " from " + ((IDAddressPair)reply.getSource()).getAddress());
					continue retry;
				}

				for (int i = 0; i < indexList.size(); i++) {
					int index = indexList.get(i);

					if (++succeed[index] >= numTimesGets) {
						respCands[index] = null;	// clear to avoid retry
					}

					if (s[i] != null) {
						if (resultSet[index] == null) resultSet[index] = new HashSet<ValueInfo<V>>();
						resultSet[index].addAll(s[i]);
//System.out.print("  key[" + i + "]");
//for (ValueInfo<V> v: s[i]) System.out.print(" " + v.getValue());
//System.out.println();
					}
				}
			}	// for (IDAddressPair contact: contactSet)
		}	// while (true)
	}

	public Set<ValueInfo<V>> put(ID key, V[] values) throws IOException {
		// local
		if (localDir != null) {
			synchronized (localDir) {
				for (V v: values) {
					try {
						localDir.put(key, new ValueInfo<V>(v, this.ttlForPut, this.hashedSecretForPut));
					}
					catch (Exception e) {/*ignore*/}
				}
			}
		}

		// remote
		DHT.PutRequest<V>[] requests = new DHT.PutRequest/*<V>*/[1];
		requests[0] = new DHT.PutRequest<V>(key, values);

		int numReplica, repeat;
		if (config.getResponsibleNodeDoesReplication()) {
			numReplica = config.getNumReplica();
			repeat = 1;
		}
		else {
			numReplica = 1;
			repeat = config.getNumReplica();
		}

		Set<ValueInfo<V>>[] ret =
			this.putOrRemoveRemotely(requests, false, this.ttlForPut, this.hashedSecretForPut, true,
					numReplica, repeat, false);

		if (ret[0] == null) throw new RoutingException();

		return ret[0];
	}

	public Set<ValueInfo<V>>[] put(DHT.PutRequest<V>[] requests) throws IOException {
		// local
		if (localDir != null) {
			synchronized (localDir) {
				for (DHT.PutRequest<V> req: requests) {
					for (V v: req.getValues()) {
						try {
							localDir.put(req.getKey(), new ValueInfo<V>(v, this.ttlForPut, this.hashedSecretForPut));
						}
						catch (Exception e) {/*ignore*/}
					}
				}
			}
		}

		// remote
		int numReplica, repeat;
		if (config.getResponsibleNodeDoesReplication()) {
			numReplica = config.getNumReplica();
			repeat = 1;
		}
		else {
			numReplica = 1;
			repeat = config.getNumReplica();
		}

		return this.putOrRemoveRemotely(requests, false, this.ttlForPut, this.hashedSecretForPut, true,
				numReplica, repeat, false);
	}

	public Set<ValueInfo<V>>[] remove(DHT.RemoveRequest<V>[] requests, ByteArray hashedSecret) {
		// remove locally from localDir
		if (localDir != null) {
			for (int i = 0; i < requests.length; i++) {
				DHT.RemoveRequest<V> req = requests[i];
				try {
					if (req.getValues() != null) {
						synchronized (localDir) {
							for (V v: req.getValues()) {
								localDir.remove(req.getKey(), new ValueInfo<V>(v, -1, hashedSecret));
							}
						}
					}
					else {
						Set<ValueInfo<V>> localValues;

						localValues = localDir.get(req.getKey());
						if (localValues != null) {
							for (ValueInfo<V> v: localValues) {
								ID h = null;
								try {
									h = ID.getSHA1BasedID(
											v.getValue().toString().getBytes(config.getValueEncoding()));
								}
								catch (UnsupportedEncodingException e) {
									// NOTREACHED
									logger.log(Level.SEVERE, "Encoding not supported: " + config.getValueEncoding());
								}

								if ((req.getValueHash() == null || h.equals(req.getValueHash()))
									&& hashedSecret.equals(v.getHashedSecret())) {
									synchronized (localDir) {
										localDir.remove(req.getKey(), v);
									}
								}
							}
						}
					}
				}
				catch (Exception e) {
					// NOTREACHED
					logger.log(Level.WARNING, "An Exception thrown by Directory#remove().", e);
				}
			}	// for (int i = 0; i < requests.length; i++)
		}	// if (localDir != null)

		// remote
		int numReplica, repeat;
		if (config.getResponsibleNodeDoesReplication()) {
			numReplica = config.getNumReplica();
			repeat = 1;
		}
		else {
			numReplica = 1;
			repeat = config.getNumReplica();
		}

		Set<ValueInfo<V>>[] results =
			this.putOrRemoveRemotely(requests, true, 0, hashedSecret, true,
					numReplica, repeat, false);

		return results;
	}

	public synchronized void stop() {
		// TODO: transfer key-value pairs on this node to other nodes

		// stop reputter daemon
		this.stopDaemons();

		super.stop();

		// close directories
		if (this.localDir != null) {
			this.localDir.close();
			this.localDir = null;
		}
	}

	public synchronized void suspend() {
		this.stopDaemons();

		super.suspend();
	}

	public synchronized void resume() {
		super.resume();

		this.startDaemons();
	}

	//
	// methods specific to DHT
	//

	public Set<ID> getLocalKeys() {
		if (this.localDir == null)
			return null;

		return this.localDir.keySet();
	}
	public Set<ValueInfo<V>> getLocalValues(ID key) {
		if (this.localDir == null)
			return null;

		Set<ValueInfo<V>> ret = null;
		try {
			ret = this.localDir.get(key);
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "An Exception thrown when retrieve from the localDir.", e);
			return null;
		}

		return ret;
	}

	protected void prepareHandlers(RoutingService routingSvc) {
		super.prepareHandlers0(routingSvc);

		MessageHandler handler;

		handler = new PutMessageHandler();
		routingSvc.addMessageHandler(PutMessage.class, handler);

		handler = new RemoveMessageHandler();
		routingSvc.addMessageHandler(RemoveMessage.class, handler);

		// for value transfer
		handler = new MessageHandler() {
			public Message process(Message msg) {
				IDAddressPair src = (IDAddressPair)msg.getSource();

				// get key-value pairs to be transferred to the requesting node
				Map<ID,Set<ValueInfo<V>>> valueMap = getValueLocallyToBeTransferredTo(src.getID());

				MessagingAddress srcAddr = src.getAddress();
				Message putValueInfoMsg = new PutValueInfoMessage<V>(valueMap);

				try {
					sender.send(srcAddr, putValueInfoMsg);
				}
				catch (IOException e) {
					logger.log(Level.WARNING, "failed to send a PUT_VALUEINFO msg: " + srcAddr);
				}

				return null;
			}
		};
		routingSvc.addMessageHandler(ReqTransferMessage.class, handler);

		handler = new PutValueInfoMessageHandler();
		routingSvc.addMessageHandler(PutValueInfoMessage.class, handler);
	}

	protected class PutMessageHandler extends BasicDHTImpl.PutMessageHandler {
		public Message process(Message msg) {
			// put locally
			Message resultMsg = super.process(msg);

			Set<ValueInfo<V>>[] ret = ((DHTReplyMessage<V>)resultMsg).existedValues;

			// put remotely
			int numReplica = ((PutMessage<V>)msg).numReplica;

			if (numReplica > 1) {
				final DHT.PutRequest<V>[] requests = ((PutMessage<V>)msg).requests;
				long ttl = ((PutMessage<V>)msg).ttl;
				final ByteArray hashedSecret = ((PutMessage<V>)msg).hashedSecret;

				Set<ValueInfo<V>>[] existedValue =
					putOrRemoveRemotely(requests, false, ttl, hashedSecret, false,
							1, numReplica - 1, true);

				if (existedValue != null) {
					for (int i = 0; i < requests.length; i++) {
						Set<ValueInfo<V>> s = existedValue[i];
						if (s != null) {
							if (ret[i] == null) ret[i] = new HashSet<ValueInfo<V>>();
							ret[i].addAll(s);
						}
					}
				}
			}	// if (numReplica > 1)

			return resultMsg;
		}
	}

	private class RemoveMessageHandler extends BasicDHTImpl<V>.RemoveMessageHandler {
		public Message process(Message msg) {
			// remove locally
			Message resultMsg = super.process(msg);

			Set<ValueInfo<V>>[] ret = ((DHTReplyMessage<V>)resultMsg).existedValues;

			// remove remotely
			int numReplica = ((RemoveMessage<V>)msg).numReplica;

			if (numReplica > 1) {
				DHT.RemoveRequest<V>[] requests = ((RemoveMessage<V>)msg).requests;
				ByteArray hashedSecret = ((RemoveMessage<V>)msg).hashedSecret;

				Set<ValueInfo<V>>[] existedValue =
					putOrRemoveRemotely(requests, true, 0, hashedSecret, false,
							1, numReplica - 1, true);

				if (existedValue != null) {
					for (int i = 0; i < requests.length; i++) {
						Set<ValueInfo<V>> s = existedValue[i];
						if (s != null) {
							if (ret[i] == null) ret[i] = new HashSet<ValueInfo<V>>();
							ret[i].addAll(s);
						}
					}
				}
			}	// if (numReplica > 1)

			return resultMsg;
		}
	}

	private class PutValueInfoMessageHandler implements MessageHandler {
		public Message process(Message msg) {
			Map<ID,Set<ValueInfo<V>>> valueMap = ((PutValueInfoMessage<V>)msg).keyValuesMap;

			if (valueMap != null) {
				for (Map.Entry<ID,Set<ValueInfo<V>>> entry: valueMap.entrySet()) {
/*
System.out.println("PUT_VALUEINFO:");
System.out.println("  from: " + msg.getSource().getAddress());
System.out.println("  to  : " + getSelfIDAddressPair());
System.out.println("  key : " + entry.getKey());
*/
					ID key = entry.getKey();
					Set<ValueInfo<V>> valSet = entry.getValue();
					for (ValueInfo<V> val: valSet) {
						try {
							synchronized (globalDir) {
								globalDir.put(key, val, val.getTTL());
							}
						}
						catch (Exception e) { /* ignore */ }
					}
				}	// for
			}	// if (valueMap != null)

			return null;
		}
	}

	// for value transfer
	private Map<ID,Set<ValueInfo<V>>> getValueLocallyToBeTransferredTo(ID otherID) {
		ID selfID = this.getSelfIDAddressPair().getID();
		RoutingAlgorithm algo = this.routingSvc.getRoutingAlgorithm();

		Map<ID,Set<ValueInfo<V>>> results = null;

//System.out.println("joining node: " + otherID);
		ID[] keys = null;
		synchronized (globalDir) {
			Set<ID> keySet = globalDir.keySet();
			if (keySet != null) {
				keys = new ID[keySet.size()];
				keySet.toArray(keys);
			}
		}

		for (ID k: keys) {
//System.out.println("  key: " + k);
			IDAddressPair[] betterRespNodes =
				algo.responsibleNodeCandidates(k, config.getNumReplica() + 1 /* means the joining node */);
			if (betterRespNodes != null && betterRespNodes.length > 0) {
				for (IDAddressPair p: betterRespNodes) {
					if (otherID.equals(p.getID())) {
//System.out.println("    -> transfer.");
						try {
							Set<ValueInfo<V>> s = globalDir.get(k);

							if (s != null) {
								if (results == null) results = new HashMap<ID,Set<ValueInfo<V>>>();

								results.put(k, s);
							}
						}
						catch (Exception e) { /* ignore */ }
					}
					else if (selfID.equals(p.getID())) {
						break;
					}
				}
			}
		}

		return results;
	}

	private final static Random rnd = new Random();

	private class Reputter implements Runnable {
		// context
		private ID[] keys = null;
		private int keyIndex = 0;

		public void run() {
			logger.log(Level.INFO, "Reputter woke up.");

			int interval = config.getReputParameters()[0];
			int numOfKeysPerInterval = config.getReputParameters()[1];
			int numOfIntervals = config.getReputParameters()[2];

			MultiValueDirectory<ID,ValueInfo<V>> dir;

			if (config.getDoReputOnRequester())
				dir = localDir;
			else
				dir = globalDir;

			try {
				// initial sleep
				if (!config.getUseTimerInsteadOfThread()) {
					Thread.sleep((long)interval);
				}

				outer_most_loop:
				while (true) {
					// reset key index
					if (keys != null) {
						if (keyIndex >= numOfKeysPerInterval * numOfIntervals) keyIndex = 0;
					}

					// obtain an array of keys
					synchronized (dir) {
						Set<ID> keySet = dir.keySet();
						if (keySet != null && keySet.size() > 0) {
							keys = new ID[keySet.size()];
							keySet.toArray(keys);
						}
						else {
							keys = null;
						}
					}

					for (int i = 0; i < numOfIntervals; i++) {
						if (!daemonsRunning) break outer_most_loop;

						// reput values
						if (keys != null) {
							int limit = keyIndex + numOfKeysPerInterval;
							for (; keyIndex < limit; keyIndex++) {
//System.out.println("keyIndex: " + keyIndex);
								if (keyIndex >= keys.length) continue;

								ID key = keys[keyIndex];

								Set<ValueInfo<V>> valueInfoSet = getValueLocally(key, dir);
								if (valueInfoSet == null) continue;

								Map<ValueInfo.Attributes,Set<V>> attrValueMap =
									new HashMap<ValueInfo.Attributes,Set<V>>();
								for (ValueInfo<V> v: valueInfoSet) {
									Set<V> vSet = attrValueMap.get(v.getAttributes());
									if (vSet == null) {
										vSet = new HashSet<V>();
										attrValueMap.put(v.getAttributes(), vSet);
									}

									vSet.add(v.getValue());
								}

								for (ValueInfo.Attributes attr: attrValueMap.keySet()) {
									Set<V> vSet = attrValueMap.get(attr);
									V[] values = (V[])new Serializable[vSet.size()];
									vSet.toArray(values);

									DHT.PutRequest<V>[] reqs = new DHT.PutRequest/*<V>*/[1];
									reqs[0] = new DHT.PutRequest<V>(key, values);

									int numReplica, repeat;
									if (config.getResponsibleNodeDoesReplication()) {
										numReplica = config.getNumReplica();
										repeat = 1;
									}
									else {
										numReplica = 1;
										repeat = config.getNumReplica();
									}

//System.out.println("reput:");
//for (int j = 0; j < reqs.length; j++) {
//	System.out.print(" " + reqs[j].getKey().toString(-1) + ":");
//	for (V val: reqs[j].getValues()) {
//		System.out.print(" " + val);
//	}
//	System.out.println();
//}
									Set<ValueInfo<V>>[] ret =
										putOrRemoveRemotely(reqs, false, attr.getTTL(), attr.getHashedSecret(), false,
												numReplica, repeat, false);

									for (int j = 0; j < reqs.length; j++) {
										if (ret[j] == null) {
											logger.log(Level.WARNING, "put() failed: " + reqs[j].getKey());
										}
									}
								}
							}
						}	// if (keys != null)

						// sleep
						double playRatio = config.getReputIntervalPlayRatio();
						double intervalRatio = 1.0 - playRatio + (playRatio * 2.0 * rnd.nextDouble());
						long sleepPeriod = (long)(interval * intervalRatio);

						if (config.getUseTimerInsteadOfThread()) {
							timer.schedule(this, Timer.currentTimeMillis() + sleepPeriod, true /*isDaemon*/);
							return;
						}
						else {
							Thread.sleep((long)(sleepPeriod));
						}
					}	// for (int i = 0; i < numOfSet)
				}	// while (true)
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "Reputter interrupted and die.", e);
			}
		}
	}
}

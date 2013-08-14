/*
 * Copyright 2008 Kazuyuki Shudo, and contributors.
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

package ow.tool.scenariogen.commands;

import java.io.PrintStream;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchProviderException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import ow.dht.DHTConfiguration;
import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingFactory;
import ow.messaging.MessagingProvider;
import ow.messaging.emulator.EmuHostID;
import ow.routing.RoutingAlgorithm;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingAlgorithmFactory;
import ow.routing.RoutingAlgorithmProvider;

/**
 * Give key-value pairs are clustered by the cluster() method.
 */
public class IDClusteringAlgorithm {
	public static void copy(PrintStream out,
			Set<Set<Entry>> resultSet,
			Set<Entry> kvPairSet, int clusterSize) {
		while (!kvPairSet.isEmpty()) {
			Set<Entry> s = new LinkedHashSet<Entry>();
			resultSet.add(s);

			int i = 0;
			for (Entry e: kvPairSet) {
				if (i++ >= clusterSize) break;
				s.add(e);
			}

			kvPairSet.removeAll(s);
		}
	}

	public static void cluster(PrintStream out,
			Set<Set<Entry>> resultSet,
			Set<Entry> kvPairSet, int clusterSize, RoutingAlgorithm algo) {
		int idSizeInByte = algo.getConfiguration().getIDSizeInByte();

		ID supposedNode = ID.getID(new byte[idSizeInByte], idSizeInByte);	// 0x00...

		int count = 0;
		while (!kvPairSet.isEmpty()) {
			Set<Entry> s = new LinkedHashSet<Entry>();
			resultSet.add(s);
			Entry best = null;

			for (int i = 0; i < clusterSize; i++) {
				if (kvPairSet.isEmpty()) break;

				BigInteger bestDistance = BigInteger.ONE.shiftLeft(idSizeInByte * 8);

				for (Entry e: kvPairSet) {
					BigInteger distance;

					if (!s.isEmpty()) {
						// calculate average distance to all IDs which have been added to s
						distance = BigInteger.ZERO;
						for (Entry tgt: s) {
							BigInteger d = algo.distance(tgt.getID(), e.getID());
							distance = distance.add(d);
						}
						distance = distance.divide(BigInteger.valueOf(s.size()));
					}
					else {
						distance = algo.distance(supposedNode, e.getID());
					}

					if (bestDistance.compareTo(distance) > 0) {
						best = e;
						bestDistance = distance;
					}
				}

				kvPairSet.remove(best);
				s.add(best);

				//out.print(".");
				if (++count % 100 == 0) out.print(" " + count);
			}

			supposedNode = best.getID();	// the node added last
//System.out.println("cluster:");
//for (Entry e: s) {
//	System.out.println("  " + e.getID() + ":" + e.getKey());
//}
		}

		out.println();
	}

	public static void alignWithNodes(PrintStream out,
			Set<Set<Entry>> resultSet,
			Set<Entry> kvPairSet, int clusterSize, RoutingAlgorithm algo, int numNodes) {
		int idSizeInByte = algo.getConfiguration().getIDSizeInByte();

		// prepare EmuMessagingProvider
		MessagingProvider msgProvider = null;
		try {
			msgProvider = MessagingFactory.getProvider("Emulator", null);
		}
		catch (NoSuchProviderException e1) {
			// NOTREACHED
		}

		// prepare node IDs
		Set<ID> nodeIDSet = new LinkedHashSet<ID>();
		EmuHostID.setInitialID(0);
		EmuHostID hostID = EmuHostID.getNewInstance();	// "emu0"

		for (int i = 0; i < numNodes; i++) {
			hostID = EmuHostID.getNewInstance();	// "emu<n>"

			MessagingAddress addr = null;
			try {
				addr = msgProvider.getMessagingAddress(hostID.toString(), DHTConfiguration.DEFAULT_SELF_PORT);
			} catch (UnknownHostException e1) {
				// NOTREACHED
			}
			IDAddressPair p = IDAddressPair.getIDAddressPair(idSizeInByte, addr);

			nodeIDSet.add(p.getID());
		}

		// align all key-value pairs with node IDs
		Map<ID,Set<Entry>> table = new LinkedHashMap<ID,Set<Entry>>();

		int count = 0;
		for (Entry e: kvPairSet) {
			BigInteger bestDistance = BigInteger.ONE.shiftLeft(idSizeInByte * 8);
			ID best = null;

			for (ID nodeID: nodeIDSet) {
				BigInteger distance = algo.distance(nodeID, e.getID());
//System.out.println(distance.toString(16));
				if (distance.compareTo(bestDistance) < 0) {
					bestDistance = distance;
					best = nodeID;
				}
			}

//System.out.println("best: " + best);
			Set<Entry> s = table.get(best);
			if (s == null) {
				s = new LinkedHashSet<Entry>();
				table.put(best, s);
			}

			s.add(e);

			out.print(".");
			if (++count % 100 == 0) out.print(count);
		}

		// adjust table to resultSet
		for (ID nodeID: nodeIDSet) {
			Set<Entry> s = table.remove(nodeID);
			if (s == null) continue;

			do {
				int size = s.size();

				if (size < clusterSize) {
					// search a nodeID which is the closest to nodeID
					BigInteger bestDistance = BigInteger.ONE.shiftLeft(idSizeInByte * 8);
					ID best = null;
					for (ID id: table.keySet()) {
						if (id.equals(nodeID) || table.get(id) == null) continue;

						BigInteger distance = algo.distance(nodeID, id);
						if (distance.compareTo(bestDistance) < 0) {
							bestDistance = distance;
							best = id;
						}
					}

					if (best != null) {
						Set<Entry> anotherSet = table.get(best);
						if (size + anotherSet.size() <= clusterSize) {
							table.remove(best);
							s.addAll(anotherSet);

							continue;
						}
					}

					resultSet.add(s);
				}
				else if (size == clusterSize) {
					resultSet.add(s);
				}
				else if (size > clusterSize) {
					// divide a set if it is larger than clusterSize
					int i = clusterSize;
					Set<Entry> newSet = new LinkedHashSet<Entry>();

					for (Entry e: s) {
						if (i-- <= 0) {
							i = clusterSize;
							resultSet.add(newSet);
							newSet = new LinkedHashSet<Entry>();
						}

						newSet.add(e);
					}

					s = newSet;
					continue;
				}

//System.out.println("cluster:");
//for (Entry e: s) {
//	System.out.println("  " + e.getID() + ":" + e.getKey());
//}
				break;
			} while (true);
		}

		out.println();
	}

	public static RoutingAlgorithm getRoutingAlgorithm(
			PrintStream out, String algoName, int idSizeInByte) {
		// prepare RoutingAlgorithm instance
		RoutingAlgorithmProvider algoProvider;
		try {
			algoProvider = RoutingAlgorithmFactory.getProvider(algoName);
		}
		catch (NoSuchProviderException e) {
			out.println("No such routing algorithm: " + algoName);
			return null;
		}

		RoutingAlgorithmConfiguration algoConf = algoProvider.getDefaultConfiguration();
		if (algoConf.getIDSizeInByte() != idSizeInByte) {
			out.println("Warning: specified ID size is different from the default value ("
					+ algoConf.getIDSizeInByte() + "): " + idSizeInByte);
			algoConf.setIDSizeInByte(idSizeInByte);
		}

		RoutingAlgorithm algo;
		try {
			algo = algoProvider.initializeAlgorithmInstance(algoConf, new RoutingRuntimeSkeleton(algoConf));
		}
		catch (InvalidAlgorithmParameterException e) {
			// NOTREACHED
			out.println("An Exception thrown.");
			return null;
		}

		return algo;
	}
}

final class Entry {
	private final String key, value;
	private ID id;
	Entry(String key, String value, int idSizeInByte) {
		this.key = key; this.value = value;
		if (idSizeInByte > 0)
			this.id = ID.parseID(key, idSizeInByte);
	}
	String getKey() { return this.key; }
	String getValue() { return this.value; }
	ID getID() { return this.id; }
	public String toString() { return (this.id == null ? "(null)" : this.id) + ":" + this.key; }
}

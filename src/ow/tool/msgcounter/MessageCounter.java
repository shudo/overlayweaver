/*
 * Copyright 2006-2007,2009,2011 National Institute of Advanced Industrial Science
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

package ow.tool.msgcounter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import ow.messaging.MessageDirectory;
import ow.messaging.MessagingAddress;
import ow.stat.MessagingCollector;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;

/**
 * Message Counter.
 * Instantiated by the {@link ow.tool.msgcounter.Main Main} class.
 */
public final class MessageCounter implements ow.stat.MessagingCallback {
	private final StatConfiguration config;
	private MessagingCollector statCollector = null;

	private final Map<Integer,CounterEntry> totalCounterMap =
		new HashMap<Integer,CounterEntry>();
	private final Map<Integer,CounterEntry> lapCounterMap =
		new HashMap<Integer,CounterEntry>();

	private final SortedSet<Integer> tagSet = new TreeSet<Integer>();

	private final Set<MessagingAddress> nodeSet = new HashSet<MessagingAddress>();
	private int maxNumNode = 0;

	private boolean showLength;

	public MessageCounter(String transport, boolean noUPnP) {
		this.config = StatFactory.getDefaultConfiguration();

		if (transport != null) {
			this.config.setMessagingTransport(transport);
		}
		this.config.setDoUPnPNATTraversal(!noUPnP);
	}

	public void start() throws Exception {
		if (this.statCollector == null) {
			this.statCollector = StatFactory.getMessagingCollector(this.config);
			this.statCollector.start(this);
		}
	}

	public void stop() {
		if (this.statCollector != null) {
			this.statCollector.stop();
			this.statCollector = null;
		}
	}

	public synchronized void clear() {
		this.totalCounterMap.clear();
		this.lapCounterMap.clear();
		this.tagSet.clear();
	}

	/**
	 * Implements {@link ow.stat.MessagingCallback#messageSent(MessagingAddress, MessagingAddress, int, int)
	 * MessagingCallback#messageSent()}.
	 */
	public synchronized void messageSent(MessagingAddress source, MessagingAddress target, int tag, int len) {
		if (len > 0) showLength = true;

		int attribute;
		if (Thread.currentThread().isDaemon())
			attribute = CounterEntry.ATTR_DAEMON;
		else
			attribute = CounterEntry.ATTR_NONDAEMON;

		// total
		this.count(totalCounterMap, tag, len, attribute);

		// lap
		this.count(lapCounterMap, tag, len, attribute);

		// node set
		this.nodeSet.add(source);

		int size = this.nodeSet.size();
		if (size > this.maxNumNode) this.maxNumNode = size;
	}

	private void count(Map<Integer,CounterEntry> map, int tag, int len, int attribute) {
		CounterEntry c;

		c = map.get(tag);
		if (c == null) {
			c = new CounterEntry();
			map.put(tag, c);
			this.tagSet.add(tag);
		}
		c.count(len, attribute);
	}

	/**
	 * Implements {@link ow.stat.MessagingCallback#nodeFailed(MessagingAddress)
	 * MessagingCallback#deleteNode()}.
	 */
	public void nodeFailed(MessagingAddress node) {
		this.nodeSet.remove(node);
	}

	public synchronized String getCountsString(boolean verbose) {
		StringBuilder sb = new StringBuilder();

		sb.append("\n");

		// lap
		int numNodes = this.nodeSet.size();

		this.appendCountsString(sb, "lap", lapCounterMap, 0, numNodes);
		if (verbose) {
			this.appendCountsString(sb, "lap:nondaemon", lapCounterMap, CounterEntry.ATTR_NONDAEMON, numNodes);
			this.appendCountsString(sb, "lap:daemon", lapCounterMap, CounterEntry.ATTR_DAEMON, numNodes);
		}

		this.lapCounterMap.clear();

		// total
		this.appendCountsString(sb, "total", totalCounterMap, 0, this.maxNumNode);

		return sb.toString();
	}

	private StringBuilder appendCountsString(StringBuilder sb,
			String label, Map<Integer,CounterEntry> map, int matchingAttribute, int numNodes) {
		int sumNumber = 0, sumLength = 0;

		sb.append("Message count");
		if (showLength) sb.append(" & length in byte");
		sb.append(" (").append(label).append("):\n");

		for (Integer i: this.tagSet) {
			CounterEntry c = map.get(i);
			if (c == null) continue;

			if (matchingAttribute > 0
					&& c.getAttribute() != matchingAttribute)
				continue;

			int number = c.getNumber();
			if (number == 0) continue;
			sumNumber += number;

			sb.append(MessageDirectory.getName(i));
			sb.append(", ");
			sb.append(number);

			if (showLength) {
				int length = c.getLength();
				sumLength += length;

				sb.append(", ");
				sb.append(length);
			}
			sb.append("\n");
		}

		sb.append("message count");
		if (showLength) sb.append(", length");
		sb.append(" & # of nodes (").append(label).append("), ");

		sb.append(sumNumber);
		if (showLength) {
			sb.append(", ");
			sb.append(sumLength);
		}
		sb.append(", ");
		sb.append(numNodes);
		sb.append("\n\n");

		return sb;
	}

	/**
	 * Counter for number and length of messages.
	 */
	private final class CounterEntry {
		public static final int ATTR_NONDAEMON = 1;
		public static final int ATTR_DAEMON = 2;

		private int number = 0;
		private int length = 0;
		private int attribute = 0;

		public int getNumber() { return this.number; }
		public int getLength() { return this.length; }
		public int getAttribute() { return this.attribute; }

		public void count(int len, int attribute) {
			this.number++;
			this.length += len;
			this.attribute = attribute;
		}
	}
}

/*
 * Copyright 2006-2007 Kazuyuki Shudo, and contributors.
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

package ow.tool.visualizer;

import ow.id.ID;
import ow.messaging.MessageReceiver;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.messaging.util.MessagingUtility;
import ow.stat.MessagingCallbackExtended;
import ow.stat.MessagingCollector;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;
import ow.tool.util.vizframework.Visualizer;

/**
 * Overlay Visualizer.
 * Instantiated by the {@link ow.tool.visualizer.Main Main} class.
 */
public final class OverlayVisualizer {
	private Visualizer viz;
	private MessagingCollector collector;

	public OverlayVisualizer(
			String transport, int idSizeInBit, String selfAddressAndPort, boolean noUPnP,
			String contactHostAndPort, int contactPort) throws Exception {
		this.viz = new Visualizer(idSizeInBit);

		// initialize StatCollector
		StatConfiguration config = StatFactory.getDefaultConfiguration();
		if (transport != null) {
			config.setMessagingTransport(transport);
		}
		if (selfAddressAndPort != null) {
			MessagingUtility.HostAndPort hostAndPort =
				MessagingUtility.parseHostnameAndPort(selfAddressAndPort, config.getSelfPort());

			config.setSelfAddress(hostAndPort.getHostName());
			config.setSelfPort(hostAndPort.getPort());
		}
		if (noUPnP) config.setDoUPnPNATTraversal(false);

		this.collector = StatFactory.getMessagingCollector(config);
		this.collector.start(new VizCallback());

		// initialize node collector
		if (contactHostAndPort != null) {
			MessagingProvider provider = this.collector.getMessagingProvider();
			MessageReceiver receiver = this.collector.getMessageReceiver();
			MessagingAddress contact;
			try {
				contact = provider.getMessagingAddress(contactHostAndPort, contactPort);
			}
			catch (IllegalArgumentException e) {
				contact = provider.getMessagingAddress(contactHostAndPort, config.getContactPort());
			}

			Runnable r = StatFactory.getNodeCollector(config,
					contact, viz, receiver);
			Thread t = new Thread(r);
			t.setName("NodeCollector in Visualizer");
			t.setDaemon(true);
			t.start();
		}
	}

	private class VizCallback implements MessagingCallbackExtended {
		public void messageSent(MessagingAddress source, MessagingAddress dest, int tag, int len) {
			ID sourceID, destID;
			sourceID = collector.getID(source);
			destID = collector.getID(dest);

			if (sourceID != null) {
				viz.addNode(sourceID, source);

				int sourceIDSizeInBit = sourceID.getSize() * 8;
				viz.setIDSizeInBit(sourceIDSizeInBit);
			}

//			if (destID != null) {
//				Visualizer.this.addNode(destID);
//			}

			if (sourceID != null && destID != null) {
				viz.addMessage(sourceID, destID, tag);
			}
		}

		public void nodeFailed(MessagingAddress node) {
			ID id = collector.getID(node);
			if (id != null) {
				viz.removeNode(id);
			}
		}

		public void emphasizeNode(ID nodeID) {
			viz.emphasizeNode(nodeID);
		}

		public void markID(ID id, int hint) {
			viz.addMark(id, hint);
		}

		public void connectNodes(ID from, ID to, int colorHint) {
			viz.connectNodes(from, to, colorHint);
		}

		public void disconnectNodes(ID from, ID to, int colorHint) {
			viz.disconnectNodes(from, to, colorHint);
		}
	}
}

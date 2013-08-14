/*
 * Copyright 2006-2008,2011 National Institute of Advanced Industrial Science
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

package ow.stat.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.stat.MessagingCallback;
import ow.stat.MessagingCallbackExtended;
import ow.stat.MessagingCollector;
import ow.stat.StatConfiguration;
import ow.stat.impl.message.ConnectNodesMessage;
import ow.stat.impl.message.DeleteNodeMessage;
import ow.stat.impl.message.DisconnectNodesMessage;
import ow.stat.impl.message.EmphasizeNodeMessage;
import ow.stat.impl.message.MarkIDMessage;
import ow.stat.impl.message.MessageSentMessage;
import ow.stat.impl.message.StatAckMessage;
import ow.stat.impl.message.StatPingMessage;

public final class MessagingCollectorImpl implements MessagingCollector {
	private final StatConfiguration config;

	private MessagingProvider provider;
	private MessageReceiver receiver;

	private MessagingCallback callback;
	private final Map<MessagingAddress,ID> idTable = new HashMap<MessagingAddress,ID>();

	public MessagingCollectorImpl(StatConfiguration config) throws Exception {
		this.config = config;

		// initialize messaging subsystem
		this.provider = this.config.deriveMessagingProvider();
		this.receiver = this.config.deriveMessageReceiver(this.provider);
/*
		this.provider = MessagingFactory.getProvider(
				this.config.getMessagingTransport(),
				Signature.getAllAcceptingSignature());

		if (this.config.getSelfAddress() != null) {
			this.provider.setSelfAddress(this.config.getSelfAddress());
		}

		MessagingConfiguration msgConfig = this.provider.getDefaultConfiguration();
		msgConfig.setDoUPnPNATTraversal(config.getDoUPnPNATTraversal());

		this.receiver = this.provider.getReceiver(msgConfig,
				this.config.getSelfPort(), this.config.getSelfPortRange());
		config.setSelfPort(receiver.getPort());	// correct config
*/
	}

	public void start(MessagingCallback callback) throws IOException {
		this.callback = callback;

		// initialize message handlers
		prepareHandlers();
	}

	public void stop() {
		// stop message receiver
		if (this.receiver != null) {
			this.receiver.stop();
		}
	}

	public ID getID(MessagingAddress address) {
		ID id = null;

		synchronized (idTable) {
			id = idTable.get(address);
		}

		return id;
	}

	public MessagingProvider getMessagingProvider() { return this.provider; }
	public MessageReceiver getMessageReceiver() { return this.receiver; }

	private void prepareHandlers() {
		MessageHandler handler;

		handler = new MessageHandler() {
			public Message process(Message msg) {
				Message ret = null;

				if (msg instanceof StatPingMessage) {
					ret = new StatAckMessage();
				}
				else if (msg instanceof MessageSentMessage) {
					MessagingAddress src = ((MessageSentMessage)msg).msgSrc;
					MessagingAddress dest = ((MessageSentMessage)msg).msgDest;
					int tag = ((MessageSentMessage)msg).msgTag;
					int len = ((MessageSentMessage)msg).msgLen;

					// register the id:address table
					IDAddressPair source = (IDAddressPair)msg.getSource();
					MessagingAddress addr = source.getAddress();
					ID id = source.getID();
					if (id != null) {
						synchronized (idTable) {
							ID oldID = idTable.get(addr);
							if (oldID == null || !oldID.equals(id)) {
								idTable.put(addr, id);
							}
						}
					}

					callback.messageSent(src, dest, tag, len);
				}
				else if (msg instanceof DeleteNodeMessage) {
					MessagingAddress node = ((DeleteNodeMessage)msg).node;

					// invoke callback
					callback.nodeFailed(node);
				}
				else if (callback instanceof MessagingCallbackExtended) {
					MessagingCallbackExtended cb = (MessagingCallbackExtended)callback;

					if (msg instanceof EmphasizeNodeMessage) {
						ID nodeID = ((EmphasizeNodeMessage)msg).node;

						// invoke callback
						cb.emphasizeNode(nodeID);
					}
					else if (msg instanceof MarkIDMessage) {
						ID[] ids = ((MarkIDMessage)msg).ids;
						int hint = ((MarkIDMessage)msg).hint;

						// invoke callback
						for (ID id: ids) cb.markID(id, hint);
					}
					else if (msg instanceof ConnectNodesMessage) {
						ID from = ((ConnectNodesMessage)msg).from;
						ID to = ((ConnectNodesMessage)msg).to;
						int colorHint = ((ConnectNodesMessage)msg).colorHint;

						// invoke callback
						cb.connectNodes(from, to, colorHint);
					}
					else if (msg instanceof DisconnectNodesMessage) {
						ID from = ((ConnectNodesMessage)msg).from;
						ID to = ((ConnectNodesMessage)msg).to;
						int colorHint = ((ConnectNodesMessage)msg).colorHint;

						// invoke callback
						cb.disconnectNodes(from, to, colorHint);
					}
				}

				return ret;
			}
		};
		this.receiver.addHandler(handler);
	}
}

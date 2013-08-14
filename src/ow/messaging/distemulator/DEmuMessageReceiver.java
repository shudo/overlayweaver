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

package ow.messaging.distemulator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.messaging.distemulator.message.EncapsulatedMessage;
import ow.messaging.emulator.EmuMessageReceiver;
import ow.messaging.emulator.EmuMessageSender;
import ow.stat.MessagingReporter;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;
import ow.tool.emulator.RemoteAppInstanceTable;

public final class DEmuMessageReceiver implements MessageReceiver {
	private final static Logger logger = Logger.getLogger("messaging");

	private InetAddress selfInetAddress;
	private final DEmuMessagingProvider provider;
	private final EmuMessageReceiver emuReceiver;
	private final MessageReceiver netReceiver;
	private final MessagingProvider netProvider;
	private final RemoteAppInstanceTable hostTable;

	private final EmuMessageSender emuSender;

	private final MessagingReporter msgReporter;

	protected DEmuMessageReceiver(InetAddress selfInetAddr,
			DEmuMessagingProvider provider,
			EmuMessageReceiver emuReceiver, MessageReceiver netReceiver, MessagingProvider netProvider,
			RemoteAppInstanceTable hostTable) throws UnknownHostException {
		this.selfInetAddress = selfInetAddr;
		this.provider = provider;
		this.emuReceiver = emuReceiver;
		this.netReceiver = netReceiver;
		this.netProvider = netProvider;
		this.hostTable = hostTable;

		if (this.selfInetAddress == null) {
			this.selfInetAddress = InetAddress.getLocalHost();
		}

		this.emuSender = (EmuMessageSender)this.emuReceiver.getSender();

		StatConfiguration conf = StatFactory.getDefaultConfiguration();
		this.msgReporter = StatFactory.getMessagingReporter(conf, this.provider, this.getSender());
	}

	public MessagingReporter getMessagingReporter() { return this.msgReporter; }

	public MessageSender getSender() {
		EmuMessageSender emuSender = (EmuMessageSender)this.emuReceiver.getSender();
		MessageSender netSender = this.netReceiver.getSender();

		return new DEmuMessageSender(this.selfInetAddress,
				this.emuReceiver, emuSender, netSender, this.netProvider, this.hostTable,
				this.msgReporter);
	}

	public void addHandler(MessageHandler handler) {
		this.emuReceiver.addHandler(handler);

		TunnelingMessageHandler h = new TunnelingMessageHandler(handler, this.emuSender);
		this.netReceiver.addHandler(h);
	}

	public void removeHandler(MessageHandler handler) {
		this.emuReceiver.removeHandler(handler);

		TunnelingMessageHandler h = new TunnelingMessageHandler(handler, this.emuSender);
		this.netReceiver.removeHandler(h);
	}

	public MessagingAddress getSelfAddress() { return this.emuReceiver.getSelfAddress(); }

	public void setSelfAddress(String hostname) {
		// does not work because relay destination is not adjusted.
		this.emuReceiver.setSelfAddress(hostname);
	}

	public MessagingAddress setSelfAddress(MessagingAddress msgAddr) {
		return this.emuReceiver.setSelfAddress(msgAddr);
	}

	public int getPort() {
		return this.emuReceiver.getPort();
	}

	public void stop() {
		this.emuReceiver.stop();
		this.netReceiver.stop();
	}

	public void start() {
		this.emuReceiver.start();
		this.netReceiver.start();
	}

	/**
	 * A handler which receives incoming messages from network.
	 */
	private class TunnelingMessageHandler implements MessageHandler {
		private final MessageHandler enclosedHandler;
		private final EmuMessageSender emuSender;

		TunnelingMessageHandler(MessageHandler handler, EmuMessageSender emuSender) {
			this.enclosedHandler = handler;
			this.emuSender = emuSender;
		}

		public Message process(Message msg) {
			Message ret = null;

			// decapsulate
			if (!(msg instanceof EncapsulatedMessage)) {
				return null;
			}

			MessagingAddress dest = ((EncapsulatedMessage)msg).dest;
			Message enclosedMessage = ((EncapsulatedMessage)msg).enclosed;
			boolean roundtrip = ((EncapsulatedMessage)msg).roundtrip;

/*
System.out.println("Decapsulated::");
System.out.println("  " + Tag.getStringByNumber(enclosedMessage.getTag()));
System.out.println("  " + enclosedMessage.getSource().getAddress().getClass());
System.out.println("  " + enclosedMessage.getSource().getAddress());
*/
			try {
				if (roundtrip) {
					ret = this.emuSender.sendAndReceive(dest, enclosedMessage);
				}
				else {
					this.emuSender.send(dest, enclosedMessage);
				}
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "Failed to send into emulator: " + dest);

				// notify statistics collector
				msgReporter.notifyStatCollectorOfDeletedNode(dest);
			}

			if (ret != null) {
				// encapsulate
				ret = new EncapsulatedMessage(
						msg.getSource().getMessagingAddress(), ret, false);
			}

			return ret;
		}

		public boolean equals(Object o) {
			TunnelingMessageHandler h = (TunnelingMessageHandler)o;
			if (this.enclosedHandler.equals(h.enclosedHandler)) {
				return true;
			}

			return false;
		}

		public int hashCode() {
			return this.enclosedHandler.hashCode() + 1;
		}
	}
}

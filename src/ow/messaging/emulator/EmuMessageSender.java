/*
 * Copyright 2009,2011 Kazuyuki Shudo, and contributors.
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

package ow.messaging.emulator;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.messaging.Message;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.stat.MessagingReporter;

/**
 * A {@link MessageSender MessageSender} class for distributed environment emulation.
 * Note that this class does not implement timeout in messaging.
 */
public final class EmuMessageSender implements MessageSender {
	private final static Logger logger = Logger.getLogger("messaging");

	private final static Random random = new Random();	// for emulating communication failure

	private final EmuMessageReceiver receiver;

	protected EmuMessageSender(EmuMessageReceiver receiver) {
		this.receiver = receiver;
	}

	public void send(MessagingAddress dest, Message msg) throws IOException {
		this.send0(dest, msg, false);
	}

	public Message sendAndReceive(MessagingAddress dest, Message msg) throws IOException {
		return this.send0(dest, msg, true);
	}

	private Message send0(MessagingAddress dest, Message msg, boolean doReceive) throws IOException {
		// set source address
		if (msg.getSource() == null)
			msg.setSource(this.receiver.getSelfAddress());

		// get a receiver
		EmuMessageReceiver receiver = EmuMessageReceiver.getReceiver(dest);

		if (receiver == null) {
			logger.log(Level.WARNING, "No such node: " + dest);

			MessagingReporter msgReporter = this.receiver.getMessagingReporter();
			if (msgReporter != null) {
				msgReporter.notifyStatCollectorOfDeletedNode(dest);
			}

			throw new IOException("No such node: " + dest);
		}

		// set signature
//		byte[] sig = this.receiver.provider.getMessageSignature();
//		msg.setSignature(sig);

		// cause communication failure artificially in sending
		if (receiver.communicationCanFail
				&& random.nextDouble() < receiver.config.getCommunicationFailureRate()) {
			// failed to send
			if (doReceive) {
				try {
					Thread.sleep(receiver.config.getStaticTimeout());
				}
				catch (InterruptedException e) { /*ignore*/ }

				throw new IOException("failed to send to " + dest);
			}
			else {
				return null;	// sender is not aware of the failure
			}
		}

		// send
		Message ret = receiver.processAMessage(msg);

//		logger.log(Level.INFO, "send: " + Tag.getStringByNumber(emuMsg.getMessage().getTag()) +
//				" from " + this.receiver.getSelfAddress() + " to " + emuAddr);

		// cause communication failure artificially in receiving
		if (receiver.communicationCanFail
				&& doReceive
				&& random.nextDouble() < receiver.config.getCommunicationFailureRate()) {
			// failed to receive
			try {
				Thread.sleep(receiver.config.getStaticTimeout());
			}
			catch (InterruptedException e) { /*ignore*/ }

			throw new IOException("failed to receive from " + dest);
		}

		// notify statistics collector
		MessagingReporter msgReporter = this.receiver.getMessagingReporter();
		if (msgReporter != null && !this.receiver.getSelfAddress().equals(dest)) {
			// TODO: measures the length of the message.
			msgReporter.notifyStatCollectorOfMessageSent(dest, msg, 0);
		}

		return ret;
	}
}

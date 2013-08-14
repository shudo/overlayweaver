/*
 * Copyright 2006-2007,2011 National Institute of Advanced Industrial Science
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

package ow.messaging.emulator;

import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingFactory;
import ow.messaging.MessagingProvider;
import ow.messaging.Signature;
import ow.routing.impl.message.AckMessage;
import ow.routing.impl.message.PingMessage;

public class EmulatorTest {
	public final static int DUMMY_PORT = 10000;

	public static void main(String[] args) {
		MessagingProvider provider;
		MessageReceiver receiver = null;

		try {
			provider = MessagingFactory.getProvider("Emulator", Signature.getAllAcceptingSignature());
			receiver = provider.getReceiver(
					provider.getDefaultConfiguration(), 10000, 1);	// port number is ignored
		}
		catch (Exception e) {
			// NOTREACHED
			System.err.println("Could not obtain Emulator's provider or receiver.");
			System.exit(1);
		}

		//receiver.start();

		receiver.addHandler(
				new MessageHandler() {
					public Message process(Message msg) {
						System.out.println("server received: " + msg.getName());
						return new AckMessage();
					};
				});


		try {
			Thread.sleep(2000);
		}
		catch (InterruptedException e) {
			System.err.println("interrupted.");
		}


		Thread t = new Thread(new AClient(receiver.getSelfAddress()));
		t.setDaemon(false);
		t.setName("test client");
		t.start();
	}


	private static class AClient implements Runnable {
		MessagingAddress servAddr;

		AClient(MessagingAddress servAddr) {
			this.servAddr = servAddr;
		}

		public void run() {
			MessagingProvider provider;
			MessageSender sender = null;
			
			try {
				provider = MessagingFactory.getProvider("Emulator", Signature.getAllAcceptingSignature());
				MessageReceiver receiver = provider.getReceiver(
						provider.getDefaultConfiguration(), DUMMY_PORT, 1);
				sender = receiver.getSender();
			}
			catch (Exception e) {
				// NOTREACHED
				System.err.println("Could not obtain Emulator's provider or receiver.");
				System.exit(1);
			}

			// test

			try {
				for (int i = 0; i < 3; i++) {
					Message msg = new PingMessage();
					msg = sender.sendAndReceive(servAddr, msg);

					System.out.println("client received: " + msg.getName());

					// sleep
					Thread.sleep(1000);
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}

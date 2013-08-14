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

package ow.messaging;

import ow.stat.MessagingReporter;

public interface MessageReceiver {
	/**
	 * Returns a MessageSender.
	 */
	MessageSender getSender();

	/**
	 * Registers a message handler.
	 */
	void addHandler(MessageHandler handler);

	/**
	 * Removes a message handler.
	 */
	void removeHandler(MessageHandler handler);

	/**
	 * Returns SocketAddress of the node itself.
	 */
	MessagingAddress getSelfAddress();

	/**
	 * Sets SocketAddress of the node itself.
	 */
	void setSelfAddress(String hostnameOrIPAddress);

	/**
	 * Sets MessagingAddress directly.
	 */
	MessagingAddress setSelfAddress(MessagingAddress msgAddress);

	/**
	 * Returns port number bound to this receiver.
	 */
	int getPort();

	MessagingReporter getMessagingReporter();

	/**
	 * Stops this receiver.
	 */
	void stop();

	/**
	 * Restarts this receiver after stopped.
	 */
	void start();
}

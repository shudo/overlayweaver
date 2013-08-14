/*
 * Copyright 2006-2009,2011 National Institute of Advanced Industrial Science
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

import java.io.IOException;
import java.net.UnknownHostException;

/**
 * The interface which a messaging provider for various transport protocols
 * (TCP, UDP, Emulator, ...) implements.  
 */
public interface MessagingProvider {
	/**
	 * Returns the name of the provider.
	 */
	String getName();

	/**
	 * Returns true if the transport which this messaging service uses is reliable.
	 * For example, TCP and Emulator messaging services return true,
	 * and UDP messaging service returns false.
	 */
	boolean isReliable();

	/**
	 * Returns the default configuration.
	 */
	MessagingConfiguration getDefaultConfiguration();

	/**
	 * Returns a MessageReceiver bound to one of port numbers from port to port + portRange - 1.
	 */
	MessageReceiver getReceiver(MessagingConfiguration config, int port, int portRange) throws IOException;

	/**
	 * Returns a MessagingAddress with the given hostname and port number.
	 */
	MessagingAddress getMessagingAddress(String hostAndPort, int defaultPort)
		throws UnknownHostException;

	/**
	 * Returns a MessagingAddress with the given hostname and port number
	 * in the following format: hostname:port or hostname/port.
	 */
	MessagingAddress getMessagingAddress(String hostAndPort)
		throws UnknownHostException;

	/**
	 * Returns a MessagingAddress for the caller itself with the given port number.
	 */
	MessagingAddress getMessagingAddress(int port);

	/**
	 * Returns the address to which all sent messages are reported.
	 */
	MessagingAddress getMessagingCollectorAddress();

	/**
	 * Sets an address to which all sent messages are reported.
	 */
	MessagingAddress setMessagingCollectorAddress(MessagingAddress addr);

	/**
	 * A workaround for Emulator.
	 */
	MessagingProvider substitute();

	/**
	 * A workaround to set the hostname or IP address of this node itself.
	 */
	void setSelfAddress(String hostnameOrIPAddress)
		throws UnknownHostException;

	/**
	 * Returns the message signature.
	 */
	byte[] getMessageSignature();

	/**
	 * Sets the message signature.
	 */
	byte[] setMessageSignature(byte[] signature);
}

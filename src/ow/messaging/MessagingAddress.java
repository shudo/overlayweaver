/*
 * Copyright 2006,2009,2011 National Institute of Advanced Industrial Science
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

import java.io.Serializable;

/**
 * Address of a communication endpoint in this messaging framework.
 * Call ow.messaging.MessagingProvider#getMessagingAddress() to obtain an instance.
 * Note that a class implementing this interface should override Object#hashCode().
 */
public interface MessagingAddress extends Serializable {
	String getHostAddress();
	String getHostname();
	String getHostnameOrHostAddress();
	int getPort();

	MessagingAddress getMessagingAddress();
		// returns an internal MessagingAddress in case of an enclosing class (IDAddressPair)

	void copyFrom(MessagingAddress newAddress);
		// substitutes a member in case of an enclosing class (IDAddressPair)

	String toString(int verboseLevel);
}

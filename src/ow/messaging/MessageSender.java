/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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


public interface MessageSender {
	/**
	 * Send the given message one-way.
	 */
	void send(MessagingAddress dest, Message msg) throws IOException;

	/**
	 * Send the given message, wait for a message and receive it.  
	 *
	 * @param timeout infinity if timeout is less than 0.
	 * @return received message, or null in case of timeout.
	 * @throws IOException
	 */
	Message sendAndReceive(MessagingAddress dest, Message msg) throws IOException;
}

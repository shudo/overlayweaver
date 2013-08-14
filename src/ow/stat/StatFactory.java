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

package ow.stat;

import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.stat.impl.MessagingCollectorImpl;
import ow.stat.impl.NodeCollectorImpl;

public class StatFactory {
	/**
	 * Returns a default configuration.
	 */
	public static StatConfiguration getDefaultConfiguration() {
		return new StatConfiguration();
	}

	public static MessagingCollector getMessagingCollector(StatConfiguration config)
			throws Exception {
		return new MessagingCollectorImpl(config);
	}

	public static MessagingReporter getMessagingReporter(StatConfiguration config,
			MessagingProvider provider, MessageSender sender) {
		return new MessagingReporter(config, provider, sender);
	}

	public static NodeCollector getNodeCollector(StatConfiguration config,
			MessagingAddress initialContact, NodeCollectorCallback cb,
			MessageReceiver receiver /* can be null */)
				throws Exception {
		return new NodeCollectorImpl(initialContact, cb, config, receiver);
	}
}

/*
 * Copyright 2006,2011 National Institute of Advanced Industrial Science
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

package ow.msgstat;

import ow.id.ID;
import ow.messaging.MessageDirectory;
import ow.messaging.MessagingAddress;
import ow.stat.MessagingCallback;
import ow.stat.MessagingCollector;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;

public class StatCollectorTest {
	public static void main(String[] args) {
		StatConfiguration config = StatFactory.getDefaultConfiguration();
		MessagingCollector collector;

		try {
			collector = StatFactory.getMessagingCollector(config);
		}
		catch (Exception e) {
			e.printStackTrace();
			return;
		}

		prepareHandlers(collector);

		System.out.println("Start.");

		try {
			Thread.sleep(Long.MAX_VALUE);
		}
		catch (InterruptedException e) {
			System.err.println("Thread#sleep() interrupted.");
			e.printStackTrace();
			return;
		}
	}

	private static void prepareHandlers(MessagingCollector collector) {
		MessagingCallback callback = new MessagingCallback() {
			public void messageSent(MessagingAddress source, MessagingAddress target, int tag, int len) {
				System.out.println("StatCollector: " + MessageDirectory.getName(tag)
						+ " from " + source + "  to " + target + ".");
				System.out.flush();
			}

			public void nodeFailed(MessagingAddress node) {
				System.out.println("StatCollector: " + node + " deleted.");
				System.out.flush();
			}

			public void emphasizeNode(ID nodeID) {
				System.out.println("StatCollector: " + nodeID + " emphasized.");
				System.out.flush();
			}

			public void markID(ID id, int hint) {
				System.out.println("StatCollector: " + id + " marked with hint " + hint + ".");
				System.out.flush();
			}

			public void connectNodes(ID from, ID to, int colorHint) {
				System.out.println("StatCollector: " + from + " and " + to + " connected.");
				System.out.flush();
			}

			public void disconnectNodes(ID from, ID to, int colorHint) {
				System.out.println("StatCollector: " + from + " and " + to + " disconnected.");
				System.out.flush();
			}
		};

		try {
			collector.start(callback);
		}
		catch (Exception e) {
			System.err.println("An Exception thrown:");
			e.printStackTrace();
			return;
		}

	}
}

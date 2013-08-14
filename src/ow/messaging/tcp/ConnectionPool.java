/*
 * Copyright 2006-2007,2009-2010 National Institute of Advanced Industrial Science
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

package ow.messaging.tcp;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.util.Timer;

/**
 * Establish an outgoing connection, pool it and return it.
 */
final class ConnectionPool {
	private final static Logger logger = Logger.getLogger("messaging");

	private final int size;
	private final long expirationTime;

	private Map<SocketAddress,ExpiringSocketChannel> connectionMap;
	private final Random rnd;

	private final static Timer timer = Timer.getSingletonTimer();
	private ExpiringTask expiringTask = null;

	ConnectionPool(int size, long expirationTime) {
		this.size = size;
		this.expirationTime = expirationTime;

		if (size > 0) this.connectionMap = new HashMap<SocketAddress,ExpiringSocketChannel>();
		this.rnd = new Random();
	}

	/**
	 * Look for a Socket connected to dest, if found remove it from the table and return it.
	 * Otherwise connect.
	 * Note that the returned Socket is possible to be already closed.
	 */
	public SocketChannel get(SocketAddress dest) throws IOException {
		if (this.size <= 0) return SocketChannel.open(dest);

		SocketChannel sock = null;
		synchronized (this.connectionMap) {
			ExpiringSocketChannel es = this.connectionMap.remove(dest);	// retrieve a Socket
			if (es != null) sock = es.getSocketChannel();
		}

		if (sock != null) {
			logger.log(Level.INFO, "A Socket found in the hash table: ", sock);
			return sock;
		}
		else {
			try {
				sock = SocketChannel.open(dest);
				logger.log(Level.INFO, "A new Socket created: " + dest);
			}
			catch (IOException e) {
				logger.log(Level.INFO, "Could not create a Socket: " + dest);
				throw e;
			}

			return sock;
		}
	}

	public void put(SocketAddress addr, SocketChannel sock) {
		if (this.size <= 0) {
			try {
				sock.close();
			}
			catch (IOException e) { /* ignore */ }

			return;
		}

		long expiringTime;
		SocketChannel existedChannel = null;

		synchronized (this.connectionMap) {
			ExpiringSocketChannel es;

			es = this.connectionMap.remove(addr);
			if (es != null) existedChannel = es.getSocketChannel();

			// keep table size 
			while (this.connectionMap.size() >= this.size) {
				logger.log(Level.INFO, "Connection pool is full. Remove an entry.");

				// remove an entry randomly
				//synchronized (this.connectionMap) {
					int removeIdx = rnd.nextInt(this.connectionMap.size());

					SocketAddress removedKey = null;
					for (SocketAddress key: this.connectionMap.keySet()) {
						if (removeIdx == 0) {
							removedKey = key;
							break;
						}
						removeIdx--;
					}

					if (removedKey != null) {
						es = this.connectionMap.remove(removedKey);
						if (es != null) existedChannel = es.getSocketChannel();
					}
				//}
			}

			// put
			expiringTime = System.currentTimeMillis() + this.expirationTime;
			this.connectionMap.put(addr, new ExpiringSocketChannel(sock, expiringTime));
		}	// synchronized (this.connectionMap)

		initExpiringTask(expiringTime + 100L);

		// disposes an existing connection
		if (existedChannel != null) {
			try {
				existedChannel.close();
			}
			catch (IOException e) { /* ignore */ }
		}
	}

	public void clear() {
		if (this.size <= 0) return;

		synchronized (connectionMap) {
			for (ExpiringSocketChannel es: this.connectionMap.values()) {
				SocketChannel sock = es.getSocketChannel();
				try { sock.close(); } catch (IOException e) { /* ignore */ }
			}

			this.connectionMap.clear();
		}
	}

	//
	// Expiration-related methods and classes
	//

	private void initExpiringTask(long expiringTime) {
		synchronized (this) {
			if (this.expiringTask != null) {
				long scheduledTime = timer.getScheduledTime(this.expiringTask);
				if (expiringTime < scheduledTime) {
					// reschedule
					timer.cancel(this.expiringTask);
					timer.schedule(this.expiringTask, expiringTime, true /*isDaemon*/);
				}
			}
			else {
				// create an ExpiringTask and schedule it
				this.expiringTask = new ExpiringTask();
				timer.schedule(this.expiringTask, expiringTime, true /*isDaemon*/);
			}
		}
	}

	private class ExpiringTask extends TimerTask {
		public void run() {
			long currentTime = System.currentTimeMillis();
			Set<SocketAddress> removeSet = new HashSet<SocketAddress>();
			long nearestExpiringTime = Long.MAX_VALUE;

			synchronized (connectionMap) {
				for (Map.Entry<SocketAddress,ExpiringSocketChannel> ent: connectionMap.entrySet()) {
					long expiringTime = ent.getValue().expiringTime;
					if (currentTime >= expiringTime) {
						// to expire
						removeSet.add(ent.getKey());
					}
					else if (expiringTime < nearestExpiringTime) {
						nearestExpiringTime = expiringTime;
					}
				}

				for (SocketAddress addr: removeSet) {
					ExpiringSocketChannel es = connectionMap.remove(addr);
					SocketChannel sock = es.getSocketChannel();
					try { sock.close(); } catch (IOException e) { /* ignore */ }
				}
			}

			if (nearestExpiringTime < Long.MAX_VALUE) {
				// reschedule
				timer.schedule(this, nearestExpiringTime, true /*isDaemon*/);
			}
			else {
				// stop
				synchronized (ConnectionPool.this) {
					ConnectionPool.this.expiringTask = null;
				}
			}
		}
	}

	private static class ExpiringSocketChannel {
		private SocketChannel sock;
		private long expiringTime;

		ExpiringSocketChannel(SocketChannel sock, long expiringTime) {
			this.sock = sock;
			this.expiringTime = expiringTime;
		}

		public SocketChannel getSocketChannel() { return this.sock; }
		public long getExpiringTime() { return this.expiringTime; }
	}
}

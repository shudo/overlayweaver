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

package ow.messaging.udp;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.messaging.InetMessagingAddress;
import ow.messaging.Message;
import ow.messaging.MessageDirectory;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.udp.message.PunchHoleRepMessage;
import ow.messaging.udp.message.PunchHoleReqMessage;
import ow.stat.MessagingReporter;
import ow.util.AlarmClock;
import ow.util.concurrent.ExecutorBlockingMode;
import ow.util.concurrent.SingletonThreadPoolExecutors;

public final class UDPMessageSender implements MessageSender {
	private final static Logger logger = Logger.getLogger("messaging");

	/** maximum message size */
	final static int MAX_MSG_SIZE = 65536;

	private final UDPMessageReceiver receiver;
	private final boolean forReceiver;

	UDPMessageSender(UDPMessageReceiver receiver, boolean forReceiver) {
		this.receiver = receiver;
		this.forReceiver = forReceiver;
	}

	public void send(MessagingAddress dest, Message msg) throws IOException {
		this.adjustLoopbackAddress((InetMessagingAddress)dest);

		// set source address
		if (msg.getSource() == null)
			msg.setSource(this.receiver.getSelfAddress());

		// destination is local
		MessagingAddress selfAddress = this.receiver.getSelfAddress();
		if (dest.equals(selfAddress)) {
			this.receiver.processMessage(msg);
			this.receiver.postProcessMessage(msg);
			return;
		}

		// destination is remote

		// prepare socket
		DatagramChannel sock;
		if (!this.forReceiver) {
			sock = this.receiver.sockPool.get();
		}
		else {
			sock = this.receiver.sock;
		}

		try {
			send(sock, ((InetMessagingAddress)dest).getInetSocketAddress(), dest, msg, false);
		}
		catch (IOException e) {
			// rethrow Exception and dispose socket
			throw e;
		}

		if (!this.forReceiver) {
			this.receiver.sockPool.put(sock);
		}
	}

	ByteBuffer send(DatagramChannel sock, SocketAddress sockAddr /* actual destination */,
			MessagingAddress dest, Message msg, boolean isReply) throws IOException {
		// UDP hole punching
		if (!(msg instanceof PunchHoleReqMessage)
				&& !(msg instanceof PunchHoleRepMessage)
				&& dest != null
				&& !this.receiver.getSelfAddress().equals(dest)
				&& !isReply) {
			if (this.receiver.beginFirstPunching()) {
				this.receiver.lastSendDest = (InetMessagingAddress)dest;

				this.receiver.punchHole();

				// update the message with the new MessagingAddress
				MessagingAddress src = msg.getSource();
				if (src != null) {
					src.copyFrom(this.receiver.getSelfAddress());
				}
			}
		}

		logger.log(Level.INFO, "send("
				+ (dest != null ? dest : sockAddr)
				+ ", " + MessageDirectory.getName(msg.getTag()) + ")");

		// set signature
		byte[] sig = this.receiver.provider.getMessageSignature();
		msg.setSignature(sig);

		// send
		ByteBuffer buf = msg.encode();
		int payloadLen = buf.remaining();

		if (payloadLen >= UDPMessageSender.MAX_MSG_SIZE) {
			logger.log(Level.WARNING, "message is too large: " + payloadLen);
			throw new IOException("message is too large: " + payloadLen);
		}

		try {
			sock.send(buf, sockAddr);
			buf.rewind();

			if (dest != null) {
				this.receiver.setLastSend((InetMessagingAddress)dest);	// for UDP hole punching

				// notify statistics collector
				MessagingReporter msgReporter = this.receiver.getMessagingReporter();
				if (msgReporter != null) {
					msgReporter.notifyStatCollectorOfMessageSent(dest, msg, buf.remaining());
				}
			}
		}
		catch (IOException e) {
			// notify statistics collector
			if (dest != null) {
				MessagingReporter msgReporter = this.receiver.getMessagingReporter();
				if (msgReporter != null) {
					msgReporter.notifyStatCollectorOfDeletedNode(dest);
				}
			}

			throw e;
		}

		return buf;
	}

	public Message sendAndReceive(MessagingAddress dest, final Message msg) throws IOException {
		this.adjustLoopbackAddress((InetMessagingAddress)dest);

		Message ret = null;

		// set source address
		if (msg.getSource() == null)
			msg.setSource(this.receiver.getSelfAddress());

		// destination is local
		MessagingAddress selfAddress = this.receiver.getSelfAddress();
		if (dest.equals(selfAddress)) {
			ret = this.receiver.processMessage(msg);

			if (this.receiver.extMessageHandlerRegistered) {
				Runnable r = new Runnable () {
					public void run() {
						UDPMessageSender.this.receiver.postProcessMessage(msg);
					}
				};

				if (UDPMessageSender.this.receiver.config.getUseThreadPool()) {
					SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.CONCURRENT_NON_BLOCKING, false).submit(r);
				}
				else {
					Thread t = new Thread(r);
					t.setName("TCPMessageSender: post-processing thread");
					t.setDaemon(false);
					t.start();
				}
			}

			return ret;
		}

		// destination is remote

		// prepare socket
		DatagramChannel sock;
		ByteBuffer buf = ByteBuffer.allocate(UDPMessageSender.MAX_MSG_SIZE);

		if (!this.forReceiver) {
			sock = this.receiver.sockPool.get();
		}
		else {
			sock = this.receiver.sock;
		}

		// receive and dispose remaining data
		sock.configureBlocking(false);
		while (true) {
			SocketAddress src = sock.receive(buf);
			if (src == null) break;
			buf.rewind();

			logger.log(Level.INFO, "Data have remained in TCP/IP protocol stack.");
		}
		sock.configureBlocking(true);

		// send
		long timeout = this.receiver.provider.getTimeoutCalculator().calculateTimeout(dest);
		long start = System.currentTimeMillis();
		try {
			send(sock, ((InetMessagingAddress)dest).getInetSocketAddress(), dest, msg, false);
		}
		catch (IOException e) {
			// rethrow Exception and dispose socket
			throw e;
		}

		// receive
		try {
			AlarmClock.setAlarm(timeout);

			sock.receive(buf);
				// should try to fill the buffer?
			buf.rewind();

			ret = Message.decode(buf);

			AlarmClock.clearAlarm();

			// timeout calculation
			this.receiver.provider.getTimeoutCalculator().updateRTT(dest, (int)(System.currentTimeMillis() - start));
		}
		catch (Exception e) {
			Thread.interrupted();

			logger.log(Level.INFO, "Timeout: " + timeout + " msec.");

			// notify statistics collector
			MessagingReporter msgReporter = this.receiver.getMessagingReporter();
			if (msgReporter != null) {
				msgReporter.notifyStatCollectorOfDeletedNode(dest);
			}

			throw new IOException("Timeout:" + timeout + " msec.");
		}

		if (!this.forReceiver) {
			this.receiver.sockPool.put(sock);
		}

		return ret;
	}

	private void adjustLoopbackAddress(InetMessagingAddress dest) {
		// adjust loopback address (e.g. 127.0.0.1) to a real address
		if (dest.getInetAddress().isLoopbackAddress()) {
			dest.setInetAddress(((InetMessagingAddress)this.receiver.getSelfAddress().getMessagingAddress()).getInetAddress());

			logger.log(Level.INFO, "destination is loopback address and adjusted to " + dest);
		}
	}
}

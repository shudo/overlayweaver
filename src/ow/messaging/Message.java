/*
 * Copyright 2006-2008,2010-2011 National Institute of Advanced Industrial Science
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public abstract class Message implements Serializable {
	private final static Logger logger = Logger.getLogger("messaging");

	public final static boolean GZIP_MESSAGE = true;

	private byte[] signature;
	private int tag;
	private MessagingAddress src;	// set by a MessageSender

	/**
	 * Instantiate a Message class.
	 */
	protected Message() {
		this.tag = MessageDirectory.getTagByClass(this.getClass());
	}

	/** Return the source address. */
	public MessagingAddress getSource() { return this.src; }

	/** Set the source address. */
	public MessagingAddress setSource(MessagingAddress src) {
		MessagingAddress old = this.src;
		this.src = src;
		return old;
	} 

	public byte[] setSignature(byte[] sig) {
		byte[] old = this.signature;
		this.signature = sig;
		return old;
	}

	/**
	 * Returns the signature.
	 */
	public byte[] getSignature() {
		return this.signature;
	}

	/**
	 * Returns the tag.
	 */
	public int getTag() {
		return this.tag;
	}

	// an utility method
	public String getName() {
		return MessageDirectory.getName(this.tag);
	}

	// an utility method
	public boolean getToBeReported() {
		return MessageDirectory.getToBeReported(this.tag);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("Message {src:");
		sb.append(this.src);
		sb.append(",tag:");
		sb.append(MessageDirectory.getName(this.tag));
		sb.append("}");

		return sb.toString();
	}

	/**
	 * Subclasses write its members into the given stream.
	 */
	protected abstract void encodeContents(ObjectOutputStream oos) throws IOException ;

	/**
	 * Convert this Message to a ByteBuffer.
	 */
	public ByteBuffer encode() {
		// serializes src and contents
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		OutputStream os = bos;

		try {
			if (GZIP_MESSAGE) {
				os = new GZIPOutputStream(os);
			}

			ObjectOutputStream oos = new ObjectOutputStream(os);
			oos.writeObject(this.src);

			this.encodeContents(oos);

			oos.flush();
			oos.close();
		}
		catch (IOException e) {
			// NOTREACHED
			logger.log(Level.SEVERE,
					"Serialization failed: " + this.tag);
		}

		byte[] srcAndContents = bos.toByteArray();
		int sigLen = Signature.getSignatureLength();

		// pack all elements
		ByteBuffer buf = ByteBuffer.allocate(
				 sigLen + 8 + srcAndContents.length);
		if (this.signature != null) {
			buf.put(this.signature, 0, sigLen);
		}
		else {
			buf.put(new byte[sigLen]);
		}
		buf.put((byte)this.tag);
		buf.putInt(srcAndContents.length);
		buf.put(srcAndContents);

		buf.rewind();

		return buf;
	}

	/**
	 * This is an utility method which writes this Message into a byte stream.
	 */
	public ByteBuffer encode(ByteChannel out) throws IOException {
		logger.log(Level.INFO, "tag:" + MessageDirectory.getName(this.tag));

		ByteBuffer buf = this.encode();

		try {
			synchronized (out) {
				do {
					out.write(buf);
				} while (buf.hasRemaining());
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Could not write a message.");
			throw e;
		}

		buf.rewind();

		return buf;
	}

	/**
	 * Subclasses read its members from the given stream.
	 */
	protected abstract void decodeContents(ObjectInputStream ois) throws IOException, ClassNotFoundException;

	/**
	 * Convert a ByteBuffer to a Message.
	 */
	public static Message decode(ByteBuffer buf) throws IOException {
		buf.mark();

		int sigLen = Signature.getSignatureLength();
		byte[] signature = new byte[sigLen];
		buf.get(signature, 0, sigLen);

		int tag = buf.get();
		int len = buf.getInt();

		byte[] srcAndContent = new byte[len];
		buf.get(srcAndContent);

		buf.reset();

		// instantiate a Message
		Message msg;
		try {
			msg = MessageDirectory.getClassByTag(tag).newInstance();
			msg.setSignature(signature);
			msg.tag = tag;
		}
		catch (Exception e) {
			logger.log(Level.SEVERE, "Message instantiation failed. tag: " + tag);
			throw new IOException("Message instantiation failed. tag: " + tag);
		}

		InputStream is = new ByteArrayInputStream(srcAndContent);
		try {
			if (GZIP_MESSAGE) {
				is = new GZIPInputStream(is);
			}
			ObjectInputStream ois = new ObjectInputStream(is);

			MessagingAddress src = (MessagingAddress)ois.readObject(); 
			msg.setSource(src);

			msg.decodeContents(ois);

			ois.close();
		}
		catch (IOException e) {
			// NOTREACHED
			logger.log(Level.SEVERE, "An IOException thrown.", e);
			throw e;
		}
		catch (ClassNotFoundException e) {
			// NOTREACHED
			logger.log(Level.SEVERE, "Class not found.", e);
			throw new IOException("Class not found: " + e);
		}

		return msg;
	}

	/**
	 * This an utility method which reads a Message from the given input stream.
	 */
	public static Message decode(SocketChannel in) throws IOException {
		return decode(in, -1L);
	}

	/**
	 * This an utility method which reads a Message from the given input stream.
	 */
	public static Message decode(SocketChannel in, long timeout) throws IOException {
		// read header
		int sigLen = Signature.getSignatureLength();
		ByteBuffer headerBuf = ByteBuffer.allocate(sigLen + 8);
		fillBuffer(in, headerBuf, timeout);

		headerBuf.position(sigLen + 4);
		int len = headerBuf.getInt();

		// read source and content
		ByteBuffer buf = ByteBuffer.allocate(sigLen + 8 + len);

		headerBuf.rewind();
		buf.rewind();
		buf.put(headerBuf);

		fillBuffer(in, buf, -1L);
		buf.rewind();

		return Message.decode(buf);
	}

	private static void fillBuffer(SocketChannel in, ByteBuffer buf, long timeout)
			throws IOException {
		// select
		if (timeout > 0) {
			in.configureBlocking(false);

			Selector sel = Selector.open();
			in.register(sel, SelectionKey.OP_READ);
			int nKeys = sel.select(timeout);

			sel.close();
				// prevents an IllegalBlockingModeException thrown
				// when calling in.configureBlocking(true).
			in.configureBlocking(true);

			if (nKeys <= 0) {
				throw new IOException("Read timed out (keep-alive time has passed).");
			}
		}

		// read
		int len = buf.remaining();

		try {
			do {
				int r = in.read(buf);
				if (r < 0) {
					logger.log(Level.INFO, "Reached end-of-stream.");
					throw new IOException("End-of-stream.");
				}
				len -= r;
			} while (len > 0);
		}
		catch (IOException e) {	// catch just for logging
			logger.log(Level.INFO, "Could not read a message.");
			throw e;
		}
	}
}

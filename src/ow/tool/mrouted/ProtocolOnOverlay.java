/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.tool.mrouted;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ProtocolOnOverlay {
	private final static Logger logger = Logger.getLogger("mrouted");

	public final static int HEADER_LEN = 22;
	public final static int SRC_ADDR_OFFSET = 0;
	public final static int SRC_PORT_OFFSET = 4;
	public final static int DEST_ADDR_OFFSET = 6;
	public final static int DEST_PORT_OFFSET = 10;
	public final static int ID_OFFSET = 12;
	public final static int IP_TTL_OFFSET = 14;
	public final static int OVERLAY_TTL_OFFSET = 16;
	public final static int DATA_LEN_OFFSET = 18;

	static int getHeaderLen() {
		return HEADER_LEN;
	}

	static int encode(byte[] encoded, Inet4Address srcAddr, int srcPort, Inet4Address destAddr, int destPort, int id, int ipTTL, int overlayTTL, byte[] data) {
		// addresses
		setSrcAddress(encoded, srcAddr);
		setDestAddress(encoded, destAddr);

		// port numbers
		setSrcPort(encoded, srcPort);
		setDestPort(encoded, destPort);

		// ID
		setID(encoded, id);

		// IP TTL
		setIPTTL(encoded, ipTTL);

		// Overlay TTL
		setOverlayTTL(encoded, overlayTTL);

		// data
		int dataLen = data.length;
		encoded[DATA_LEN_OFFSET] =  (byte)(dataLen >> 24);
		encoded[DATA_LEN_OFFSET + 1] = (byte)(dataLen >> 16);
		encoded[DATA_LEN_OFFSET + 2] = (byte)(dataLen >> 8);
		encoded[DATA_LEN_OFFSET + 3] = (byte)(dataLen);

		int copyLen = Math.min(dataLen, encoded.length - HEADER_LEN);
		System.arraycopy(data, 0, encoded, HEADER_LEN, copyLen);

		return HEADER_LEN + copyLen;
	}

	static int decode(DecodedMessageOnOverlay decoded, byte[] encoded) {
		// addresses
		Inet4Address srcAddr = getSrcAddress(encoded);
		Inet4Address destAddr = getDestAddress(encoded);

		// port numbers
		int srcPort = getSrcPort(encoded);
		int destPort = getDestPort(encoded);

		int id = getID(encoded);

		// IP TTL
		int ipTTL = getIPTTL(encoded);

		// Overlay TTL
		int overlayTTL = getOverlayTTL(encoded);

		// data
		int dataLen = 0;
		dataLen |= (((int)encoded[DATA_LEN_OFFSET]) & 0xff) << 24;
		dataLen |= (((int)encoded[DATA_LEN_OFFSET + 1]) & 0xff) << 16;
		dataLen |= (((int)encoded[DATA_LEN_OFFSET + 2]) & 0xff) << 8;
		dataLen |= (((int)encoded[DATA_LEN_OFFSET + 3]) & 0xff);

		byte[] buf = decoded.getData();
		int bufLen = 0;
		if (buf == null) {
			bufLen = Math.min(dataLen, ApplicationLevelMulticastRouterConfiguration.DEFAULT_DATAGRAM_BUFFER_SIZE);
			buf = new byte[bufLen];
		}
		else {
			bufLen = buf.length;
		}

		int copyLen = Math.min(bufLen, dataLen);
		copyLen = Math.min(copyLen, encoded.length - HEADER_LEN);
		if (copyLen != dataLen) {
			logger.log(Level.WARNING, "Received data have been trimmed from " + dataLen + " byte to " + copyLen + ".");
		}

		System.arraycopy(encoded, HEADER_LEN ,buf, 0, copyLen);

		// return
		decoded.setSrcAddress(srcAddr);
		decoded.setDestAddress(destAddr);
		decoded.setSrcPort(srcPort);
		decoded.setDestPort(destPort);
		decoded.setID(id);
		decoded.setIPTTL(ipTTL);
		decoded.setOverlayTTL(overlayTTL);
		decoded.setData(buf);
		decoded.setDataLength(copyLen);

		return copyLen;
	}

	static int getID(byte[] encoded) {
		return getUnsignedInt16(encoded, ID_OFFSET);
	}

	static void setID(byte[] encoded, int id) {
		setUnsignedInt16(encoded, ID_OFFSET, id);
	}

	static int getIPTTL(byte[] encoded) {
		return getUnsignedInt16(encoded, IP_TTL_OFFSET);
	}

	static void setIPTTL(byte[] encoded, int ttl) {
		setUnsignedInt16(encoded, IP_TTL_OFFSET, ttl);
	}

	static int getOverlayTTL(byte[] encoded) {
		return getUnsignedInt16(encoded, OVERLAY_TTL_OFFSET);
	}

	static void setOverlayTTL(byte[] encoded, int ttl) {
		setUnsignedInt16(encoded, OVERLAY_TTL_OFFSET, ttl);
	}

	static Inet4Address getSrcAddress(byte[] encoded) {
		return getAddress(encoded, SRC_ADDR_OFFSET);
	}

	static Inet4Address getDestAddress(byte[] encoded) {
		return getAddress(encoded, DEST_ADDR_OFFSET);
	}

	static void setSrcAddress(byte[] encoded, Inet4Address addr) {
		setAddress(encoded, SRC_ADDR_OFFSET, addr);
	}

	static void setDestAddress(byte[] encoded, Inet4Address addr) {
		setAddress(encoded, DEST_ADDR_OFFSET, addr);
	}

	static int getSrcPort(byte[] encoded) {
		return getUnsignedInt16(encoded, SRC_PORT_OFFSET);
	}

	static int getDestPort(byte[] encoded) {
		return getUnsignedInt16(encoded, DEST_PORT_OFFSET);
	}

	static void setSrcPort(byte[] encoded, int port) {
		setUnsignedInt16(encoded, SRC_PORT_OFFSET, port);
	}

	static void setDestPort(byte[] encoded, int port) {
		setUnsignedInt16(encoded, DEST_PORT_OFFSET, port);
	}

	private static Inet4Address getAddress(byte[] encoded, int offset) {
		byte[] bytes = new byte[4];
		System.arraycopy(encoded, offset, bytes, 0, 4);

		Inet4Address addr = null;
		try {
			addr = (Inet4Address)Inet4Address.getByAddress(bytes);
		}
		catch (UnknownHostException e) {
			// NOTREACHED
			logger.log(Level.WARNING, "An UnknownHostException thrown.", e);
		}

		return addr;
	}

	private static void setAddress(byte[] encoded, int offset, Inet4Address addr) {
		byte[] bytes = addr.getAddress();
		System.arraycopy(bytes, 0, encoded, offset, 4);
	}

	private static int getUnsignedInt16(byte[] encoded, int offset) {
		int result = 0;
		result |= (encoded[offset] & 0xff) << 8;
		result |= (encoded[offset + 1] & 0xff);

		return result;
	}

	private static void setUnsignedInt16(byte[] encoded, int offset, int value) {
		encoded[offset] = (byte)(value >> 8);
		encoded[offset + 1] = (byte)(value);
	}
}

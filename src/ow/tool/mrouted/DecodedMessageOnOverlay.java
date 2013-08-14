/*
 * Copyright 2006 National Institute of Advanced Industrial Science
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

class DecodedMessageOnOverlay {
	private Inet4Address srcAddr, destAddr;
	private int srcPort, destPort;
	private int id = 0;
	private int ipTTL = 0;
	private int overlayTTL = 0;
	private int dataLen;
	private byte[] data;

	DecodedMessageOnOverlay(byte[] data) {
		this.srcAddr = this.destAddr = null;
		this.srcPort = this.destPort = -1;
		this.data = data;
	}

	Inet4Address getSrcAddress() { return this.srcAddr; }
	Inet4Address setSrcAddress(Inet4Address src) {
		Inet4Address old = this.srcAddr;
		this.srcAddr = src;
		return old;
	}

	Inet4Address getDestAddress() { return this.destAddr; }
	Inet4Address setDestAddress(Inet4Address dest) {
		Inet4Address old = this.destAddr;
		this.destAddr = dest;
		return old;
	}

	int getSrcPort() { return this.srcPort; }
	int setSrcPort(int port) {
		int old = this.srcPort;
		this.srcPort = port;
		return old;
	}

	int getDestPort() { return this.destPort; }
	int setDestPort(int port) {
		int old = this.destPort;
		this.destPort = port;
		return old;
	}

	int getID() { return this.id; }
	int setID(int id) {
		int old = this.id;
		this.id = id;
		return old;
	}

	int getIPTTL() { return this.ipTTL; }
	int setIPTTL(int ttl) {
		int old = this.ipTTL;
		this.ipTTL = ttl;
		return old;
	}

	int getOverlayTTL() { return this.overlayTTL; }
	int setOverlayTTL(int ttl) {
		int old = this.overlayTTL;
		this.overlayTTL = ttl;
		return old;
	}

	int getDataLength() { return this.dataLen; }
	int setDataLength(int len) {
		int old = this.dataLen;
		this.dataLen = len;
		return old;
	}

	byte[] getData() { return this.data; }
	byte[] setData(byte[] data) {
		byte[] old = this.data;
		this.data = data;
		return old;
	}
}

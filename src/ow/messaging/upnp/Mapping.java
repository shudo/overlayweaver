/*
 * Copyright 2007 Kazuyuki Shudo, and contributors.
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

package ow.messaging.upnp;

/**
 * An instance of this class represents an address and port mapping on an Internet router.
 */
public final class Mapping {
	private final int externalPort;
	private final String internalAddress;
	private final int internalPort;
	private final Mapping.Protocol protocol;
	private final String description;

	public enum Protocol {
		UDP, TCP;
	}

	public Mapping(int externalPort,	// 3997
			String internalAddress,		// "192.168.0.1"
			int internalPort,			// 3997
			Mapping.Protocol protocol,	// Mapping.Protocol.UDP
			String description) {
		this.externalPort = externalPort;
		this.internalAddress = internalAddress;
		this.internalPort = internalPort;
		this.protocol = protocol;
		this.description = description;
	}

	public int getExternalPort() { return this.externalPort; }
	public String getInternalAddress() { return this.internalAddress; }
	public int getInternalPort() { return this.internalPort; }
	public Protocol getProtocol() { return this.protocol; }
	public String getDescription() { return this.description; }

	public int hashCode() {
		return (this.protocol.ordinal() << 16) + this.internalPort;
	}

	public boolean equals(Object o) {
		if (!(o instanceof Mapping))
			return false;

		Mapping m = (Mapping)o;
		if (m.hashCode() == this.hashCode())
			return true;

		return false;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("{external port:").append(this.externalPort);
		sb.append(", internal port:").append(this.internalPort);
		sb.append(", internal addr:").append(this.internalAddress);
		sb.append(", protocol:").append(this.protocol);
		if (this.description != null) {
			sb.append(", desc:").append(this.description);
		}
		sb.append("}");

		return sb.toString();
	}
}

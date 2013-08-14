/*
 * Copyright 2006,2009 National Institute of Advanced Industrial Science
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

package ow.ipmulticast;

import java.net.Inet4Address;

import ow.util.Timer;

class Querier {
	private Inet4Address address;
	private int igmpVersion;
	private long respondedTime;

	Querier(Inet4Address address, int igmpVersion) {
		this.address = address;
		this.igmpVersion = igmpVersion;
		this.respondedTime = Timer.currentTimeMillis();
	}

	public Inet4Address getAddress() { return this.address; }

	public int getIGMPVersion() { return this.igmpVersion; }
	public int setIGMPVersion(int ver) {
		int old = this.igmpVersion;
		this.igmpVersion = ver;
		return old;
	}

	public long getRespondedTime() { return this.respondedTime; }
	public long updateRespondedTime() {
		long old = this.respondedTime;
		this.respondedTime = Timer.currentTimeMillis();
		return old;
	}

	public String toString() {
		return toString("");
	}

	public String toString(String indent) {
		StringBuilder sb = new StringBuilder();

		sb.append(indent);
		sb.append(this.address);
		sb.append(":IGMPv");
		sb.append(this.igmpVersion);

		return sb.toString();
	}
}

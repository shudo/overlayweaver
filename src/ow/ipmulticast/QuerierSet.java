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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ow.util.Timer;

public class QuerierSet {
	Map<Inet4Address,Querier> querierMap = new HashMap<Inet4Address,Querier>();
	private int lowestIGMPVersion = IGMP.DEFAULT_IGMP_VERSION;

	public int getLowestIGMPVersion() { return this.lowestIGMPVersion; }

	public void registerQuerier(Inet4Address address, int igmpVersion) {
		Querier querier = null;

		synchronized (this.querierMap) {
			querier = this.querierMap.get(address);
			if (querier != null) {
				querier.updateRespondedTime();
				querier.setIGMPVersion(igmpVersion);
			}
			else {
				querier = new Querier(address, igmpVersion);
				this.querierMap.put(address, querier);
			}

			if (igmpVersion < this.lowestIGMPVersion) {
				this.lowestIGMPVersion = igmpVersion;
			}
		}
	}

	public boolean expire(long expiration) {
		long now = Timer.currentTimeMillis();
		int lowestVer = 3;

		boolean changed = false;
		Set<Inet4Address> removedKeySet = new HashSet<Inet4Address>();

		synchronized (this.querierMap) {
			for (Inet4Address addr: this.querierMap.keySet()) {
				Querier querier = this.querierMap.get(addr);
				if (now >= querier.getRespondedTime() + expiration) {	// expire
					removedKeySet.add(addr);
				}
				else {
					int ver = querier.getIGMPVersion(); 
					if (ver < lowestVer) {
						lowestVer = ver;
					}
				}
			}

			for (Inet4Address removedKey: removedKeySet) {
				this.querierMap.remove(removedKey);
				changed = true;
			}
		}

		this.lowestIGMPVersion = lowestVer;

		return changed;
	}

	public String toString() {
		return toString("");
	}

	public String toString(String indent) {
		StringBuilder sb = new StringBuilder();

		sb.append(indent);
		sb.append("querier [");
		for (Querier q: this.querierMap.values()) {
			sb.append("\n");
			sb.append(indent);
			sb.append(q.toString("  "));
		}
		sb.append("\n");
		sb.append(indent);
		sb.append("]");

		return sb.toString();
	}
}

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
import java.util.HashSet;
import java.util.Set;

import ow.util.Timer;

public class Host {
	private Inet4Address address;
	private int igmpVersion;
	private final boolean isSelf;

	private long respondedTime;

	private FilterMode filterMode = FilterMode.EXCLUDE;
	private Set<Inet4Address> sourceSet = new HashSet<Inet4Address>();

	Host(Inet4Address address, int igmpVersion, boolean isSelf) {
		this.address = address;
		this.igmpVersion = igmpVersion;
		this.isSelf = isSelf;

		this.respondedTime = Timer.currentTimeMillis();
	}

	public Inet4Address getAddress() { return this.address; }

	public int getIGMPVersion() { return this.igmpVersion; }
	public int setIGMPVersion(int ver) {
		int old = this.igmpVersion;
		this.igmpVersion = ver;
		return old;
	}

	public boolean isSelf() { return this.isSelf; }

	public long getRespondedTime() { return this.respondedTime; }
	public long updateRespondedTime() {
		long old = this.respondedTime;
		this.respondedTime = Timer.currentTimeMillis();
		return old;
	}

	public FilterMode getFilterMode() { return this.filterMode; }
	public FilterMode setFilterMode(FilterMode newMode) {
		FilterMode old = this.filterMode;
		this.filterMode = newMode;
		return old;
	}

	public void clearSourceSet() { this.sourceSet.clear(); }
	public Set<Inet4Address> getSourceSet() { return this.sourceSet; }
	public void addSourceSet(Set<Inet4Address> set) {
		synchronized (set) {
			this.sourceSet.addAll(set);
		}
	}
	public boolean removeSourceSet(Set<Inet4Address> set) {
		synchronized (set) {
			return this.sourceSet.removeAll(set);
		}
	}
	public void setSourceSet(Set<Inet4Address> set) {
		this.sourceSet.clear();
		synchronized (set) {
			this.sourceSet.addAll(set);
		}
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
		sb.append(":");
		sb.append((this.filterMode == FilterMode.EXCLUDE) ? "EXCLUDE" : "INCLUDE");
		for (Inet4Address src: this.sourceSet) {
			sb.append(":");
			sb.append(src);
		}

		return sb.toString();
	}
}

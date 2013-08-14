/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.util.Timer;

public final class Group {
	private final static Logger logger = Logger.getLogger("ipmulticast");

	private final Inet4Address address;
	private final VirtualInterface vif;
	private Filter filter;
	private int lowestIGMPVersion = IGMP.DEFAULT_IGMP_VERSION;
	private Map<Inet4Address,Host> hostMap = new HashMap<Inet4Address,Host>();

	Group(Inet4Address address, VirtualInterface vif) {
		this.address = address;
		this.vif = vif;

		this.updateFilter();
	}

	public Inet4Address getGroupAddress() { return this.address; }
	public VirtualInterface getVirtualInterface() { return this.vif; }

	public int getLowestIGMPVersion() { return this.lowestIGMPVersion; }

	public int numOfHosts() {
		return this.hostMap.size();
	}

	public Host getHost(Inet4Address address) {
		Host host = null;

		synchronized (this.hostMap) {
			host = this.hostMap.get(address);
		}

		return host;	// can return null
	}

	public Host registerHost(Inet4Address address, int igmpVersion) {
		Host host = null;

		synchronized (this.hostMap) {
			host = this.hostMap.get(address);
			if (host != null) {
				if (igmpVersion < host.getIGMPVersion()) {
					host.setIGMPVersion(igmpVersion);
				}

				host.updateRespondedTime();
			}
			else {
				boolean isSelf = address.equals(vif.getLocalAddress());

				host = new Host(address, igmpVersion, isSelf);
				this.hostMap.put(address, host);
			}

			if (igmpVersion < this.lowestIGMPVersion) {
				this.lowestIGMPVersion = igmpVersion;
			}
		}

		return host;	// does not return null
	}

	public Host unregisterHost(Inet4Address address) {
		Host host = null;

		synchronized (this.hostMap) {
			host = this.hostMap.remove(address);

			// re-calculate lowest IGMP version
			if (host != null
					&& host.getIGMPVersion() <= this.lowestIGMPVersion) {
				int lowestVer = IGMP.DEFAULT_IGMP_VERSION;

				for (Inet4Address addr: this.hostMap.keySet()) {
					Host h = this.hostMap.get(addr);
					int ver = h.getIGMPVersion();
					if (ver < lowestVer) {
						lowestVer = ver;
					}
				}

				this.lowestIGMPVersion = lowestVer;
			}
		}

		return host;
	}

	public Collection<Host> getAllHost() {
		return this.hostMap.values();
	}

	/**
	 * Returns the filter for this group.
	 * Note that it is not guaranteed that the returned filter is up to date.
	 * You need to call updateFilter() before getFilter() to obtain an up-to-date filter.
	 */
	public synchronized Filter getFilter() {
		return this.filter;
	}

	/**
	 * Updates the filter for this group.
	 *
	 */
	public synchronized void updateFilter() {
		this.filter = this.calculateFilter();
	}

	/**
	 * Constructs and returns a filter.
	 */
	private synchronized Filter calculateFilter() {
		boolean excludeExists = false;
		boolean includeExists = false;
		Filter filter;

		synchronized (this.hostMap) {
			for (Host host: this.hostMap.values()) {
				FilterMode filterMode = host.getFilterMode();
				if (filterMode == FilterMode.EXCLUDE) {
					excludeExists = true;
				}
				else {	// INCLUDE
					includeExists = true;
				}
			}

			FilterMode m = ((includeExists && !excludeExists) ? FilterMode.INCLUDE : FilterMode.EXCLUDE);
			filter = new Filter(m);
			Set<Inet4Address> sourceSet = new HashSet<Inet4Address>();

			if (m == FilterMode.EXCLUDE) {
				for (Host host: this.hostMap.values()) {
					if (host.getFilterMode() == FilterMode.EXCLUDE) {
						Set<Inet4Address> s = host.getSourceSet();
						synchronized (s) {
							sourceSet.addAll(s);
						}
					}
				}

				for (Host host: this.hostMap.values()) {
					if (host.getFilterMode() == FilterMode.INCLUDE) {
						Set<Inet4Address> s = host.getSourceSet();
						synchronized (s) {
							sourceSet.removeAll(s);
						}
					}
				}
			}
			else {	// INCLUDE
				for (Host host: this.hostMap.values()) {
					Set<Inet4Address> s = host.getSourceSet();
					synchronized (s) {
						sourceSet.addAll(s);
					}
				}
			}

			filter.setSourceSet(sourceSet);
		}

		return filter;
	}

	protected boolean expire(long expiration) {
		long now = Timer.currentTimeMillis();
		int lowestVer = 3;

		boolean changed = false;
		Set<Inet4Address> removedKeySet = new HashSet<Inet4Address>();

		synchronized (this.hostMap) {
			for (Inet4Address addr: this.hostMap.keySet()) {
				Host host = this.hostMap.get(addr);
//System.out.print(" " + host.getAddress());
//System.out.println("  " + ((now - host.getRespondedTime()) / 1000.0));
				if (now >= host.getRespondedTime() + expiration) {	// expire
					removedKeySet.add(addr);
//System.out.println("   expires.");
				}
				else {
					int ver = host.getIGMPVersion(); 
					if (ver < lowestVer) {
						lowestVer = ver;
					}
				}
			}

			for (Inet4Address removedKey: removedKeySet) {
				this.hostMap.remove(removedKey);
				changed = true;

				logger.log(Level.INFO, removedKey + " expired in a group " + this.address + ".");
			}
		}

		this.lowestIGMPVersion = lowestVer;

		this.updateFilter();

		return changed;
	}

	public String toString() {
		return toString("");
	}

	public String toString(String indent) {
		StringBuilder sb = new StringBuilder();

		sb.append(indent);
		sb.append(this.address);
		sb.append(":IGMPv");
		sb.append(this.lowestIGMPVersion);
		sb.append(" [");
		for (Host h: this.hostMap.values()) {
			sb.append("\n");
			sb.append(indent);
			sb.append(h.toString("  "));
		}
		sb.append("\n");
		sb.append(indent);
		sb.append("]");

		return sb.toString();
	}
}

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

package ow.ipmulticast;

import java.net.Inet4Address;
import java.util.HashSet;
import java.util.Set;

/**
 * This class represents a filter,
 * which determines whether an incoming multicast packet is to be passed or not. 
 */
public class Filter {
	private FilterMode filterMode;	// INCLUDE or EXCLUDE
	private Set<Inet4Address> sourceSet;

	public Filter(FilterMode filterMode) {
		this.filterMode = filterMode;
		this.sourceSet = new HashSet<Inet4Address>();
	}

	public boolean doBlock(Inet4Address source) {
		boolean contained = this.sourceSet.contains(source);

		if (this.filterMode == FilterMode.EXCLUDE) {
			return contained;
		}
		else {	// INCLUDE
			return !contained;
		}
	}

	public synchronized void setSourceSet(Set<Inet4Address> sourceSet) {
		this.sourceSet.clear();
		synchronized (sourceSet) {
			this.sourceSet.addAll(sourceSet);
		}
	}

	public synchronized void addSource(Inet4Address address) {
		this.sourceSet.add(address);
	}

	public synchronized boolean removeSource(Inet4Address address) {
		return this.sourceSet.remove(address);
	}
}

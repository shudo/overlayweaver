/*
 * Copyright 2007,2009-2010 Kazuyuki Shudo, and contributors.
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

package ow.routing;

import java.io.Serializable;

import ow.id.IDAddressPair;
import ow.util.Timer;

/**
 * An instance of this class represents a hop in a route.
 */
public final class RoutingHop implements Serializable {
	private final IDAddressPair node;
	private final long time;

	private RoutingHop(IDAddressPair node, long time) {	// prohibit direct instantiation
		this.node = node;
		this.time = time;
	}

	public static RoutingHop newInstance(IDAddressPair node) {
		return new RoutingHop(node, Timer.currentTimeMillis());
	}

	public IDAddressPair getIDAddressPair() { return this.node; }
	public long getTime() { return this.time; }

	public String toString() { return this.toString(0); }

	public String toString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append(this.node.toString(verboseLevel));
		sb.append(" (").append(this.time).append(")");

		return sb.toString();
	}
}

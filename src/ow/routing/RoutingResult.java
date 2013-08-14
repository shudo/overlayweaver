/*
 * Copyright 2006-2007,2010 Kazuyuki Shudo, and contributors.
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

/**
 * An instance of this class represents a routing result,
 * which consists of the route and the neighbors of the responsible node.
 */
public final class RoutingResult implements Serializable {
	private final RoutingHop[] route;
	private final IDAddressPair[] responsibleNodeCandidates;

	public RoutingResult(RoutingHop[] r, IDAddressPair[] n) {
		this.route = r;
		this.responsibleNodeCandidates = n;
	}

	/**
	 * Returns the route.
	 */
	public RoutingHop[] getRoute() { return this.route; }

	/**
	 * Returns candidates for the responsible nodes,
	 * in other words, the responsible node and its neighbors.
	 */
	public IDAddressPair[] getResponsibleNodeCandidates() { return this.responsibleNodeCandidates; }

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("RoutingResult { ");

		if (this.route != null && this.route.length > 0) {
			long timeBase = this.route[0].getTime();

			sb.append("route: ");
			sb.append(this.route[0].getIDAddressPair().getAddress());

			for (int i = 1; i < this.route.length; i++) {
				sb.append(", ");
				sb.append(this.route[i].getIDAddressPair().getAddress());
				sb.append(" (");
				sb.append(this.route[i].getTime() - timeBase);
				sb.append(")");
			}

			sb.append(" ");
		}

		sb.append("responsibleNodeCand: ");
		if (this.responsibleNodeCandidates == null) {
			sb.append("(null)");
		}
		else if (this.responsibleNodeCandidates.length <= 0) {
			sb.append("(length=0)");
		}
		else {
			sb.append(this.responsibleNodeCandidates[0].getAddress());
			for (int i = 1; i < this.responsibleNodeCandidates.length; i++) {
				sb.append(", ");
				sb.append(this.responsibleNodeCandidates[i].getAddress());
			}
		}

		sb.append(" }");

		return sb.toString();
	}
}

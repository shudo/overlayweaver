/*
 * Copyright 2006,2010 Kazuyuki Shudo, and contributors.
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

/**
 * Context about routing passed between nodes on the route.
 * Note that an instance of RoutingContext should be immutable.
 */
public abstract class RoutingContext implements Serializable {
	protected RoutingContext() {}	// default constructor

	protected RoutingContext(RoutingContext c) {}	// for clone

	public abstract RoutingContext clone();

	public String toString(int verboseLevel) { return this.toString(); }
}

/*
 * Copyright 2006,2010 National Institute of Advanced Industrial Science
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

package ow.routing;

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;

public interface CallbackOnRoute {
	/**
	 * A callback method, which is called by a routing driver on each hop in a route.
	 *
	 * @param target destination of the routing.
	 * @param tag tag given by the initiator of the routing.
	 * @param callbackArgs arguments given by the initiator of the routing.
	 * @param filter filter which processes the result returned by a callback.
	 * @param lastHop the last hop of this node. possibly null.
	 * @param onResponsibleNode true if this node is the responsible node.
	 * @return the result on the responsible node is passed to the initiator.
	 */
	Serializable process(ID target, int tag, Serializable[] callbackArgs, IDAddressPair lastHop, boolean onResponsibleNode);
}

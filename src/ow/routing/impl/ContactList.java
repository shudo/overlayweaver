/*
 * Copyright 2006,2008 National Institute of Advanced Industrial Science
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

package ow.routing.impl;

import ow.id.IDAddressPair;

/**
 * A sorted list of {@link ow.id.IDAddressPair IDAddressPair}
 * for {@link ow.routing.impl.IterativeRoutingDriver IterativeRoutingDriver}.
 */
interface ContactList {
	int size();

	int numOfContactedNodes();

	void clear();

	/**
	 * Add a contact to this list.
	 *
	 * @return true if the first contact has been changed.
	 */
	boolean add(IDAddressPair contact);
	boolean addAsContacted(IDAddressPair contact);

	boolean remove(IDAddressPair contact);

	IDAddressPair inspect();
	IDAddressPair inspectExceptContactedNode();
	IDAddressPair first();
	IDAddressPair firstExceptContactedNode();

	boolean isContactedNode(IDAddressPair node);
}

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

package ow.tool.util.vizframework;

import ow.id.ID;

/**
 * A pair of two IDs.
 */
class IDPairAndTag {
	private ID id0, id1;
	int tag;

	public IDPairAndTag(ID id0, ID id1, int tag) {
		this.id0 = id0;
		this.id1 = id1;
		this.tag = tag;
	}

	public ID getID0() { return this.id0; }
	public ID getID1() { return this.id1; }
	public int getTag() { return this.tag; }

	public int hashCode() {
		return id0.hashCode() ^ id1.hashCode() ^ this.tag;
	}

	public boolean equals(Object o) {
		if (o instanceof IDPairAndTag) {
			IDPairAndTag other = (IDPairAndTag)o;
			
			if (this.tag == other.tag
					&& this.id0.equals(other.id0)
					&& this.id1.equals(other.id1)) {
				return true;
			}
		}
		return false;
	}

	public String toString() {
		String result = (id0 == null ? "(null)" : id0.toString()); 
		result += "-";
		result += (id1 == null ? "(null)" : id1.toString());
		result += ":";
		result += tag;

		return result;
	}
}

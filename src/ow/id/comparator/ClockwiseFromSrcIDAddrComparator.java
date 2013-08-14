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

package ow.id.comparator;

import java.math.BigInteger;
import java.util.Comparator;

import ow.id.ID;
import ow.id.IDAddressPair;

public final class ClockwiseFromSrcIDAddrComparator implements Comparator<IDAddressPair> {
	private BigInteger sourceIDInteger;
	private final BigInteger ID_SPACE_SIZE;

	public ClockwiseFromSrcIDAddrComparator(int idSizeInBit, ID sourceID) {
		this.sourceIDInteger = sourceID.toBigInteger();

		this.ID_SPACE_SIZE = BigInteger.ONE.shiftLeft(idSizeInBit);
	}

	public int compare(IDAddressPair p1, IDAddressPair p2) {
		BigInteger dist1 = p1.getID().toBigInteger().subtract(this.sourceIDInteger);
		if (dist1.compareTo(BigInteger.ZERO) < 0) {
			dist1 = dist1.add(this.ID_SPACE_SIZE);
		}

		BigInteger dist2 = p2.getID().toBigInteger().subtract(this.sourceIDInteger);
		if (dist2.compareTo(BigInteger.ZERO) < 0) {
			dist2 = dist2.add(this.ID_SPACE_SIZE);
		}

		return dist1.compareTo(dist2);
	}
}

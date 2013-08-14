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
import java.net.UnknownHostException;

public class Utilities {
	public static Inet4Address intToInet4Address(int v) {
		Inet4Address addr = null;

		try {
			addr = (Inet4Address)Inet4Address.getByAddress(Utilities.intToByteArray(v));
		}
		catch (UnknownHostException e) {
			// NOTREACHED
			System.err.println("Inet4Address.getByAddress() throws an UnknownHostException.");
		}

		return addr;
	}

	public static byte[] intToByteArray(int v) {
		byte[] result = new byte[4];
	
		for (int i = 0; i < 4; i++) {
			result[i] = (byte)(v >>> ((3 - i) * 8));
		}
	
		return result;
	}

	public static int byteArrayToInt(byte[] bArray, int start, int len) {
		int result = 0;

		for (int i = 0; i < len; i++) {
			result <<= 8;
			result |= ((int)bArray[start + i] & 0xff);
		}

		return result;
	}

	public static int Inet4AddressToInt(Inet4Address addr) {
		int result = 0;
		byte[] bytes;
	
		if (addr != null) {
			bytes = addr.getAddress();
			for (int i = 0; i < 4; i++) {
				result |= (bytes[i] & 0xff) << ((3 - i) * 8);
			}
		}
	
		return result;
	}

	public static long Inet4AddressToLong(Inet4Address addr) {
		long result = 0;
		byte[] bytes;

		if (addr != null) {
			bytes = addr.getAddress();
			for (int i =0; i < 4; i++) {
				result |= (bytes[i] & 0xffL) << ((3 - i) * 8);
			}
		}

		return result;
	}
}

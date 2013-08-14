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

package ow.id;

public class IDTest {
	public static void main(String[] args) {
//		ID a = ID.getID("000102030405060708091011121314151617181920", 20);
		ID a = ID.getID("7e7310dc1feccee9b8c092d1c0f38e48ab47a18d", 20);
		System.out.println("a: " + a);

		ID b = ID.getID(a.toString(), 20);
		System.out.println("b: " + b);

//		ID c = ID.getID("000102030405060708091011121314151617181720", 20);
		ID c = ID.getID("0616ec2427434b623ca58afaf6f167e4223dc0fc", 20);
		System.out.println("c: " + c);
		System.out.println("matchLengthFromMSB(a, c): " + ID.matchLengthFromMSB(a, c));

		System.out.println("a.getBits(7, 8): 0x" + Integer.toHexString(a.getBits(8, 8)));
	}
}

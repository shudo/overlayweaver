/*
 * Copyright 2007 Kazuyuki Shudo, and contributors.
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

package ow.messaging.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class AccessControlTest {
	private AccessController ac;

	public static void main(String[] args) throws IOException {
		(new AccessControlTest()).test(args);
	}

	private void test(String[] args) throws IOException {
		this.ac = new AccessController();
		this.ac.parse(args[0]);

		this.test("192.168.0.128");
		this.test("192.168.0.64");
		this.test("192.168.0.32");
		this.test("192.168.0.2");
		this.test("192.168.0.1");
	}

	private void test(String host) throws UnknownHostException {
		InetAddress addr = InetAddress.getByName(host);

		System.out.println(host + ": "
				+ (this.ac.allow(addr) ? "pass" : "rejected"));
	}
}

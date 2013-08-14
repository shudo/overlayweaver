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

package ow.tool.emulator;

public class WorkerTableTest {
	public final static void main(String[] args) throws Exception {
		RemoteAppInstanceTable table = RemoteAppInstanceTable.readHostFile(args[0]);

		System.out.println(table.getWorkerHostAndPort(0));
		System.out.println(table.getWorkerHostAndPort(1));
		System.out.println(table.getWorkerHostAndPort(2));
		System.out.println(table.getWorkerHostAndPort(3));
		System.out.println(table.getWorkerHostAndPort(100));
		System.out.println(table.getWorkerHostAndPort(-10));

		String str = table.getStringRepresentation();
		System.out.println(str);

		table = RemoteAppInstanceTable.parseString(str);

		str = table.getStringRepresentation();
		System.out.println(str);
	}
}

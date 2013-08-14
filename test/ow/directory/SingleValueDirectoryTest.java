/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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

package ow.directory;

import ow.id.ID;

public class SingleValueDirectoryTest {
	public final static int ID_SIZE = 20; 

	public static void main(String[] args) throws Exception {
		String providerName = "BerkeleyDB";
//		String providerName = "PersistentMap";
//		String providerName = "VolatileMap";

		DirectoryProvider dirProvider = DirectoryFactory.getProvider(providerName);
		DirectoryConfiguration dirConfig = DirectoryConfiguration.getDefaultConfiguration();

		dirConfig.setExpirationTime(3000);

		SingleValueDirectory<ID,Data> dir0 = dirProvider.openSingleValueDirectory(ID.class, Data.class, "./", "test0", dirConfig);
		SingleValueDirectory<ID,Data> dir1 = dirProvider.openSingleValueDirectory(ID.class, Data.class, "./", "test1", dirConfig);

		byte[] b0 = { 1 };
		byte[] b1 = { 2 };
		ID id0 = ID.getSHA1BasedID(b0, ID_SIZE);
		ID id1 = ID.getSHA1BasedID(b1, ID_SIZE);

		printEntry(dir0, id0);
		printEntry(dir0, id1);

		dir0.put(id0, new Data(id0, "abcdef"));
		dir0.put(id1, new Data(id1, "ghijkl"));
		dir0.put(id1, new Data(id1, "mnopqr"));

		dir0.remove(id1);

		printEntry(dir0, id0);
		printEntry(dir0, id1);

		System.out.println("keySet: " + dir0.keySet());

		dir1.put(id1, new Data(id1, "foo"));
		dir1.remove(id1);
		printEntry(dir1, id0);
		printEntry(dir1, id1);

//		dir.clear();
//		System.out.println("keySet: " + dir.keySet());

		// wait for the synchronization (in case of "PersistentMap")
		Thread.sleep(5000);

		dir0.close();
	}

	private static void printEntry(SingleValueDirectory<ID,Data> map, ID id) throws Exception {
		Data val = map.get(id);

		System.out.print(id);
		System.out.print(": ");
		System.out.print(val);
		System.out.println();
	}

	public static class Data implements java.io.Serializable {
		ID id;
		String str;

		Data(ID id, String str) {
			this.id = id;
			this.str = str;
		}

		public String toString() {
			return id.toString() + ", " + this.str;
		}
	}
}

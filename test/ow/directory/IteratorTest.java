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

import java.util.Iterator;
import java.util.Map.Entry;

public class IteratorTest {
	public static void main(String[] args) throws Exception {
		String providerName = "BerkeleyDB";
//		String providerName = "PersistentMap";
//		String providerName = "VolatileMap";

		DirectoryProvider dirProvider = DirectoryFactory.getProvider(providerName);
		DirectoryConfiguration dirConfig = DirectoryConfiguration.getDefaultConfiguration();

//		dirConfig.setExpirationTime(3000L);

		MultiValueDirectory<String,String> dir = dirProvider.openMultiValueDirectory(String.class, String.class, "./", "test", dirConfig);

		// put
		dir.put("a", "A0");
		dir.put("a", "A1");
		dir.put("b", "B0");
		dir.put("b", "B1");
		dir.put("c", "C");
		dir.put("d", "D");
		dir.put("e", "E");
		dir.put("f", "F");

		for (Entry<String,String> e: dir) {
			System.out.println(e);
		}

		System.out.println();

		// remove
		System.out.println("remove entries with key \"a\".");

		for (Iterator<Entry<String,String>> it = dir.iterator(); it.hasNext(); ) {
			Entry<String,String> e = it.next();

			if ("a".equals(e.getKey())) {
				it.remove();
			}
		}

		dir.put("g", "G");
		dir.put("h", "H");

		for (Entry<String,String> e: dir) {
			System.out.println(e);
		}

		// wait for the synchronization (in case of "PersistentMap")
		Thread.sleep(5000);

		dir.close();
	}
}

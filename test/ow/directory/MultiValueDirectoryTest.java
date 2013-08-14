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


public class MultiValueDirectoryTest {
	public static void main(String[] args) throws Exception {
		String providerName = "BerkeleyDB";
//		String providerName = "PersistentMap";
//		String providerName = "VolatileMap";

		DirectoryProvider dirProvider = DirectoryFactory.getProvider(providerName);
		DirectoryConfiguration dirConfig = DirectoryConfiguration.getDefaultConfiguration();

//		dirConfig.setExpirationTime(3000L);

		MultiValueDirectory<String,String> dir = dirProvider.openMultiValueDirectory(String.class, String.class, "./", "test", dirConfig);

		System.out.println(dir.get("abc"));
		System.out.println(dir.get("def"));

		dir.put("abc", "ABC");
		dir.put("def", "DEF");
		dir.put("abc", "GHI");
		dir.put("abc", "JKL");
		dir.put("abc", "JKL");
		dir.put("abc", "JKL");
		dir.put("abc", "JKL");
		dir.put("abc", "JKL");

		System.out.println(dir.get("abc") + " should be [ABC, GHI, JKL]");
		System.out.println(dir.get("def") + " should be [DEF]");

		System.out.println(dir.remove("abc", "AAA") + " should be null");
			// does not remove

		System.out.println(dir.get("abc") + " should be [ABC, GHI, JKL]");

		System.out.println(dir.remove("abc", "GHI") + " should be GHI");

		System.out.println(dir.get("abc") + " should be [ABC, JKL]");

		System.out.println("keySet: " + dir.keySet() + " should be [abc, def]");

		System.out.println(dir.remove("abc") + " should be [ABC, JKL]");

		System.out.println(dir.get("abc") + " should be null");

		System.out.println(dir.remove("def", "DEF") + " should be DEF");

		System.out.println("keySet: " + dir.keySet() + " should be []");

		// wait for the synchronization (in case of "PersistentMap")
		Thread.sleep(5000);

		dir.close();
	}
}

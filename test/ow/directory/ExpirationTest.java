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

import java.util.Map;
import java.util.Set;

public class ExpirationTest {
//	public final static String PROVIDER_NAME = "BerkeleyDB"; 
//	public final static String PROVIDER_NAME = "PersistentMap";
	public final static String PROVIDER_NAME = "VolatileMap";

	public final static int EXPIRATION = 3000; 

	public static void main(String[] args) throws Exception {
		DirectoryProvider dirProvider = DirectoryFactory.getProvider(PROVIDER_NAME);
		DirectoryConfiguration dirConfig = DirectoryConfiguration.getDefaultConfiguration();

		dirConfig.setExpirationTime(EXPIRATION);

		SingleValueDirectory<String,String> dir = dirProvider.openSingleValueDirectory(String.class, String.class, "./", "test", dirConfig);
//		MultiValueDirectory<String,String> dir = dirProvider.openMultiValueDirectory(String.class, String.class, "./", "test", dirConfig);

		// put
		dir.put("a", "ABC");
		dir.put("b", "BCD");

		Thread.sleep(2000);

		dir.put("c", "CDE");
		dir.put("c", "cde");
		dir.put("d", "DEF", 5000);

		// print
		System.out.println("contents:");
		synchronized (dir) {
			if (true) {
				// entrySet()
				Set<Map.Entry<String,String>> set = dir.entrySet();
				System.out.println("num: " + set.size());
				for (Map.Entry<String,String> e: set) {
					System.out.println(e);
				}
			}
			else {
				// iterator()
				for (Map.Entry<String,String> e: dir) {
					System.out.println(e);
//					Thread.sleep(500);
				}
			}
		}

		// sleep
		try {
			Thread.sleep(4000L);
		}
		catch (InterruptedException e) {}

		// print
		System.out.println("contents:");
		synchronized (dir) {
			for (Map.Entry<String,String> e: dir) {
				System.out.println(e);
			}
		}
	}
}

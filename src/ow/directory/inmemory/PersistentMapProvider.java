/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.directory.inmemory;

import java.io.File;

import ow.directory.DirectoryConfiguration;
import ow.directory.DirectoryProvider;
import ow.directory.MultiValueDirectory;
import ow.directory.SingleValueDirectory;

public final class PersistentMapProvider extends DirectoryProvider {
	private final static String NAME = "PersistentMap";

	// configuration
	private final static long SYNC_INTERVAL = 3 * 1000L;	// 3 sec

	public String getName() { return NAME; }

	protected <K,V> SingleValueDirectory<K,V> provideSingleValueDirectory(Class typeK, Class typeV, String dir, String dbName,
			DirectoryConfiguration config) throws Exception {
		return new SingleValueHashDirectory<K,V>(typeK, typeV, dir, dbName, getBackupFilename(dbName),
				config, SYNC_INTERVAL);
	}

	protected <K,V> MultiValueDirectory<K,V> provideMultiValueDirectory(Class typeK, Class typeV, String dir, String dbName,
			DirectoryConfiguration config) throws Exception {
		return new MultipleValueHashDirectory<K,V>(typeK, typeV, dir, dbName, getBackupFilename(dbName),
				config, SYNC_INTERVAL);
	}

	public void removeDirectory(String dir, String dbName) throws Exception {
		File dbFile = new File(dir + dbName);
		File backupFile = new File(dir + getBackupFilename(dbName) + ".old");

		dbFile.delete();
		backupFile.delete();
	}

	//
	// Utility methods
	//

	private static String getBackupFilename(String name) {
		return name + ".old";
	}
}

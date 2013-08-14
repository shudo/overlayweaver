/*
 * Copyright 2006,2009 National Institute of Advanced Industrial Science
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

package ow.directory.berkeleydb;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ow.directory.DirectoryConfiguration;
import ow.directory.DirectoryProvider;
import ow.directory.MultiValueDirectory;
import ow.directory.SingleValueDirectory;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.Transaction;

public class BerkeleyDBProvider extends DirectoryProvider {
	private final static String NAME = "BerkeleyDB";

	// configuration
	protected final static boolean ALLOW_CREATE = true;

	private final Map<File,Environment> envTable = Collections.synchronizedMap(new HashMap<File,Environment>());
	private EnvironmentConfig envConfig = null;
	private boolean initialized = false;

	public String getName() {
		return NAME;
	}

	protected <K,V> SingleValueDirectory<K,V> provideSingleValueDirectory(Class typeK, Class typeV, String workingDir, String dbName,
			DirectoryConfiguration config /* ignored */) throws Exception {
		init();

		Environment env = this.getEnvironment(new File(workingDir));
		return new SingleValueJEDirectory<K,V>(typeK, typeV, env, dbName);
	}

	protected <K,V> MultiValueDirectory<K,V> provideMultiValueDirectory(Class typeK, Class typeV, String workingDir, String dbName,
			DirectoryConfiguration config /* ignored */) throws Exception {
		init();

		Environment env = this.getEnvironment(new File(workingDir));
		return new MultiValueJEDirectory<K,V>(typeK, typeV, env, dbName);
	}

	private void init() {
		if (!this.initialized) {
			// prepare Environment config
			this.envConfig = new EnvironmentConfig();
			this.envConfig.setTransactional(true);
			this.envConfig.setAllowCreate(BerkeleyDBProvider.ALLOW_CREATE);	// create a DB if it does not exist.

			this.initialized = true;
		}
	}

	public void removeDirectory(String dir, String dbName) throws Exception {
		Environment env = this.getEnvironment(new File(dir));
			// throws DatabaseException

		Transaction txn = env.beginTransaction(null, null);
		env.removeDatabase(txn, dbName);
		env.removeDatabase(txn, dbName + ".catalog");
		txn.commit();
	}

	//
	// Utility methods
	//

	private Environment getEnvironment(File dir) throws DatabaseException {
		Environment env = this.envTable.get(dir);
		if (env == null) {
			env = new Environment(dir, this.envConfig);
			this.envTable.put(dir, env);
		}

		return env;
	}
}

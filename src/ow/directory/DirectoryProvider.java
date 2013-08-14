/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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

import ow.directory.expiration.ExpiringMultiValueDirectory;
import ow.directory.expiration.ExpiringSingleValueDirectory;
import ow.directory.expiration.ExpiringValue;

/**
 * The interface which a directory provider for various backend databases implements.
 */
public abstract class DirectoryProvider {
	/**
	 * Return the name of the provider.
	 */
	public abstract String getName();

	/**
	 * Create a directory and return it.
	 */
	public <K,V> SingleValueDirectory<K,V> openSingleValueDirectory(Class typeK, Class typeV, String workingDir, String dbName,
			DirectoryConfiguration config) throws Exception {
		if (config == null) config = DirectoryConfiguration.getDefaultConfiguration();

		if (config.getExpirationTime() < 0L) {
			return this.provideSingleValueDirectory(typeK, typeV, workingDir, dbName, config);
		}
		else {
			SingleValueDirectory<K,ExpiringValue<V>> dir =
				this.provideSingleValueDirectory(typeK, ExpiringValue/*<V>*/.class, workingDir, dbName, config);
					// to be written as ExpiringValue<V>.class

			return new ExpiringSingleValueDirectory(dir, config.getExpirationTime());
				// TODO
				// should be parameterized.
		}
	}

	/**
	 * Create a directory and return it.
	 * The directory holds multiple values associated with the same key.
	 */
	public <K,V> MultiValueDirectory<K,V> openMultiValueDirectory(Class typeK, Class typeV, String workingDir, String dbName,
			DirectoryConfiguration config) throws Exception {
		if (config == null) config = DirectoryConfiguration.getDefaultConfiguration();

		if (config.getExpirationTime() < 0L) {
			return this.provideMultiValueDirectory(typeK, typeV, workingDir, dbName, config);
		}
		else {
			MultiValueDirectory<K,ExpiringValue<V>> dir =
				this.provideMultiValueDirectory(typeK, ExpiringValue/*<V>*/.class, workingDir, dbName, config);
					// to be written as MultiValueExpiringDirectory<K,V>.ExpiringValue<V>.class

			return new ExpiringMultiValueDirectory(dir, config.getExpirationTime());
				// TODO
				// should be parameterized.
		}
	}

	public abstract void removeDirectory(String dir, String dbName) throws Exception;

	//
	// Methods to be overriden
	//
	protected abstract <K,V> SingleValueDirectory<K,V> provideSingleValueDirectory(Class typeK, Class typeV, String workingDir, String dbName, DirectoryConfiguration config) throws Exception;
	protected abstract <K,V> MultiValueDirectory<K,V> provideMultiValueDirectory(Class typeK, Class typeV, String workingDir, String dbName, DirectoryConfiguration config) throws Exception;
}

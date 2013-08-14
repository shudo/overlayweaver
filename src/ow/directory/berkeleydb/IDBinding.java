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

import ow.id.ID;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;

public class IDBinding implements EntryBinding<ID> {
	public IDBinding() {}

	public ID entryToObject(DatabaseEntry entry) {
		byte[] bytes = new byte[entry.getSize()];
		System.arraycopy(entry.getData(), entry.getOffset(), bytes, 0, bytes.length);
		return ID.getID(bytes, bytes.length);
	}

	public void objectToEntry(ID o, DatabaseEntry entry) {
		byte[] bytes = (byte[])o.getValue();
		entry.setData(bytes, 0, bytes.length);
	}
}

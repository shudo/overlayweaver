/*
 * Copyright 2009 Kazuyuki Shudo, and contributors.
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

import java.io.Serializable;

public class DirectoryConfiguration implements Serializable {
	public static DirectoryConfiguration getDefaultConfiguration() {
		return new DirectoryConfiguration();
	}

	public enum HeapOverflowAction {
		DO_NOT_CARE,	// At last, an OutOfMemoryError is thrown
		IGNORE,		// A newly put key-value pair is ignored 
		THROW_AN_OUT_OF_HEAP_EXCEPTION,	// A ow.directory.OutOfHeapException is thrown
		LRU;			// A LRU key-value pair is removed
	}


	public final static long DEFAULT_EXPIRATION_TIME = -1L;	// does not expire
	public final static HeapOverflowAction DEFAULT_HEAP_OVERFLOW_ACTION = HeapOverflowAction.LRU;
	public final static long DEFAULT_REQUIRED_FREE_HEAP_TO_PUT = 128 * 1024L;	// 128 KB


	// Expiration time (in millisecond)
	public long expirationTime = DEFAULT_EXPIRATION_TIME;
	public long getExpirationTime() { return this.expirationTime; }
	public long setExpirationTime(long expTime) {
		long old = this.expirationTime;
		this.expirationTime = expTime;
		return old;
	}

	// How does the directory treat heap overflow
	// Note: this flag works only with on-memory key-value store.
	// In other words, works with "VolatileMap" and "PersistentMap" and does not work with "BerkeleyDB".
	public HeapOverflowAction heapOverflowAction = DEFAULT_HEAP_OVERFLOW_ACTION;
	public HeapOverflowAction getHeapOverflowAction() { return this.heapOverflowAction; }
	public HeapOverflowAction setHeapOverflowAction(HeapOverflowAction flag) {
		HeapOverflowAction old = this.heapOverflowAction;
		this.heapOverflowAction = flag;
		return old;
	}

	private long reqFreeHeap = DEFAULT_REQUIRED_FREE_HEAP_TO_PUT;
	public long getRequiredFreeHeapToPut() { return this.reqFreeHeap; }
	public long setRequiredFreeHeapToPut(long mem) {
		long old = this.reqFreeHeap;
		this.reqFreeHeap = mem;
		return old;
	}
}

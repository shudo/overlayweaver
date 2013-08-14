/*
 * Copyright 2008 Kazuyuki Shudo, and contributors.
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

package ow.dht.memcached;

import java.math.BigInteger;
import java.util.Set;

import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.routing.RoutingException;

public interface Memcached extends DHT<Item> {
	public final static String ENCODING = "UTF-8";
	public final static String PUBLIC_SECRET = "secret";

	public static enum Condition {
		NONE,		// for "set" command
		NOT_EXIST,	// for "add" command
		EXIST,		// for "replace" command
		APPEND,		// for "append" command
		PREPEND,	// for "prepend" command
		CAS,		// for "cas" command
		INCREMENT,	// for "incr" command
		DECREMENT	// for "decr" command
	}

	//
	// utility constants
	//

	public final static BigInteger UINT64_MAX =
		BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

	//
	// methods
	//

	Set<ValueInfo<Item>> put(ID key, Item value, Condition cond)
		throws Exception;

	Set<ValueInfo<Item>> put(ID key, Item value, int casUnique)
		throws Exception;

	Set<ValueInfo<Item>> remove(ID key)
		throws RoutingException;

	public static class PutRequest extends DHT.PutRequest<Item> {
		private final Condition cond;
		private final int casUnique;

		public PutRequest(ID key, Item[] values, Condition cond) {
			this(key, values, cond, 0);
		}

		public PutRequest(ID key, Item[] values, Condition cond, int casUnique) {
			super(key, values);
			this.cond = cond;
			this.casUnique = casUnique;
		}

		public Condition getCondition() { return this.cond; }
		public int getCasUnique() { return this.casUnique; }
	}
}

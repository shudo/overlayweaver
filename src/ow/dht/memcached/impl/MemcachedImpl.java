/*
 * Copyright 2008,2011 Kazuyuki Shudo, and contributors.
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

package ow.dht.memcached.impl;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import ow.dht.ByteArray;
import ow.dht.DHTConfiguration;
import ow.dht.ValueInfo;
import ow.dht.impl.ChurnTolerantDHTImpl;
import ow.dht.impl.message.DHTReplyMessage;
import ow.dht.memcached.Item;
import ow.dht.memcached.Memcached;
import ow.dht.memcached.impl.message.PutOnConditionMessage;
import ow.id.ID;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.routing.RoutingException;
import ow.routing.RoutingService;

/**
 * An implementation of DHT service for memcached.
 * It supports conditional put operations.
 */
public final class MemcachedImpl extends ChurnTolerantDHTImpl<Item> implements Memcached {
	private ByteArray hashedSecret;

	public MemcachedImpl(short applicationID, short applicationVersion,
			DHTConfiguration config, ID selfID /* possibly null */)
				throws Exception {
		super(applicationID, applicationVersion, config, selfID);
		this.init();
	}

	public MemcachedImpl(DHTConfiguration config, RoutingService routingSvc)
			throws Exception {
		super(config, routingSvc);
		this.init();
	}

	private void init() {
		this.hashedSecret = (new ByteArray(new byte[0])).hashWithSHA1();
		super.setHashedSecretForPut(this.hashedSecret);
	}

	protected void prepareHandlers(RoutingService routingSvc) {
		super.prepareHandlers(routingSvc);

		MessageHandler handler;

		handler = new PutOnConditionMessageHandler();
		routingSvc.addMessageHandler(PutOnConditionMessage.class, handler);
	}

	public Set<ValueInfo<Item>> put(ID key, Item value, Condition cond)
			throws Exception {
		return this.put(key, value, cond, 0);
	}

	public Set<ValueInfo<Item>> put(ID key, Item value, int casUnique)
			throws Exception {
		return this.put(key, value, Condition.CAS, casUnique);
	}

	private Set<ValueInfo<Item>> put(ID key, Item value, Condition cond, int casUnique)
			throws Exception {
		if (cond == null || cond.equals(Condition.NONE)) return this.put(key, value);

		Item[] values = new Item[1];
		values[0] = value;

		Memcached.PutRequest[] requests = new Memcached.PutRequest[1];
		requests[0] = new Memcached.PutRequest(key, values, cond, casUnique);

		Set<ValueInfo<Item>>[] ret = this.put(requests);	// calls BasicDHTImpl#call(DHT.PutRequest[])

		if (requests[0] == null) throw new RoutingException();

		return ret[0];
	}

	public Set<ValueInfo<Item>> remove(ID key)
			throws RoutingException {
		return super.remove(key, this.hashedSecret);
	}

	private class PutOnConditionMessageHandler extends ChurnTolerantDHTImpl<Item>.PutMessageHandler {
		public Message process(Message msg) {
			Memcached.PutRequest[] requests = ((PutOnConditionMessage)msg).requests;

			int size = requests.length;
			boolean[] adopted = new boolean[size];
			int numAdopted = 0;
			Item[] incrOrDecrResults = null;

			//synchronized (globalDir) {	// can cause a (distributed) dead-lock
			Set<ValueInfo<Item>>[] existedValues = new Set/*<ValueInfo<Item>>*/[size];	// for "cas" command

			for (int i = 0; i < size; i++) {
				Item[] values;

				Set<ValueInfo<Item>> existedValue = null;
				try {
					existedValue = globalDir.get(requests[i].getKey());
					existedValues[i] = existedValue;
				}
				catch (Exception e) {}

				Condition cond = requests[i].getCondition();
				switch (cond) {
				case NOT_EXIST:
					if (existedValue == null) { adopted[i] = true; numAdopted++; }
					break;
				case EXIST:
					if (existedValue != null) { adopted[i] = true; numAdopted++; }
					break;
				case APPEND:
					if (existedValue == null) break;
					adopted[i] = true; numAdopted++;
					values = requests[i].getValues();
					for (ValueInfo<Item> v: existedValue) {
						byte[] old = v.getValue().getData();
						byte[] appended = values[0].getData();
						byte[] newarray = new byte[old.length + appended.length];
						System.arraycopy(old, 0, newarray, 0, old.length);
						System.arraycopy(appended, 0, newarray, old.length, appended.length);
						values[0] = new Item(newarray, v.getValue().getFlag());	// new value to be put
					}
					break;
				case PREPEND:
					if (existedValue == null) break;
					adopted[i] = true; numAdopted++;
					values = requests[i].getValues();
					for (ValueInfo<Item> v: existedValue) {
						byte[] old = v.getValue().getData();
						byte[] prepended = values[0].getData();
						byte[] newarray = new byte[prepended.length + old.length];
						System.arraycopy(prepended, 0, newarray, 0, prepended.length);
						System.arraycopy(old, 0, newarray, prepended.length, old.length);
						values[0] = new Item(newarray, v.getValue().getFlag());	// new value to be put
					}
					break;
				case CAS:
					if (existedValue == null) break;
					for (ValueInfo<Item> v: existedValue) {
						Item old = v.getValue();
						if (requests[i].getCasUnique() == old.hashCode()) { adopted[i] = true; numAdopted++; }
					}
					break;
				case INCREMENT:
					if (existedValue == null) break;
					adopted[i] = true; numAdopted++;

					values = requests[i].getValues();

					if (incrOrDecrResults == null) incrOrDecrResults = new Item[size];
					incrOrDecrResults[i] = this.incrementOrDecrement(true, existedValue, values);

					break;
				case DECREMENT:
					if (existedValue == null) break;
					adopted[i] = true; numAdopted++;

					values = requests[i].getValues();

					if (incrOrDecrResults == null) incrOrDecrResults = new Item[size];
					incrOrDecrResults[i] = this.incrementOrDecrement(false, existedValue, values);

					break;
				}
			}

			if (numAdopted < size) {
				Memcached.PutRequest[] copiedRequests = new Memcached.PutRequest[size];
				for (int i = 0; i < size; i++) {
					if (adopted[i]) copiedRequests[i] = requests[i];
				}
				((PutOnConditionMessage)msg).requests = copiedRequests;
			}

			// put
			Message ret = super.process(msg);

			Set<ValueInfo<Item>>[] retValues = ((DHTReplyMessage<Item>)ret).existedValues;
			for (int i = 0; i < size; i++) {
				Condition cond = requests[i].getCondition();

				switch (cond) {
				case CAS:
					// adjust existed values set for "cas" command
					// "NOT_FOUND": set is null
					// "EXISTS": set is not null, but has no value
					// "STORED": set is not null, and has a value
					if (!adopted[i] && existedValues[i] != null)
						retValues[i] = new HashSet<ValueInfo<Item>>();	// "EXISTS"
					break;
				case INCREMENT:
				case DECREMENT:
					// replace an existed value with the resulting value
					if (adopted[i]) {
						ValueInfo<Item> vi = null;
						for (ValueInfo<Item> returnedVI: retValues[i]) {
							vi = returnedVI; break;
						}

						vi = new ValueInfo<Item>(incrOrDecrResults[i], vi.getAttributes());
						retValues[i].clear();
						retValues[i].add(vi);
					}
					break;
				}
			}
			//}	// synchronized (globalDir)

			return ret;
		}

		private Item incrementOrDecrement(boolean increment,
				Set<ValueInfo<Item>> existedValues, Item[] values) {
			Item ret = null;

			// parse a value
			String valueStr = null;
			try {
				valueStr = new String(values[0].getData(), Memcached.ENCODING);
			}
			catch (UnsupportedEncodingException e) { /* NOTREACHED */ }

			BigInteger value = BigInteger.ZERO;
			try {
				value = new BigInteger(valueStr);
			}
			catch (NumberFormatException e) { /* ignore */ }

			// increment
			for (ValueInfo<Item> v: existedValues) {

				// parse an existing value 
				String existedValueStr = null;
				byte[] b = v.getValue().getData();
				int existedValueSize = b.length;
				int len = 0; for (; len < existedValueSize; len++) { if (b[len] == 0) break; }
				try {
					existedValueStr = new String(b, 0, len, Memcached.ENCODING);
				}
				catch (UnsupportedEncodingException e) { /* NOTREACHED */ }

				BigInteger existedValue = BigInteger.ZERO;
				try {
					existedValue = new BigInteger(existedValueStr);
				}
				catch (NumberFormatException e) { /* ignore */ }

				if (increment) {
					existedValue = existedValue.add(value);
					while (existedValue.compareTo(Memcached.UINT64_MAX) > 0) {
						existedValue = existedValue.subtract(Memcached.UINT64_MAX);
					}
				}
				else {
					existedValue = existedValue.subtract(value);
					if (existedValue.compareTo(BigInteger.ZERO) < 0) {
						existedValue = BigInteger.ZERO;
					}
				}

				byte[] newValue = null;
				try {
					newValue = existedValue.toString().getBytes(Memcached.ENCODING);
				}
				catch (UnsupportedEncodingException e) { /* NOTREACHED */ }
				if (newValue.length < existedValueSize) {
					b = new byte[existedValueSize];
					System.arraycopy(newValue, 0, b, 0, newValue.length);
					newValue = b;
				}

				values[0] = ret = new Item(newValue, v.getValue().getFlag());	// new value to be put
			}

			return ret;
		}
	}
}

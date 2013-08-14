/*
 * Copyright 2006,2010 National Institute of Advanced Industrial Science
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

package ow.messaging.emulator;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ow.messaging.MessagingConfiguration;

public final class EmuHostID implements Serializable {
	public final static String HOSTNAME_PREFIX = "emu";

	/** (Virtual) hostname: "emu<n>". */
	private String hostname;

	/** Created order. */
	private int id;
	private static int nextID = -1;
	private static boolean initialIDHasBeenSet = false;

	private static Map<Integer,EmuHostID> addrTable =
		Collections.synchronizedMap(new HashMap<Integer,EmuHostID>());

	private EmuHostID() {
		int id;
		synchronized (EmuHostID.class) {
			id = nextID++;
		}

		this.init(id);
	}

	private EmuHostID(int id) {
		this.init(id);
	}

	private void init(int id) {
		this.id = id;

		addrTable.put(this.id, this);

		// generate a (virtual) hostname
		this.hostname = HOSTNAME_PREFIX + this.id;
	}

	/**
	 * Returns the ID of this (virtual) host.
	 */
	public int getHostID() {
		return this.id;
	}

	/**
	 * Resolve the given (virtual) hostname and return an address.
	 *
	 * @return a (virtual) hostname, or null if could not be resolved.
	 */
	protected static EmuHostID resolve(String hostname) {
		EmuHostID resolved = null;

		if (!hostname.startsWith(HOSTNAME_PREFIX)) return null;

		String idStr = hostname.substring(HOSTNAME_PREFIX.length());
		int id = -1;
		try {
			id = Integer.parseInt(idStr);
		}
		catch (NumberFormatException e) {
			return null;
		}

		resolved = addrTable.get(id);
		if (resolved == null) resolved = new EmuHostID(id);

		return resolved;
	}

	/**
	 * Sets the initial ID.
	 * This method is called by {@link EmuMessagingProvider#getReceiver(MessagingConfiguration, int, int) getReceiver()}.
	 */
	public synchronized static void setInitialID(int id) {
		if (!initialIDHasBeenSet) {
			nextID = id;
			initialIDHasBeenSet = true;
		}
	}

	/**
	 * The factory method which returns an unique address.
	 */
	public static EmuHostID getNewInstance() {
		return new EmuHostID();
	}

	/**
	 * Discard this instance.
	 */
	protected void discard() {
		addrTable.remove(this.id);
	}

	/**
	 * Return a hash code value for this instance.
	 * Override java.lang.Object#hashCode().
	 */
	public int hashCode() {
		return this.id;
	}

	public boolean equals(Object o) {
		if (o instanceof EmuHostID) {
			return this.id == ((EmuHostID)o).id;
		}
		return false;
	}

	public String toString() {
		return this.hostname;
	}
}

/*
 * Copyright 2006 National Institute of Advanced Industrial Science
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

package ow.mcast;

import ow.id.ID;
import ow.mcast.impl.McastImpl;
import ow.routing.RoutingService;

public class McastFactory {
	/**
	 * Returns a default configuration.
	 */
	public static McastConfiguration getDefaultConfiguration() {
		return new McastConfiguration();
	}

	/**
	 * Returns an instance of Mcast
	 * ID of this instance is determined consistently based on the hostname.
	 */
	public static Mcast getMcast(McastConfiguration config)
			throws Exception {
		return new McastImpl(config, (ID)null);
	}

	/**
	 * Returns an instance of Mcast.
	 */
	public static Mcast getMcast(McastConfiguration config, ID selfID)
			throws Exception {
		return new McastImpl(config, selfID);
	}

	/**
	 * Returns an instance of Mcast.
	 * ID of this instance is determined consistently based on the hostname.
	 *
	 * @param applicationID ID of application embedded in a message signature to avoid cross-talk between different applications.
	 */
	public static Mcast getMcast(
			short applicationID, short applicationVersion, McastConfiguration config)
				throws Exception {
		return new McastImpl(applicationID, applicationVersion, config, (ID)null);
	}

	/**
	 * Returns an instance of Mcast.
	 *
	 * @param applicationID ID of application embedded in a message signature to avoid cross-talk between different applications.
	 */
	public static Mcast getMcast(
			short applicationID, short applicationVersion,McastConfiguration config, ID selfID)
				throws Exception {
		return new McastImpl(applicationID, applicationVersion, config, selfID);
	}

	/**
	 * Returns an instance of Mcast.
	 */
	public static Mcast getMcast(McastConfiguration config, RoutingService routingSvc)
				throws Exception {
		return new McastImpl(config, routingSvc);
	}
}

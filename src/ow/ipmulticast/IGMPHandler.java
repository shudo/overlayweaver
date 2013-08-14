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

package ow.ipmulticast;

import java.net.Inet4Address;

public interface IGMPHandler {
	/**
	 * This method processes an incoming IGMP message.
	 * Note that an argument "data" contains the entire IGMP message. 
	 */
	void process(Inet4Address src, Inet4Address dest, int type, int code, Inet4Address group,
			byte[] data,
			VirtualInterface vif /* can be null */);
}

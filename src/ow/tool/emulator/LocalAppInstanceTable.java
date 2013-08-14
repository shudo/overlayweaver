/*
 * Copyright 2006,2012 National Institute of Advanced Industrial Science
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

package ow.tool.emulator;

import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class LocalAppInstanceTable implements AppInstanceTable {
	Map<Integer,Writer> controlPipeMap = new HashMap<Integer,Writer>();
	Map<Integer,EmulatorControllable> appInstanceMap = new HashMap<Integer,EmulatorControllable>();

	public synchronized Writer getControlPipe(int hostID) {
		return this.controlPipeMap.get(hostID);
	}

	public synchronized void setAppInstance(int hostID, Writer controlPipe, EmulatorControllable appInstance) {
		if (controlPipe != null)
			this.controlPipeMap.put(hostID, controlPipe);
		if (appInstance != null)
			this.appInstanceMap.put(hostID, appInstance);
	}

	public Collection<Integer> getAllHostIDs() {
		return this.controlPipeMap.keySet();
	}

	public Collection<Writer> getAllControlPipes() {
		return this.controlPipeMap.values();
	}
}

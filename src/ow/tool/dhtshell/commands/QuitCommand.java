/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.tool.dhtshell.commands;

import ow.dht.DHT;
import ow.tool.util.shellframework.Command;
import ow.tool.util.shellframework.ShellContext;

public final class QuitCommand implements Command<DHT<String>> {
	private final static String[] NAMES = {"quit", "exit"};

	public String[] getNames() { return NAMES; }

	public String getHelp() {
		return "quit|exit";
	}

	public boolean execute(ShellContext<DHT<String>> context) {
		return true;
	}
}

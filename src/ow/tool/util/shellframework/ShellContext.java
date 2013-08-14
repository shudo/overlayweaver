/*
 * Copyright 2006,2008 National Institute of Advanced Industrial Science
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

package ow.tool.util.shellframework;

import java.io.PrintStream;
import java.util.List;

public class ShellContext<T> {
	private ShellServer<T> shellServer;
	private Shell<T> shell;
	private T opaqueData;
	private PrintStream out;
	private List<Command<T>> commandList;
	private String command;
	private String[] args;
	private boolean interactive;

	public ShellContext(ShellServer<T> shellServer, Shell<T> shell, T opaqueData,
			PrintStream out,
			List<Command<T>> commandList, String command, String[] args, boolean interactive) {
		this.shellServer = shellServer;
		this.shell = shell;
		this.opaqueData = opaqueData;
		this.out = out;
		this.commandList = commandList;
		this.command = command;
		this.args = args;
		this.interactive = interactive;
	}

	public ShellServer<T> getShellServer() { return this.shellServer; }
	public Shell<T> getShell() { return this.shell; }
	public T getOpaqueData() { return this.opaqueData; }
	public PrintStream getOutputStream() { return this.out; }
	public List<Command<T>> getCommandList() { return this.commandList; }
	public String getCommand() { return this.command; }
	public void setArguments(String[] args) { this.args = args; }
	public String[] getArguments() { return this.args; }
	public boolean isInteractive() { return this.interactive; }
}

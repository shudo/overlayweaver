/*
 * Copyright 2006-2009,2012 National Institute of Advanced Industrial Science
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

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import ow.messaging.util.AccessControlledServerSocket;
import ow.messaging.util.AccessController;

/**
 * DHT shell server. An instance of this class wraps a DHT instance.
 */
public final class ShellServer<T> implements Runnable {
	public final static String ENCODING = "US-ASCII";

	private final Map<String,Command<T>> commandTable;
	private final List<Command<T>> commandList;
	private final MessagePrinter showPromptPrinter;
	private final MessagePrinter noCommandPrinter;
	private final MessagePrinter emptyLinePrinter;

	private T appDepData;
	private int port;
	private AccessController accessController;

	private ServerSocket servSock = null;
	protected Thread servSockThread = null;

	private Set<Interruptible> interruptibleSet = new HashSet<Interruptible>();

	public static <C> List<Command<C>> createCommandList(Class<Command<C>>[] commandClasses) {
		List<Command<C>> commandList = new ArrayList<Command<C>>();

		for (Class<Command<C>> commandClz: commandClasses) {
			Command<C> cmd;
			try {
				cmd = (Command<C>)commandClz.newInstance();
			}
			catch (Exception e) {
				continue;
			}

			commandList.add(cmd);
		}

		return commandList;
	}

	public static <C> Map<String,Command<C>> createCommandTable(List<Command<C>> commandList) {
		Map<String,Command<C>> commandTable = new HashMap<String,Command<C>>();

		for (int i = commandList.size() - 1; i >= 0; i--) {
			Command<C> cmd = commandList.get(i);

			String[] names = cmd.getNames();
			for (String name: names) {
				for (int j = 1; j <= name.length(); j++) {
					String key = name.substring(0, j);
					commandTable.put(key, cmd);
				}
			}
		}

		return commandTable;
	}

	/**
	 * A constructor.
	 */
	public ShellServer(Map<String,Command<T>> commandTable, List<Command<T>> commandList,
			MessagePrinter showPromptPrinter, MessagePrinter noCommandPrinter, MessagePrinter emptyLinePrinter,
			T applicationDependentData, int port) {
		this(commandTable, commandList, showPromptPrinter, noCommandPrinter, emptyLinePrinter,
				applicationDependentData, port, null);
	}

	/**
	 * A constructor.
	 *
	 * @param applicationDependentData
	 *            in case of DHT server, a DHT instance which has been initialized.
	 * @param port
	 *            the port number on which this instance waits for incoming
	 *            connections.
	 */
	public ShellServer(Map<String,Command<T>> commandTable, List<Command<T>> commandList,
			MessagePrinter showPromptPrinter, MessagePrinter noCommandPrinter, MessagePrinter emptyLinePrinter,
			T applicationDependentData, int port,
			AccessController ac) {
		this.appDepData = applicationDependentData;
		this.port = port;
		this.accessController = ac;

		// initialize commands
		this.commandTable = commandTable;
		this.commandList = commandList;
		this.showPromptPrinter = showPromptPrinter;
		this.noCommandPrinter = noCommandPrinter;
		this.emptyLinePrinter = emptyLinePrinter;

		// network initialization
		this.startServSockThread();
	}

	private void startServSockThread() {
		if (this.port < 0 || this.port >= 65536) return;

		synchronized (this) {
			if (this.servSockThread != null) return;

			try {
				this.servSock = new AccessControlledServerSocket(this.accessController);
				this.servSock.setReuseAddress(true); // for development
				this.servSock.bind(new InetSocketAddress(port));
			}
			catch (IOException e) {
				System.err.println("An Exception thrown:");
				e.printStackTrace();

				this.servSock = null;
				return;
			}

			// invokes a thread to accept connections
			this.servSockThread = new Thread(this);
			this.servSockThread.setName("ShellServer");
			this.servSockThread.setDaemon(true);
			this.servSockThread.start();

			System.out.println("A shell server is waiting on the port tcp/" + port);
		}
	}

	public void stopServSockThread() {
		if (this.port < 0 || this.port >= 65536) return;

		synchronized (this) {
			if (this.servSockThread == null) return;

			this.servSockThread.interrupt();
			this.servSockThread = null;

			try {
				this.servSock.close();
			}
			catch (IOException e) { /* ignore */ }
			this.servSock = null;
		}
	}

	/**
	 * This ShellServer instance starts accepting incoming connections.
	 */
	public void run() {
		// accept incoming connections and invoke shells
		while (true) {
			Socket sock;
			try {
				sock = servSock.accept();
			}
			catch (IOException e) {	// servSock has been closed
				System.out.println("Halt.");
				break;
			}

			// instantiate a shell
			Shell<T> sh = null;
			try {
				sh = new Shell<T>(sock, this, this.commandTable, this.commandList,
						this.showPromptPrinter, this.noCommandPrinter, this.emptyLinePrinter,
						this.appDepData); 
			}
			catch (Exception e) {
				continue;
			}

			Thread t = new Thread(sh);
			t.setName("A Shell");
			t.setDaemon(false);
			t.start();
		}

		synchronized (this) {
			this.servSockThread = null;
			this.servSock = null;
		}
	}

	public void addInterruptible(Interruptible t) {
		synchronized (this.interruptibleSet) {
			this.interruptibleSet.add(t);
		}
	}

	public void removeInterruptible(Interruptible t) {
		synchronized (this.interruptibleSet) {
			this.interruptibleSet.remove(t);
		}
	}

	public void interruptAllInterruptible() {
		synchronized (this.interruptibleSet) {
			for (Interruptible t: this.interruptibleSet) {
				t.interrupt();
			}
		}
	}

	public Map<String, Command<T>> getCommandTable() { return this.commandTable; }

	protected List<Command<T>> getCommandList() { return this.commandList; }

	protected MessagePrinter getShowPromptPrinter() { return this.showPromptPrinter; }

	protected MessagePrinter getNoCommandPrinter() { return this.noCommandPrinter; }

	protected MessagePrinter getEmptyLinePrinter() { return this.emptyLinePrinter; }

	//
	// for ShellServer#print and println
	//

	protected static Set<PrintStream> outputStreamSet = new HashSet<PrintStream>();
	private static Queue<String> messageQueue = new LinkedList<String>();

	/**
	 * Broadcast a message to all shells.
	 */
	public static void print(String str) {
		startPrintServer();

		synchronized (messageQueue) {
			messageQueue.offer(str);
			messageQueue.notify();
		}
	}

	/**
	 * Broadcast a message to all shells.
	 */
	public static void println(String str) {
		print(str + "\n");
	}

	private static Thread printServer = null;

	private static void startPrintServer() {
		if (printServer != null) return;	// just a performance optimization

		synchronized (ShellServer.class) {
			if (printServer == null) {
				// start a PrintServer
				Runnable r = new PrintServer();
				Thread t = new Thread(r);
				t.setName("PrintServer");
				t.setDaemon(true);
				t.start();

				printServer = t;
			}
		}
	}

	private static void stopPrintServer() {
		synchronized (ShellServer.class) {
			if (printServer != null) {
				printServer.interrupt();

				printServer = null;
			}
		}
	}

	private static class PrintServer implements Runnable {
		public void run() {
			while (true) {
				StringBuilder sb = new StringBuilder();

				// dequeue messages
				synchronized (messageQueue) {
					while (true) {
						String msg = messageQueue.poll();
						if (msg != null) {
							sb.append(msg);
							continue;
						}
						else {
							if (sb.length() > 0) break;
						}

						try {
							messageQueue.wait();
						}
						catch (InterruptedException e) {
							// interrupted and die
							return;
						}
					}
				}

				// write out messages
				String message = sb.toString();
				boolean wroteToStdout = false;

				//synchronized (outputStreamSet) {
					for (PrintStream out: outputStreamSet) {
						if (System.out.equals(out)) wroteToStdout = true;

						out.print(message);
					}
				//}

				if (!wroteToStdout) {	// always write out the message to stdout
					System.out.print(message);
				}
			}	// while (true)
		}
	}

	//
	// suspend and resume
	//

	public void suspend() {
		this.startServSockThread();
		ShellServer.stopPrintServer();
	}

	public void resume() {
		this.startServSockThread();
		ShellServer.startPrintServer();
	}
}

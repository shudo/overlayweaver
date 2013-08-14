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

package ow.tool.emulator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import ow.messaging.util.MessagingUtility;
import ow.messaging.util.MessagingUtility.HostAndPort;

public class RemoteAppInstanceTable implements AppInstanceTable {
	private SortedSet<WorkerEntry> entrySet = new TreeSet<WorkerEntry>();
	private Map<InetAddress,WorkerEntry> entryMap = new HashMap<InetAddress,WorkerEntry>();
	private Map<InetAddress,Writer> pipeMap = new HashMap<InetAddress,Writer>();
	private boolean pipeEstablished = false;

	private OutputRedirector outputRedirector;

	private RemoteAppInstanceTable() {
		this.outputRedirector = new OutputRedirector(System.out);
	}

	/**
	 * Generates an instance based on the specified file.
	 */
	public static RemoteAppInstanceTable readHostFile(String filename) throws IOException {
		RemoteAppInstanceTable table = new RemoteAppInstanceTable();

		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(
					new FileInputStream(filename), Main.ENCODING));
		}
		catch (UnsupportedEncodingException e) {
			// NOTREACHED
		}

		String line;
		while (true) {
			line = in.readLine();
			if (line == null) break;		// EOF
			if (line.length() <= 0) continue;	// skip blank lines

			if (line.startsWith("#")
					|| line.startsWith(";")
					|| line.startsWith("//"))
				continue;	// comments

			String[] splitted = line.split("\\s+");
			HostAndPort hostPort = MessagingUtility.parseHostnameAndPort(splitted[0], Main.DIST_EMU_PORT);
			int startHostID = Integer.parseInt(splitted[1]);

			WorkerEntry entry = new WorkerEntry(hostPort, startHostID);
			table.entrySet.add(entry);
			table.entryMap.put(hostPort.getHostAddress(), entry);
		}

		return table;
	}

	/**
	 * Generates an instance based on the specified String.
	 */
	public static RemoteAppInstanceTable parseString(String str) throws UnknownHostException {
		RemoteAppInstanceTable table = new RemoteAppInstanceTable();

		String[] splitted = str.split("\\s*,\\s*");

		try {
			int index = 0;
			while (true) {
				HostAndPort hostPort = MessagingUtility.parseHostnameAndPort(splitted[index], Main.DIST_EMU_PORT);
				int startHostID = Integer.parseInt(splitted[index + 1]);

				WorkerEntry entry = new WorkerEntry(hostPort, startHostID);
				table.entrySet.add(entry);
				table.entryMap.put(hostPort.getHostAddress(), entry);

				index += 2;
			}
		}
		catch (ArrayIndexOutOfBoundsException e) {
			// ignore
		}

		return table;
	}

	/**
	 * Generates a String representation of this instance.
	 * This String can be parsed by
	 * {@link RemoteAppInstanceTable#parseString(String) parseString} method.
	 */
	public String getStringRepresentation() {	// generates an argument for -w option
		StringBuilder sb = new StringBuilder();
		boolean firstEntry = true;

		for (WorkerEntry w: this.entrySet) {
			if (firstEntry) {
				firstEntry = false;
			}
			else {
				sb.append(",");
			}

			sb.append(w.getHostAndPort());
			sb.append(",");
			sb.append(w.getStartHostID());
		}

		return sb.toString();
	}

	private WorkerEntry searchKey = new WorkerEntry(null, 0);

	public synchronized HostAndPort getWorkerHostAndPort(int hostID) {
		this.searchKey.startHostID = hostID + 1;
		SortedSet<WorkerEntry> headSet = this.entrySet.headSet(this.searchKey);

		if (headSet == null) {
			return null;
		}

		HostAndPort addr = null;
		try {
			WorkerEntry target = headSet.last();
			addr = target.getHostAndPort();
		}
		catch (NoSuchElementException e) {
			addr = null;
		}

		return addr;
	}

	protected InetAddress getWorkerHostAddress(int hostID) {
		InetAddress ret = null;

		HostAndPort hostPort = this.getWorkerHostAndPort(hostID);
		if (hostPort != null) {
			try {
				ret = hostPort.getHostAddress();
			}
			catch (UnknownHostException e) {
				System.err.println("Could not resolve a hostname: " + hostPort.getHostName());
			}
		}
		else {
			ret = null;
		}

		return ret;
	}

	public Writer getControlPipe(int hostID) {
		InetAddress hostAddress = this.getWorkerHostAddress(hostID);

		Writer pipe = null;
		if (hostAddress != null) {
			synchronized (this.pipeMap) {
				pipe = this.pipeMap.get(hostAddress);
			}
		}

		return pipe;
	}

	public void setAppInstance(int hostID, Writer out, EmulatorControllable appInstance) {
		// do nothing
	}

	public Collection<Integer> getAllHostIDs() {
		// do nothing
		return null;
	}

	public Collection<Writer> getAllControlPipes() {
		synchronized (this.pipeMap) {
			return this.pipeMap.values();
		}
	}

	public int getStartHostID(InetAddress hostAddress) throws NoSuchElementException {
		WorkerEntry entry = null;
		synchronized (this.entryMap) {
			entry = this.entryMap.get(hostAddress);
		}

		if (entry == null) {
			throw new NoSuchElementException("There is no entry: " + hostAddress.getHostName());
		}

		return entry.getStartHostID();
	}

	public boolean isPipeEstablished() { return this.pipeEstablished; }

	public Set<Thread> establishControlPipes(
			String dir, String javaPath, String jvmOption, PrintStream out) {
		if (this.pipeEstablished) return null;

		// prepare arguments
		StringBuilder sb = new StringBuilder();

		if (dir != null) {
			sb.append("cd ");
			sb.append(dir);
			sb.append(" && ");
		}

		sb.append("exec ");

		if (true) {
			if (javaPath != null) {
				sb.append(javaPath);
				if (!javaPath.endsWith(File.separator)) {
					sb.append(File.separator);
				}
			}
		}
		else {
			if (javaPath != null) {
				sb.append("env PATH=");
				sb.append(javaPath);
				sb.append(" ");
			}
		}
		sb.append(Main.JAVA_COMMAND);

		if (jvmOption != null) {
			sb.append(" ");
			sb.append(jvmOption);
			
		}

		sb.append(" ");
		sb.append(Main.class.getName());
		sb.append(" -w ");
		sb.append(this.getStringRepresentation());

		String remoteCommand = sb.toString();

		Set<Thread> threadSet = new HashSet<Thread>();
		for (WorkerEntry w: this.entrySet) {
			InetAddress hostAddress = null;
			try {
				hostAddress = w.getHostAndPort().getHostAddress();
			}
			catch (UnknownHostException e) {
				System.err.println("Could not resolve a hostname: " + w.getHostAndPort().getHostName());
				System.exit(1);
			}

			if (this.pipeMap.get(hostAddress) != null) continue;

			// remote invocation
			out.println("execute: " + Main.RSH_COMMAND + " " + hostAddress.getHostName()
					+ " " + remoteCommand);

			ProcessBuilder pb = new ProcessBuilder(Main.RSH_COMMAND, hostAddress.getHostName(), remoteCommand);
			pb.redirectErrorStream(true);
			Process proc = null;
			try {
				proc = pb.start();
			}
			catch (IOException e) {
				System.err.println("Failed to start a remote shell:");
				e.printStackTrace(System.err);
				System.exit(1);
			}

			// input and output stream
			Thread t =
				this.outputRedirector.redirect(proc.getInputStream(),
						"Redirector for " + hostAddress.getHostAddress());
			threadSet.add(t);

			BufferedWriter controlPipe = null;
			try {
				controlPipe = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream(), Main.ENCODING));
			}
			catch (UnsupportedEncodingException e) {
				// NOTREACHED
			}

			this.pipeMap.put(hostAddress, controlPipe);
		}

		this.pipeEstablished = true;

		return threadSet;
	}

	private static class WorkerEntry implements Comparable<WorkerEntry> {
		private final HostAndPort hostPort;
		private int startHostID;

		WorkerEntry(HostAndPort hostPort, int startHostID) {
			this.hostPort = hostPort;
			this.startHostID = startHostID;
		}

		public HostAndPort getHostAndPort() { return this.hostPort; }
		public int getStartHostID() { return this.startHostID; }

		public int compareTo(WorkerEntry o) {
			return Integer.signum(this.startHostID - o.startHostID);
		}

		public int hashCode() {
			return this.hostPort.hashCode() ^ this.startHostID;
		}

		public boolean equals(Object o) {
			if (o instanceof WorkerEntry) {
				WorkerEntry w = (WorkerEntry)o;
				if (this.startHostID == w.startHostID
						&& this.hostPort.equals(w.hostPort)) {
					return true;
				}
			}

			return false;
		}
	}
}

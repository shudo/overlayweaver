/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Shell<T> implements Runnable, Interruptible {
	public final static String CRLF = "\r\n";

	private Socket sock;
	private final ShellServer<T> shellServer;	// this field is only for SourceCommand.java
	private BufferedInputStream in;
	private PrintStream out;

	private Thread selfThread = null;

	private final Map<String, Command<T>> commandTable;
	private final List<Command<T>> commandList;
	private final MessagePrinter showPromptPrinter;
	private final MessagePrinter noCommandPrinter;
	private final MessagePrinter emptyLinePrinter;
	private final T appDepData;

	private boolean interactive;

	protected Shell(Socket sock, ShellServer<T> shellServer,
			Map<String, Command<T>> commandTable, List<Command<T>> commandList,
			MessagePrinter showPromptPrinter, MessagePrinter noCommandPrinter, MessagePrinter emptyLinePrinter,
			T applicationDependentData)
				throws Exception {
		this.sock = sock;
		this.shellServer = shellServer;
		InputStream sockInStream = sock.getInputStream();
		this.in = new BufferedInputStream(sockInStream);
		this.out = new PrintStream(sock.getOutputStream(),
				false /* autoFlush */);

		this.commandTable = commandTable;
		this.commandList = commandList;
		this.showPromptPrinter = showPromptPrinter;
		this.noCommandPrinter = noCommandPrinter;
		this.emptyLinePrinter = emptyLinePrinter;
		this.appDepData = applicationDependentData;

		this.interactive = true;
	}

	public Shell(InputStream in, PrintStream out,
			Map<String,Command<T>> commandTable, List<Command<T>> commandList,
			MessagePrinter showPromptPrinter, MessagePrinter noCommandPrinter, MessagePrinter emptyLinePrinter,
			T applicationDependentData,
			boolean interactive) {
		this(in, out,
				new ShellServer<T>(commandTable, commandList,
						showPromptPrinter, noCommandPrinter, emptyLinePrinter, applicationDependentData, -1),
						applicationDependentData, interactive);
	}

	public Shell(InputStream in, PrintStream out,
			ShellServer<T> shellServer,
			T applicationDependentData,
			boolean interactive) {
		this.sock = null;
		this.shellServer = shellServer;
		if (in != null) {
			this.in = (in instanceof BufferedInputStream ?
					(BufferedInputStream)in : new BufferedInputStream(in));	// read only in run()
		}
		this.out = out;

		this.commandTable = this.shellServer.getCommandTable();
		this.commandList = this.shellServer.getCommandList();
		this.showPromptPrinter = this.shellServer.getShowPromptPrinter();
		this.noCommandPrinter = this.shellServer.getNoCommandPrinter();
		this.emptyLinePrinter = this.shellServer.getEmptyLinePrinter();
		this.appDepData = applicationDependentData;

		this.interactive = interactive;
	}

	public void run() {
		this.selfThread = Thread.currentThread();

		if (shellServer != null) {
			Set<PrintStream> newSet = new HashSet<PrintStream>();
			newSet.addAll(ShellServer.outputStreamSet);
			newSet.add(this.out);
			synchronized (ShellServer.class) {	// to show this change to other threads
				ShellServer.outputStreamSet = newSet;
			}

			shellServer.addInterruptible(this);
		}

		while (true) {
			if (this.interactive && this.showPromptPrinter != null) {
				this.showPromptPrinter.execute(this.out, null);
			}

			String commandLine = null;
			try {
				commandLine = this.readLineString();
			}
			catch (IOException e) {
				System.err.print("An Exception thrown when reading from a network." + CRLF);
				e.printStackTrace(System.err);
			}

			boolean quit = this.parseALine(commandLine);

			if (quit) {
				break;
			}
		}

		if (shellServer != null) {
			Set<PrintStream> newSet = new HashSet<PrintStream>();
			newSet.addAll(ShellServer.outputStreamSet);
			newSet.remove(this.out);
			synchronized (ShellServer.class) {	// to show this change to other threads
				ShellServer.outputStreamSet = newSet;
			}

			shellServer.removeInterruptible(this);
		}

		// close streams
		if (this.sock != null) {
			int retry = 3;
			while (retry-- > 0) {
				try {
					this.sock.close();
					this.sock = null;
					break;
				}
				catch (IOException e) {
					System.err.print("SocketChannel#close() threw an IOException." + CRLF);
				}
			}
		}

		try {
			this.in.close();
		}
		catch (IOException e) {
			System.err.print("close() threw an IOException." + CRLF);
		}
	}

	private int bufSize = 8;	// initial buffer size
	private byte[] buf = new byte[bufSize];
	private synchronized String readLineString() throws IOException {
		int len = this.readLineBytes(0, false);
		if (len == -1) return null;	// EOF
		return new String(buf, 0, len, ShellServer.ENCODING);
	}
	private int readLineBytes(int minLen, boolean recognizeEscape) throws IOException {
		int index = 0;

		loop: while (true) {
//if (this.in == null) { System.out.print("[in is null !]"); System.out.flush(); }
			int b = this.in.read();
//System.out.println("[" + b + "]");

			if (b == -1) {	// EOF
				if (index == 0) index = -1;
				break loop;
			}

			if (index >= minLen) {
				// recognize LF, CR, CR LF, CR <NUL> as a line terminator
				if (b == 0x0a) {		// LF
					break loop;
				}
				else if (b == 0x0d) {	// CR
					this.in.mark(1);
					int next = this.in.read();
					if (next != 0x0a && next != 0) {	// next != LF && next != <NUL>
						this.in.reset();
					}
					break loop;
				}
				else if (recognizeEscape && b == 0x5c) {	// \
					int next = this.in.read();
					switch (next) {
					case 0x62:	// \b -> BS
						b = 0x08; break;
					case 0x74:	// \t -> TAB
						b = 0x09; break;
					case 0x6e:	// \n -> LF
						b = 0x0a; break;
					case 0x66:	// \f -> FF
						b = 0x0c; break;
//					case 0x22:	// \" -> "
//						b = 0x22; break;
//					case 0x27:	// \' -> '
//						b = 0x27; break;
					case 0x5c:	// \\ -> \
						b = 0x5c; break;
					case 0x72:	// \r -> CR
						b = 0x0d; break;
					default:
						//b = 0x5c;
						break;
					}
				}
			}

			if (index >= bufSize) {
				// extend buffer
				byte[] newBuf = null;
				try {
					newBuf = new byte[bufSize << 1];
				}
				catch (OutOfMemoryError e) {	// for debug
					System.out.println(new String(buf, 0, 16, ShellServer.ENCODING));
					throw e;
				}
				System.arraycopy(buf, 0, newBuf, 0, bufSize);
				bufSize <<= 1;
				buf = newBuf;
			}

			buf[index++] = (byte)b;
		}	// loop: while (true)

		return index;
	}
	public synchronized byte[] readLine(int minLen) throws IOException {
		int len = this.readLineBytes(minLen, false);

		if (len == -1) return null;	// EOF

		byte[] ret = new byte[len];
		System.arraycopy(buf, 0, ret, 0, len);
		return ret;
	}

	private boolean parseALine(String commandLine) {
		if (commandLine == null)	// EOF
			return true;

		// remove a NUL at the head of command line
		// a correct telnet client send <CR><NUL> as the line terminating sequence
		// and the <NUL> remains at the head of the next command line. 
		if (commandLine.length() > 0 && commandLine.charAt(0) == '\0') {
			commandLine = commandLine.substring(1);
		}

		// split
		String[] tokens = commandLine.split("\\s+");

		// skip null tokens
		int k = 0;
		for (; k < tokens.length; k++) {
			if (tokens[k].length() > 0)
				break;
		}
		if (k >= tokens.length) {
			// all token are null
			if (this.emptyLinePrinter != null) {
				this.emptyLinePrinter.execute(this.out, null);
			}

			return false;
		}

		String cmdToken = tokens[k];

		// skip a comment line
		if (cmdToken.startsWith("#") || cmdToken.startsWith("%") || cmdToken.startsWith("//")) {
			return false;
		}

		Command<T> command = this.commandTable.get(cmdToken);
		if (command == null) {
			if (this.noCommandPrinter != null) {
				this.noCommandPrinter.execute(this.out, cmdToken);
			}

			return false;
		}

		k++;
		String[] args = new String[tokens.length - k];
		for (int i = 0; i < args.length; i++, k++)
			args[i] = tokens[k];

		// execute
		ShellContext<T> context = new ShellContext<T>(
				this.shellServer, this, this.appDepData, this.out,
				this.commandList, cmdToken, args,
				this.interactive);
		boolean quit = false;
		try {
			quit = command.execute(context);
		}
		catch (Throwable e) {
			this.out.print("An Exception thrown: " + CRLF);
			e.printStackTrace(this.out);
			this.out.flush();
		}

		return quit;
	}

	public void interrupt() {
		if (this.selfThread != null && !this.selfThread.equals(Thread.currentThread()))
			this.selfThread.interrupt();
	}

	public Writer getWriter() { return new ShellWriter(); }

	public class ShellWriter extends Writer {
		public void close() throws IOException { /* ignore */ }

		public void flush() throws IOException { /* ignore */ }

		public void write(char[] cbuf, int off, int len) throws IOException {
			String str = new String(cbuf, off, len);
			Shell.this.parseALine(str);
		}
	}
}

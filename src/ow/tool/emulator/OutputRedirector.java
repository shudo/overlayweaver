/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public final class OutputRedirector implements Runnable {
	BufferedReader in;
	PrintStream out;

	public OutputRedirector(PrintStream out) {
		this.out = out;
	}

	public Thread redirect(InputStream in, String threadName) {
		Runnable r = new OutputRedirector(in, this.out);
		Thread t = new Thread(r);
		t.setName(threadName);
		t.setDaemon(true);
		t.start();

		return t;
	}

	private OutputRedirector(InputStream in, PrintStream out) {
		try {
			this.in = new BufferedReader(new InputStreamReader(in, Main.ENCODING));
		}
		catch (UnsupportedEncodingException e) {
			// NOTREACHED
		}
		this.out = out;
	}

	public void run() {
		try {
			while (true) {
				String line = this.in.readLine();
				if (line == null) break;
				this.out.println(line);
			}
		}
		catch (IOException e) { /* ignore */ }
	}
}

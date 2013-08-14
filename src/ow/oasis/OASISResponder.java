/*
 * Copyright 2008 Kazuyuki Shudo, and contributors.
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

package ow.oasis;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import ow.messaging.util.AccessControlledServerSocket;
import ow.messaging.util.AccessController;

/**
 * A server, which responds to queries from OASIS (http://oasis.coralcdn.org/) replicas.
 */
public final class OASISResponder implements Runnable {
	public final static byte[] SERVICE_ID = { 0x4f, 0x57, 0x56, 0x52 };	// "OWVR"
	public final static int LOAD = 1;
	public final static int CAPACITY = 10;
	private final static byte[] RESPONSE;

	public final static boolean DO_ACCESS_CONTROL = false;
	public final static String ACCESS_CONTROL_LIST =
		"allow 127.0.0.1\n" +
		"deny\n";
	public final static AccessController ac;

	static {
		// prepare the response
		RESPONSE = new byte[12];
		System.arraycopy(SERVICE_ID, 0, RESPONSE, 0, SERVICE_ID.length);
		for (int i = 0; i < 4; i++) {
			RESPONSE[4 + i] = (byte)(LOAD >>> ((3 - i) << 3));
			RESPONSE[8 + i] = (byte)(CAPACITY >>> ((3 - i) << 3));
		}

		// prepare AccessController
		if (DO_ACCESS_CONTROL) {
			try {
				ac = new AccessController(ACCESS_CONTROL_LIST.toCharArray());
			}
			catch (IOException e) { /*NOTREACHED*/ }
		}
		else {
			ac = null;
		}
	}

	private final ServerSocket servSock;

	public OASISResponder(int port) throws IOException {
		this.servSock = new AccessControlledServerSocket(ac, port);
	}

	public void run() {
		try {
			while (true) {
				Socket sock;
				sock = this.servSock.accept();

				OutputStream out = sock.getOutputStream();
				out.write(RESPONSE);
				out.flush();

				try {
					Thread.sleep(1000L);
				}
				catch (InterruptedException e) { /*ignore*/ }

				sock.close();
			}
		}
		catch (IOException e) {
			try {
				if (this.servSock != null) this.servSock.close();
			}
			catch (IOException ex) { /*ignore*/ }
		}
	}

	// for testing
	public static void main(String[] args) throws IOException {
		OASISResponder s = new OASISResponder(10000);

		Thread t = new Thread(s);
		t.start();
	}
}

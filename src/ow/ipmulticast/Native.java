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

import java.io.IOException;

/**
 * An utility class holding native methods.
 */
class Native {
	private final static String NATIVE_LIB = "owmcast";
	private static boolean libraryLoaded = false;

	public synchronized static void initialize() throws IOException {
		if (!libraryLoaded) {
			System.loadLibrary(NATIVE_LIB);
			libraryLoaded = true;
		}

		initNative();
	}

	public synchronized static void stop() {
		stopNative();
	}

	//
	// native methods
	//

	static native void initNative() throws IOException;
	static native void stopNative();

	// for VirutalInterface class
	static native int numberOfVIFs();
	static native void fillVIFs(VirtualInterface.NativeVIF[] vifs);

	// for IGMP class
	static native void receiveIGMP(IGMP.IGMPMessage container) throws IOException, InterruptedException;
	static native void sendIGMP(int src, int dest, int type, int code, int group, byte[] data);

	// for IPMulticast class
	static native void receiveMulticast(IPMulticast.MulticastMessage container) throws IOException, InterruptedException;
	static native void sendMulticast(int srcaddr, int srcport, int destaddr, int destport, int id, int ttl, int datalen, byte[] data);

	static native void joinGroup(int group, int ifLocalAddr, int ifIndex);
	static native void leaveGroup(int group, int ifLocalAddr, int ifIndex);
}

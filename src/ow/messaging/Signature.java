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

package ow.messaging;

/**
 * A class to calculate a signature of messages.
 */
public final class Signature {
	// public constants
	public final static byte APPLICATION_ID_DHT_SHELL = 1;
	public final static byte APPLICATION_ID_GROUP_MANAGER_SHELL = 2;
	public final static byte APPLICATION_ID_ALM_ROUTER = 3;
	public final static byte APPLICATION_ID_MEMCACHED = 4;

	// private constants
	private final static byte[] SIGNATURE = { 0x4f, 0x57 };	// "OW"
	private final static int SIGNATURE_LENGTH = 8;

	/**
	 * Returns the length of a signature.
	 */
	public static int getSignatureLength() {
		return SIGNATURE_LENGTH;
	}

	/**
	 * Calculates a signature based on a routing style ID, routing algorithm ID,
	 * application ID and application version.
	 *
	 * @param routingStyleID an ID returned by {@link ow.routing.RoutingServiceFactory#getRoutingStyleID(String) getRoutingStyleID()}.
	 * @param routingAlgorithmID and ID returned by {@link ow.routing.RoutingAlgorithmFactory#getAlgorithmID(String) getAlgorithmID()}.
	 * @param applicationID an arbitrary ID determined by an application developer.
	 * @param applicationVersion an arbitrary version number determined by an application developer.
	 * @return an signature to be inserted to the head of messages.
	 */
	public static byte[] getSignature(byte routingStyleID, byte routingAlgorithmID, short applicationID, short applicationVersion) {
		byte[] sig = getAllAcceptingSignature();

		sig[2] = routingStyleID;
		sig[3] = routingAlgorithmID;

		sig[4] = (byte)(applicationID >>> 8);
		sig[5] = (byte)applicationID;

		sig[6] = (byte)(applicationVersion >>> 8);
		sig[7] = (byte)applicationVersion;

		return sig;
	}

	/**
	 * Returns a signature to be set to a messaging provider which accepts all incoming messages.
	 */
	public static byte[] getAllAcceptingSignature() {
		byte[] sig = new byte[SIGNATURE_LENGTH];

		sig[0] = SIGNATURE[0];
		sig[1] = SIGNATURE[1];

		return sig;
	}

	public static short getAllAcceptingApplicationID() { return 0; }
	public static short getAllAcceptingApplicationVersion() { return 0; }

	public static boolean match(byte[] signature, byte[] acceptableSignature) {
		int len = Signature.getSignatureLength();

		exactMatching:
		{
			for (int i = 0; i < len; i++) {
				if (signature[i] != acceptableSignature[i])
					break exactMatching;
			}
			return true;
		}

		receiverIsAllmighty:
		{
			for (int i = 3; i < len; i++) {
				if (acceptableSignature[i] != 0)
					break receiverIsAllmighty;
			}
			return true;
		}

		messageIsAllmighty:
		{
			for (int i = 3; i < len; i++) {
				if (signature[i] != 0)
					break messageIsAllmighty;
			}
			return true;
		}

		return false;
	}

	public static String toString(byte[] signature) {
		StringBuilder sb = new StringBuilder();

		sb.append("{");
		if (signature != null) {
			int i = 0;
			sb.append(Integer.toHexString(signature[i]));
			for (i = 1; i < signature.length; i++) {
				sb.append(",");
				sb.append(Integer.toHexString(signature[i]));
			}
		}
		else {
			sb.append("null");
		}
		sb.append("}");

		return sb.toString();
	}
}

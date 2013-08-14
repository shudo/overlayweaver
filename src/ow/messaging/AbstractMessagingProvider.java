/*
 * Copyright 2006,2009 National Institute of Advanced Industrial Science
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


public abstract class AbstractMessagingProvider implements MessagingProvider {
	//
	// Message signature
	//

	private byte[] signature = {};

	public byte[] setMessageSignature(byte[] signature) {
		byte[] old = this.signature;
		this.signature = signature;
		return old;
	}

	public byte[] getMessageSignature() { return this.signature; }
}

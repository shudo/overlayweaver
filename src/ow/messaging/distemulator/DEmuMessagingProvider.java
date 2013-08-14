/*
 * Copyright 2006-2007,2009,2011 National Institute of Advanced Industrial Science
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

package ow.messaging.distemulator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchProviderException;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.messaging.AbstractMessagingProvider;
import ow.messaging.MessageReceiver;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingConfiguration;
import ow.messaging.MessagingFactory;
import ow.messaging.MessagingProvider;
import ow.messaging.emulator.EmuMessageReceiver;
import ow.messaging.emulator.EmuMessagingProvider;

public class DEmuMessagingProvider extends AbstractMessagingProvider {
	private final static Logger logger = Logger.getLogger("messaging");

	private final static String NAME = "DistributedEmulator";

	EmuMessagingProvider emuProvider;
	MessagingProvider netProvider;
	MessageReceiver netReceiver = null;

	byte[] messageSignature = null;
	String selfAddress = null;
	InetAddress selfInetAddress;

	private MessagingAddress statCollectorAddress = null;

	public DEmuMessagingProvider() {
		// obtain provider for emulator
		try {
			this.emuProvider = (EmuMessagingProvider)MessagingFactory.getProvider(
					MessagingFactory.EMULATOR_PROVIDER_NAME, null, true);
		}
		catch (NoSuchProviderException e) {
			// NOTREACHED
		}
	}

	public String getName() { return NAME; }
	public boolean isReliable() { return false; }

	public MessagingConfiguration getDefaultConfiguration() {
		DEmuMessagingConfiguration config = new DEmuMessagingConfiguration();

		config.setInitialID(MessagingFactory.INITIAL_EMULATOR_HOST_ID);
		config.setHostTable(MessagingFactory.HOST_TABLE_FOR_DIST_EMULATOR);

		return config;
	}

	public MessageReceiver getReceiver(MessagingConfiguration config, int port, int portRange)
			throws IOException {
		DEmuMessagingConfiguration demuConf = (DEmuMessagingConfiguration)config;

		// obtain MessageReceiver for network
		synchronized (this) {
			if (this.netReceiver == null) {
				String type = demuConf.getRemoteMessagingTransport();
				try {
					this.netProvider = MessagingFactory.getProvider(type, this.getMessageSignature(), true);
				}
				catch (NoSuchProviderException e) {
					logger.log(Level.SEVERE, "No such messaging provider: " + type, e);
					return null;
				}

				// set default self address and message signature
				if (this.selfAddress != null) {
					this.netProvider.setSelfAddress(this.selfAddress);
				}
				if (this.messageSignature != null) {
					this.netProvider.setMessageSignature(this.messageSignature);
				}

				// obtain receiver
				MessagingConfiguration netConfig = this.netProvider.getDefaultConfiguration();
				this.netReceiver = this.netProvider.getReceiver(
						netConfig, demuConf.getNetPort(), demuConf.getNetPortRange());
			}
		}

		// obtain MessageReceiver for emulator
		MessagingConfiguration emuConfig = this.emuProvider.getDefaultConfiguration();
		EmuMessageReceiver emuReceiver =
			(EmuMessageReceiver)this.emuProvider.getReceiver(
					emuConfig, demuConf.getNetPort(), demuConf.getNetPortRange());

		return new DEmuMessageReceiver(this.selfInetAddress,
				this,
				emuReceiver, this.netReceiver, this.netProvider, demuConf.getHostTable());
	}

	public MessagingAddress getMessagingAddress(String hostAndPort, int defaultPort)
			throws UnknownHostException {
		return this.emuProvider.getMessagingAddress(hostAndPort, defaultPort);
	}

	public MessagingAddress getMessagingAddress(String hostAndPort)
			throws UnknownHostException {
		return this.emuProvider.getMessagingAddress(hostAndPort);
	}

	public MessagingAddress getMessagingAddress(int port) {
		return this.emuProvider.getMessagingAddress(port);
	}

	public byte[] setMessageSignature(byte[] signature) {
		this.messageSignature = signature;

		if (this.netProvider != null) {
			this.netProvider.setMessageSignature(signature);
		}

		return this.emuProvider.setMessageSignature(signature);
	}

	public byte[] getMessageSignature() {
		return this.emuProvider.getMessageSignature();
	}

	public MessagingAddress getMessagingCollectorAddress() { return this.statCollectorAddress; }
	public MessagingAddress setMessagingCollectorAddress(MessagingAddress addr) {
		MessagingAddress old;

		synchronized (this) {
			old = this.statCollectorAddress;
			this.statCollectorAddress = addr;
		}

		return old;
	}

	public MessagingProvider substitute() {
		MessagingProvider newProvider = new DEmuMessagingProvider();

		return newProvider;
	}

	public void setSelfAddress(String host) throws UnknownHostException {
		this.selfAddress = host;
		this.selfInetAddress = InetAddress.getLocalHost();

		if (this.netProvider != null) {
			this.netProvider.setSelfAddress(host);
		}

		this.emuProvider.setSelfAddress(host);
	}
}

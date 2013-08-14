/*
 * Copyright 2007,2009-2010 Kazuyuki Shudo, and contributors.
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

package ow.messaging.upnp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.cybergarage.upnp.Action;
import org.cybergarage.upnp.Argument;
import org.cybergarage.upnp.ControlPoint;
import org.cybergarage.upnp.Device;
import org.cybergarage.upnp.Service;
import org.cybergarage.upnp.UPnP;
import org.cybergarage.upnp.device.DeviceChangeListener;
import org.cybergarage.xml.parser.JaxpParser;

/**
 * An utility to establish address and port mappings on an Internet router
 * with UPnP protocol.
 */
public final class UPnPManager {
	private final static Logger logger = Logger.getLogger("messaging");

	private final static String ROUTER_DEV =
		"urn:schemas-upnp-org:device:InternetGatewayDevice:1";
	private final static String WAN_DEV =
		"urn:schemas-upnp-org:device:WANDevice:1";
	private final static String WANCON_DEV =
		"urn:schemas-upnp-org:device:WANConnectionDevice:1";
	private final static String WANIPCON_SERV =
		"urn:schemas-upnp-org:service:WANIPConnection:1";
	private final static String WANPPPCON_SERV =
		"urn:schemas-upnp-org:service:WANPPPConnection:1";

	private static UPnPManager singletonInstance = new UPnPManager();

	public static UPnPManager getInstance() { return UPnPManager.singletonInstance; }

	private ControlPoint cp;
	private Device dev;
	private Service serv;
	private final Set<Mapping> registeredMappings = new HashSet<Mapping>();

	static {
		UPnP.setXMLParser(new JaxpParser());
	}

	private UPnPManager() {
		this.cp = new ControlPoint();
	}

	public boolean start() {
		this.cp.addDeviceChangeListener(new DevChgListener());
		boolean ret = this.cp.start();

		logger.log(Level.INFO, "UPnP manager started.");

		return ret;
	}

	public void stop() {
		this.cp.stop();
	}

	public void waitForDeviceFound() {
		this.waitForDeviceFound(Long.MAX_VALUE);
	}

	public boolean waitForDeviceFound(long timeout) {
		synchronized (this) {
			if (this.deviceFound()) return true;

			try {
				this.wait(timeout);
			}
			catch (InterruptedException e) { /*ignore*/ }
		}

		return this.deviceFound();
	}

	private boolean deviceFound() {
		return (UPnPManager.this.dev != null && UPnPManager.this.serv != null);
	}

	private class DevChgListener implements DeviceChangeListener {
		public void deviceAdded(Device dev) {
			if (UPnPManager.this.deviceFound()) {
				// no NAT device has been found
				return;
			}

			if (!(dev.getDeviceType().equals(UPnPManager.ROUTER_DEV) && dev.isRootDevice())) {
				return;
			}

			UPnPManager.this.dev = dev;

			for (Object o1: dev.getDeviceList()) {
				Device d1 = (Device)o1;

				if (!d1.getDeviceType().equals(UPnPManager.WAN_DEV))
					continue;

				for (Object o2: d1.getDeviceList()) {
					Device d2 = (Device)o2;

					if (!d2.getDeviceType().equals(UPnPManager.WANCON_DEV))
						continue;

					UPnPManager.this.serv = d2.getService(UPnPManager.WANIPCON_SERV);
					if (UPnPManager.this.serv == null)
						UPnPManager.this.serv = d2.getService(UPnPManager.WANPPPCON_SERV);

					logger.log(Level.INFO, "UPnP device found: " + dev.getFriendlyName());

					// notify
					synchronized (UPnPManager.this) {
						UPnPManager.this.notifyAll();
					}
				}
			}
		}

		public void deviceRemoved(Device dev) { /* ignore */ }
	}

	public InetAddress getExternalAddress() {
		if (!this.deviceFound()) return null;

		Action a = this.serv.getAction("GetExternalIPAddress");
		if (a == null) return null;

		if (!a.postControlAction()) {
			return null;
		}

		Argument ipAddrArg = a.getOutputArgumentList().getArgument("NewExternalIPAddress");

		InetAddress ipAddr = null;
		try {
			ipAddr = InetAddress.getByName(ipAddrArg.getValue());
		} catch (UnknownHostException e) {
			// NOTREACHED
			return null;
		}

		return ipAddr;
	}

	/**
	 * Adds a port mapping.
	 *
	 * @param protocol "TCP" or "UDP"
	 * @param description optional
	 * @return true if succeed.
	 */
	public boolean addMapping(Mapping map) {
		if (!this.deviceFound()) return false;

		Action a = this.serv.getAction("AddPortMapping");
		if (a == null) return false;

		a.setArgumentValue("NewRemoteHost", "");
		a.setArgumentValue("NewExternalPort", map.getExternalPort());
		a.setArgumentValue("NewInternalClient", map.getInternalAddress());
		a.setArgumentValue("NewInternalPort", map.getInternalPort());
		a.setArgumentValue("NewProtocol", map.getProtocol().toString());
		String desc = map.getDescription();
		if (desc != null)
			a.setArgumentValue("NewPortMappingDescription", desc);
		a.setArgumentValue("NewEnabled", "1");
		a.setArgumentValue("NewLeaseDuration", 0);

		boolean succeed = a.postControlAction();
		if (succeed) {
			synchronized (this.registeredMappings) {
				this.registeredMappings.add(map);
			}
		}

		logger.log(Level.INFO, "UPnP address port mapping "
				+ (succeed ? "succeeded" : "failed")
				+ ": ext port " + map.getExternalPort()
				+ ", internal port " + map.getInternalPort());

		return succeed;
	}

	/**
	 * Deletes a port mapping.
	 *
	 * @return true if succeed.
	 */
	public boolean deleteMapping(int externalPort, Mapping.Protocol protocol) {
		Mapping map = new Mapping(externalPort, null, 0, protocol, null);
		return this.deleteMapping(map);
	}

	/**
	 * Deletes a port mapping.
	 *
	 * @param protocol "TCP" or "UDP"
	 * @return true if succeed.
	 */
	public boolean deleteMapping(Mapping map) {
		if (!this.deviceFound()) return false;

		Action a = this.serv.getAction("DeletePortMapping");
		if (a == null) return false;

		a.setArgumentValue("NewRemoteHost", "");
		a.setArgumentValue("NewExternalPort", map.getExternalPort());
		a.setArgumentValue("NewProtocol", map.getProtocol().toString());

		boolean succeed = a.postControlAction();
		if (succeed) {
			synchronized (this.registeredMappings) {
				this.registeredMappings.remove(map);
			}
		}

		logger.log(Level.INFO, "UPnP address port mapping "
				+ (succeed ? "deleted" : "deletion failed")
				+ ": ext port " + map.getExternalPort());

		return succeed;
	}

	public void clearMapping() {
		if (!this.deviceFound()) return;

		int size;
		Mapping[] maps;

		synchronized (this.registeredMappings) {
			size = this.registeredMappings.size();
			if (size <= 0) return;

			maps = new Mapping[size];
			this.registeredMappings.toArray(maps);
		}

		for (Mapping m: maps) {
			this.deleteMapping(m.getExternalPort(), m.getProtocol());
		}
	}
}

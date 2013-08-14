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
import java.net.Inet4Address;
import java.util.HashSet;
import java.util.Set;

/**
 * Virtual network interface represented in in user space.
 * This class is corresponding to "struct uvif" in vif.h of pimd (IP multicast daemon).
 * Note that functions of this class requires native methods and "root" priviledge.
 */
public final class VirtualInterface {
	// static fields
	private static Set<VirtualInterface> vifs = null;

	// instance fields
	private final boolean isRegisterVIF;
	private final Inet4Address localAddress;
	private final int localAddressAsInt;
	private final int netmask;
	private final int subnet;
	private final int subnetBroadcast;
	private final String name;
	private final int ifIndex;
	private boolean isQuerier;

	private QuerierSet querierSet;
	private GroupSet groupSet;

	public synchronized static Set<VirtualInterface> getVirtualInterfaces() throws IOException {
		if (vifs == null) {	// not initialized
			Native.initialize();	// throws IOException

			vifs = new HashSet<VirtualInterface>();

			int nVifs = Native.numberOfVIFs();
			if (nVifs > 0) {
				NativeVIF[] nativeVIFs = new NativeVIF[nVifs];
				for (int i = 0; i < nVifs; i++) {
					nativeVIFs[i] = new NativeVIF();
				}
				Native.fillVIFs(nativeVIFs);

				for (int i = 0; i < nVifs; i++) {
					NativeVIF v = nativeVIFs[i];
					VirtualInterface vif = new VirtualInterface(
							v.isRegisterVIF, v.localAddress, v.netmask, v.name, v.ifIndex);
					vifs.add(vif);
				}
			}
		}

		return vifs;
	}

	private VirtualInterface(boolean isRegisterVIF, int localadr, int netmask, String name, int ifIndex) {
		this.isRegisterVIF = isRegisterVIF;
		this.localAddress = Utilities.intToInet4Address(localadr);
		this.localAddressAsInt = localadr;
		this.netmask = netmask;
		this.subnet = localadr & netmask;
		this.subnetBroadcast = this.subnet | ~netmask; 
		this.name = name;
		this.ifIndex = ifIndex;
		this.isQuerier = true;

		this.querierSet = new QuerierSet();
		this.groupSet = new GroupSet(this);
	}

	public boolean isRegisterVIF() { return this.isRegisterVIF; }
	public Inet4Address getLocalAddress() { return this.localAddress; }
	public int getLocalAddressAsInt() { return this.localAddressAsInt; }
	public int getNetmask() { return this.netmask; }
	public int getSubnet() { return this.subnet; }
	public int getSubnetBroadcast() { return this.subnetBroadcast; }
	public String getName() { return this.name; }
	public int getIfIndex() { return this.ifIndex; }

	public boolean isQuerier() { return this.isQuerier; }
	public boolean setQuerier(boolean flag) {
		boolean old = this.isQuerier;
		this.isQuerier = flag;
		return old;
	}

	public QuerierSet getQuerierSet() { return this.querierSet; }
	public GroupSet getGroupSet() { return this.groupSet; }

	public static VirtualInterface findVirtualInterface(Inet4Address src) {
		int srcAddressInt = Utilities.Inet4AddressToInt(src);

		for (VirtualInterface vif: vifs) {
			if (vif.isRegisterVIF())
				continue;

			int netmask = vif.getNetmask();

			if ((srcAddressInt & netmask) == vif.getSubnet()
				&& (netmask == 0xffffffff || srcAddressInt != vif.getSubnetBroadcast())) {
				return vif;
			}
		}

		return null;
	}

	public String toString() {
		return toString("");
	}

	public String toString(String indent) {
		StringBuilder sb = new StringBuilder();
		boolean firstFlag = true;

		// interface itself
		String netmaskStr = Integer.toHexString(this.netmask);
		netmaskStr = "00000000".substring(0, 8 - netmaskStr.length()) + netmaskStr;

		sb.append(indent);
		sb.append(this.ifIndex);
		sb.append(":");
		sb.append(this.name);
		sb.append(":");
		sb.append(this.localAddress);
		sb.append("/");
		sb.append(netmaskStr);
		sb.append("(");
		if (this.isRegisterVIF) {
			if (firstFlag)
				firstFlag = false;
			else
				sb.append(",");
			sb.append("reg_vif");
		}
		if (this.isQuerier) {
			if (firstFlag)
				firstFlag = false;
			else
				sb.append(",");
			sb.append("querier");
		}
		sb.append(")");

		// queriers
		sb.append("\n");
		sb.append(indent);
		sb.append(this.querierSet.toString(indent));

		// groups
		sb.append("\n");
		sb.append(indent);
		sb.append(this.groupSet.toString(indent));

		return sb.toString();
	}

	static class NativeVIF {
		boolean isRegisterVIF;
		int localAddress;
		int netmask;
		String name;
		int ifIndex;
	}
}

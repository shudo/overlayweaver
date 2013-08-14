/*
 * This file has been derived from pimd-2.1.0-alpha29.17.
 * The pimd program is covered by the license in the accompanying file
 * named "LICENSE.pimd-2.1.0-alpha29.17".
 *
 * Part of the pimd program has been derived from mrouted.
 * The mrouted program is covered by the license in the accompanying file
 * named "LICENSE.mtouted".
 */

#include "common.h"

/*
 * Query the kernel to find network interfaces that are multicast-capable
 * and install them in the uvifs array.
 */
void config_vifs_from_kernel() {
    struct ifreq *ifrp, *ifend;
    register struct uvif *v;
    register vifi_t vifi;
    int n;
    uint32_t addr, mask, subnet;
    short flags;
    int num_ifreq = 32;
    struct ifconf ifc;
    char *newbuf;

    total_interfaces = 0; /* The total number of physical interfaces */
    
    ifc.ifc_len = num_ifreq * sizeof(struct ifreq);
    ifc.ifc_buf = calloc(ifc.ifc_len, sizeof(char));
    while (ifc.ifc_buf) {
	if (ioctl(udp_socket, SIOCGIFCONF, (char *)&ifc) < 0)
	    log(LOG_ERR, errno, "ioctl SIOCGIFCONF");
	
	/*
	 * If the buffer was large enough to hold all the addresses
	 * then break out, otherwise increase the buffer size and
	 * try again.
	 *
	 * The only way to know that we definitely had enough space
	 * is to know that there was enough space for at least one
	 * more struct ifreq. ???
	 */
	if ((num_ifreq * sizeof(struct ifreq)) >=
	    ifc.ifc_len + sizeof(struct ifreq))
	    break;
	
	num_ifreq *= 2;
	ifc.ifc_len = num_ifreq * sizeof(struct ifreq);
	newbuf = realloc(ifc.ifc_buf, ifc.ifc_len);
	if (newbuf == NULL)
	    free(ifc.ifc_buf);
	ifc.ifc_buf = newbuf;
    }
    if (ifc.ifc_buf == NULL)
	log(LOG_ERR, 0, "config_vifs_from_kernel: ran out of memory");
    
    ifrp = (struct ifreq *)ifc.ifc_buf;
    ifend = (struct ifreq *)(ifc.ifc_buf + ifc.ifc_len);
    /*
     * Loop through all of the interfaces.
     */
    for (; ifrp < ifend; ifrp = (struct ifreq *)((char *)ifrp + n)) {
	struct ifreq ifr;
#ifdef HAVE_SA_LEN
	n = ifrp->ifr_addr.sa_len + sizeof(ifrp->ifr_name);
	if (n < sizeof(*ifrp))
	    n = sizeof(*ifrp);
#else
	n = sizeof(*ifrp);
#endif /* HAVE_SA_LEN */
	
	/*
	 * Ignore any interface for an address family other than IP.
	 */
	if (ifrp->ifr_addr.sa_family != AF_INET) {
	    total_interfaces++;  /* Eventually may have IP address later */
	    continue;
	}
	
	addr = ((struct sockaddr_in *)&ifrp->ifr_addr)->sin_addr.s_addr;
	
	/*
	 * Need a template to preserve address info that is
	 * used below to locate the next entry.  (Otherwise,
	 * SIOCGIFFLAGS stomps over it because the requests
	 * are returned in a union.)
	 */
	bcopy(ifrp->ifr_name, ifr.ifr_name, sizeof(ifr.ifr_name));
	
	/*
	 * Ignore loopback interfaces and interfaces that do not
	 * support multicast.
	 */
	if (ioctl(udp_socket, SIOCGIFFLAGS, (char *)&ifr) < 0)
	    log(LOG_ERR, errno, "ioctl SIOCGIFFLAGS for %s", ifr.ifr_name);
	flags = ifr.ifr_flags;
	if ((flags & (IFF_LOOPBACK | IFF_MULTICAST)) != IFF_MULTICAST)
	    continue;
	
	/*
	 * Everyone below is a potential vif interface.
	 * We don't care if it has wrong configuration or not configured
	 * at all.
	 */	  
	total_interfaces++;

	/*
	 * Ignore any interface whose address and mask do not define a
	 * valid subnet number, or whose address is of the form
	 * {subnet,0} or {subnet,-1}.
	 */
	if (ioctl(udp_socket, SIOCGIFNETMASK, (char *)&ifr) < 0) {
	    if (!(flags & IFF_POINTOPOINT)) {
		log(LOG_ERR, errno, "ioctl SIOCGIFNETMASK for %s",
		    ifr.ifr_name);
	    } else {
		mask = 0xffffffff;
	    }
	} else {
	    mask = ((struct sockaddr_in *)&ifr.ifr_addr)->sin_addr.s_addr;
	}
	
	subnet = addr & mask;
	if ((!inet_valid_subnet(subnet, mask))
	    || (addr == subnet) || addr == (subnet | ~mask)) {
	    if (!(inet_valid_host(addr) && (flags & IFF_POINTOPOINT))) {
		log(LOG_WARNING, 0,
		    "ignoring %s, has invalid address (%s) and/or mask (%s)",
		    ifr.ifr_name, inet_fmt(addr, s1), inet_fmt(mask, s2));
		continue;
	    }
	}
	
	/*
	 * Ignore any interface that is connected to the same subnet as
	 * one already installed in the uvifs array.
	 */
	/*
	 * TODO: XXX: bug or "feature" is to allow only one interface per
	 * subnet?
	 */
	for (vifi = 0, v = uvifs; vifi < numvifs; ++vifi, ++v) {
	    if (strcmp(v->uv_name, ifr.ifr_name) == 0) {
		log(LOG_DEBUG, 0,
		    "skipping %s (%s on subnet %s) (alias for vif#%u?)",
		    v->uv_name, inet_fmt(addr, s1),
		    netname(subnet, mask), vifi);
		break;
	    }
	    /* we don't care about point-to-point links in same subnet */
	    if (flags & IFF_POINTOPOINT)
		continue;
	    if (v->uv_flags & VIFF_POINT_TO_POINT)
		continue;
#if 0
	    /*
	     * TODO: to allow different interfaces belong to
	     * overlapping subnet addresses, use this version instead
	     */
	    if (((addr & mask ) == v->uv_subnet) &&
		(v->uv_subnetmask == mask)) {
		log(LOG_WARNING, 0, "ignoring %s, same subnet as %s",
		    ifr.ifr_name, v->uv_name);
		break;
	    }
#else
	    if ((addr & v->uv_subnetmask) == v->uv_subnet ||
		(v->uv_subnet & mask) == subnet) {
		log(LOG_WARNING, 0, "ignoring %s, same subnet as %s",
		    ifr.ifr_name, v->uv_name);
		break;
	    }
#endif /* 0 */
	}
	if (vifi != numvifs)
	    continue;
	
	/*
	 * If there is room in the uvifs array, install this interface.
	 */
	if (numvifs == MAXVIFS) {
	    log(LOG_WARNING, 0, "too many vifs, ignoring %s", ifr.ifr_name);
	    continue;
	}
	v = &uvifs[numvifs];
	zero_vif(v, FALSE);
	v->uv_lcl_addr		= addr;
	v->uv_subnet		= subnet;
	v->uv_subnetmask	= mask;
	v->uv_subnetbcast	= subnet | ~mask;
	strncpy(v->uv_name, ifr.ifr_name, IFNAMSIZ);
	
	if (flags & IFF_POINTOPOINT) {
	    v->uv_flags |= (VIFF_REXMIT_PRUNES | VIFF_POINT_TO_POINT);
	    if (ioctl(udp_socket, SIOCGIFDSTADDR, (char *)&ifr) < 0) {
		log(LOG_ERR, errno, "ioctl SIOCGIFDSTADDR for %s", v->uv_name);
	    } else {
		v->uv_rmt_addr
		    = ((struct sockaddr_in *)(&ifr.ifr_dstaddr))->sin_addr.s_addr;
	    
	    }
	}
#ifdef linux
        {
	    struct ifreq ifridx;
	    
	    memset(&ifridx, 0, sizeof(ifridx));
	    strncpy(ifridx.ifr_name,v->uv_name, IFNAMSIZ);
	    if (ioctl(udp_socket, SIOGIFINDEX, (char *) &ifridx) < 0) {
		log(LOG_ERR, errno, "ioctl SIOGIFINDEX for %s",
		    ifridx.ifr_name);
		/* Not reached */
		return;
	    }
	    v->uv_ifindex = ifridx.ifr_ifindex;
        }
	if (flags & IFF_POINTOPOINT) {
	    log(LOG_INFO, 0,
		"installing %s (%s -> %s) as vif #%u-%d - rate=%d",
		v->uv_name, inet_fmt(addr, s1), inet_fmt(v->uv_rmt_addr, s2),
		numvifs, v->uv_ifindex, v->uv_rate_limit);
	} else {
	    log(LOG_INFO, 0,
		"installing %s (%s on subnet %s) as vif #%u-%d - rate=%d",
		v->uv_name, inet_fmt(addr, s1), netname(subnet, mask),
		numvifs, v->uv_ifindex, v->uv_rate_limit);
	}
#else /* !linux */
	if (flags & IFF_POINTOPOINT) {
	    log(LOG_INFO, 0,
		"installing %s (%s -> %s) as vif #%u - rate=%d",
		v->uv_name, inet_fmt(addr, s1), inet_fmt(v->uv_rmt_addr, s2),
		numvifs, v->uv_rate_limit);
	} else {
	    log(LOG_INFO, 0,
		"installing %s (%s on subnet %s) as vif #%u - rate=%d",
		v->uv_name, inet_fmt(addr, s1), netname(subnet, mask),
		numvifs, v->uv_rate_limit);
	}
#endif /* linux */
	
	++numvifs;
	
	/*
	 * If the interface is not yet up, set the vifs_down flag to
	 * remind us to check again later.
	 */
	if (!(flags & IFF_UP)) {
	    v->uv_flags |= VIFF_DOWN;
	    vifs_down = TRUE;
	}
    }
}

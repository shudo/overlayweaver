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
 * Exported variables.
 */
struct uvif	uvifs[MAXVIFS]; /* array of all virtual interfaces          */
vifi_t numvifs;	/* Number of vifs in use                    */
int vifs_down;      /* 1=>some interfaces are down              */
int phys_vif;       /* An enabled vif                           */
vifi_t reg_vif_num;    /* really virtual interface for registers   */
int udp_socket;	/* Since the honkin' kernel doesn't support */
				/* ioctls on raw IP sockets, we need a UDP  */
				/* socket as well as our IGMP (raw) socket. */
				/* How dumb.                                */
int total_interfaces; /* Number of all interfaces: including the
				   * non-configured, but excluding the
				   * loopback interface and the non-multicast
				   * capable interfaces.
				   */

static int init_reg_vif();
static void start_all_vifs();
static void start_vif(vifi_t vifi);
static void stop_vif(vifi_t vifi);


void init_vifs() {
    vifi_t vifi;
    struct uvif *v;
    int enabled_vifs;
	
    numvifs    = 0;
    reg_vif_num = NO_VIF;
    vifs_down = FALSE;

    /*
     * Configure the vifs based on the interface configuration of the
     * the kernel and the contents of the configuration file.
     * (Open a UDP socket for ioctl use in the config procedures if
     * the kernel can't handle IOCTL's on the IGMP socket.)
     */
#ifdef IOCTL_OK_ON_RAW_SOCKET
    udp_socket = igmp_socket;
#else
    if ((udp_socket = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
	log(LOG_ERR, errno, "UDP socket");
#endif

    /*
     * Clean up all vifs
     */
    for (vifi = 0, v = uvifs; vifi < MAXVIFS; ++vifi, ++v) {
	zero_vif(v, FALSE);
    }

    log(LOG_INFO, 0, "Getting vifs from kernel");
    config_vifs_from_kernel();
#if 0	// by Shudo
    log(LOG_INFO, 0, "Getting vifs from %s", configfilename);
    config_vifs_from_file();
#endif

    /*
     * Quit if there are fewer than two enabled vifs.
     */
    enabled_vifs    = 0;
    phys_vif        = -1;
	 
    for (vifi = 0, v = uvifs; vifi < numvifs; ++vifi, ++v) {
	/* Initialize the outgoing timeout for each vif.
	 * Currently use a fixed time 'PIM_JOIN_PRUNE_HOLDTIME'.
	 * Later, may add a configurable array to feed these
	 * parameters, or compute them as function of the i/f
	 * bandwidth and the overall connectivity...etc.
	 */
#if 0	// by Shudo
	SET_TIMER(v->uv_jp_timer, PIM_JOIN_PRUNE_HOLDTIME);
#endif
	if (v->uv_flags
	    & (VIFF_DISABLED | VIFF_DOWN | VIFF_REGISTER | VIFF_TUNNEL))
	    continue;
	if (phys_vif == -1)
	    phys_vif = vifi;
	enabled_vifs++;
    }

    if (enabled_vifs < 1) /* XXX: TODO: */
	log(LOG_ERR, 0, "can't forward: %s",
	    enabled_vifs == 0 ? "no enabled vifs" : "only one enabled vif");

    k_init_pim(igmp_socket);	/* Call to kernel to initialize structures */

    /* Add a dummy virtual interface to support Registers in the kernel. 
     * In order for this to work, the kernel has to have been modified
     * with the PIM patches to ip_mroute.{c,h} and ip.c
     */
    init_reg_vif();

    start_all_vifs();
}

/*
 * Initialize the passed vif with all appropriate default values.
 * "t" is true if a tunnel or register_vif, or false if a phyint.
 */
void zero_vif(struct uvif *v, int t) {
    v->uv_flags		= 0;
    v->uv_metric	= DEFAULT_METRIC;
    v->uv_admetric	= 0;
    v->uv_threshold	= DEFAULT_THRESHOLD;
    v->uv_rate_limit	= t ? DEFAULT_REG_RATE_LIMIT : DEFAULT_PHY_RATE_LIMIT;
    v->uv_lcl_addr	= INADDR_ANY_N;
    v->uv_rmt_addr	= INADDR_ANY_N;
#if 0	// by Shudo
    v->uv_dst_addr	= t ? INADDR_ANY_N : allpimrouters_group;
#else
    v->uv_dst_addr	= INADDR_ANY_N;
#endif
    v->uv_subnet	= INADDR_ANY_N;
    v->uv_subnetmask	= INADDR_ANY_N;
    v->uv_subnetbcast	= INADDR_ANY_N;
    strncpy(v->uv_name, "", IFNAMSIZ);
    v->uv_groups	= (struct listaddr *)NULL;
    v->uv_dvmrp_neighbors = (struct listaddr *)NULL;
    NBRM_CLRALL(v->uv_nbrmap);
    v->uv_querier	= (struct listaddr *)NULL;
    v->uv_igmpv1_warn	= 0;
    v->uv_prune_lifetime = 0;
    v->uv_acl		= (struct vif_acl *)NULL;
#if 0	// by Shudo
    RESET_TIMER(v->uv_leaf_timer);
#endif
    v->uv_addrs		= (struct phaddr *)NULL;
    v->uv_filter	= (struct vif_filter *)NULL;
#if 0	// by Shudo
    RESET_TIMER(v->uv_pim_hello_timer);
    RESET_TIMER(v->uv_gq_timer);
    RESET_TIMER(v->uv_jp_timer);
#endif
#if 0	// by Shudo
    v->uv_pim_neighbors	= (struct pim_nbr_entry *)NULL;
#endif
#if 0	// by Shudo
    v->uv_local_pref	= default_source_preference;
    v->uv_local_metric	= default_source_metric;
#else
    v->uv_local_pref	= UCAST_DEFAULT_SOURCE_PREFERENCE;
    v->uv_local_metric	= UCAST_DEFAULT_SOURCE_METRIC;
#endif
#ifdef linux
    v->uv_ifindex	= -1;
#endif /* linux */
}


/*
 * Add a (the) register vif to the vif table.
 */
static int init_reg_vif() {
    struct uvif *v;
    vifi_t i;
    
    v = &uvifs[numvifs];
    v->uv_flags = 0; /* initialization */
    if ((numvifs + 1) == MAXVIFS) {
        /* Exit the program! The PIM router must have a Register vif */
	log(LOG_ERR, 0,
	    "cannot install the Register vif: too many interfaces");
	/* To make lint happy */
	return (FALSE);
    }
    
    /*
     * So far in PIM we need only one register vif and we save its number in
     * the global reg_vif_num.
     */
    reg_vif_num = numvifs;
    
    /* set the REGISTER flag */
    v->uv_flags = VIFF_REGISTER;
#ifdef PIM_EXPERIMENTAL
    v->uv_flags |= VIFF_REGISTER_KERNEL_ENCAP;
#endif
    strncpy(v->uv_name,"register_vif0", IFNAMSIZ);
    
    /* Use the address of the first available physical interface to
     * create the register vif.
     */
    for (i = 0; i < numvifs; i++)
	if (uvifs[i].uv_flags
	    & (VIFF_DOWN | VIFF_DISABLED | VIFF_REGISTER | VIFF_TUNNEL))
	    continue;
	else
	    break;
    if (i >= numvifs) {
	log(LOG_ERR, 0, "No physical interface enabled");
	return -1;
    }
    v->uv_lcl_addr = uvifs[i].uv_lcl_addr;
    v->uv_threshold = MINTTL;

    numvifs++;
    total_interfaces++;
    return 0;
}


static void start_all_vifs() {
    vifi_t vifi;
    struct uvif *v;
    unsigned int action;

    /* Start first the NON-REGISTER vifs */
    for(action = 0; ; action = VIFF_REGISTER) {
	for (vifi = 0, v = uvifs; vifi < numvifs; ++vifi, ++v) {
	    if ((v->uv_flags & VIFF_REGISTER) ^ action)
		/* If starting non-registers but the vif is a register
		 * or if starting registers, but the interface is not
		 * a register, then just continue.
		 */
		continue;
	    /* Start vif if not DISABLED or DOWN */
	    if (v->uv_flags & (VIFF_DISABLED | VIFF_DOWN)) {
		if (v->uv_flags & VIFF_DISABLED)
		    log(LOG_INFO, 0,
			"%s is DISABLED; vif #%u out of service", 
			v->uv_name, vifi);
		else
		    log(LOG_INFO, 0,
			"%s is DOWN; vif #%u out of service", 
			v->uv_name, vifi);
	    }
	    else
		start_vif(vifi);
	}
	if (action == VIFF_REGISTER)
	    break;   /* We are done */
    }
}



/*
 * stop all vifs
 */
void stop_all_vifs() {
    vifi_t vifi;

    for (vifi = 0; vifi < numvifs; vifi++) {
		stop_vif(vifi);
    }
}


/*
 * Initialize the vif and add to the kernel. The vif can be either
 * physical, register or tunnel (tunnels will be used in the future
 * when this code becomes PIM multicast boarder router.
 */
static void start_vif(vifi_t vifi) {
    struct uvif *v;
    uint32_t    src;

    v		    = &uvifs[vifi];
    src             = v->uv_lcl_addr;
    /* Initialy no router on any vif */
    if (v->uv_flags & VIFF_REGISTER) 
	v->uv_flags = v->uv_flags & ~VIFF_DOWN;
    else {
	v->uv_flags = (v->uv_flags | VIFF_DR | VIFF_NONBRS) & ~VIFF_DOWN;
#if 0	// by Shudo
	SET_TIMER(v->uv_pim_hello_timer, 1 + RANDOM() % PIM_TIMER_HELLO_PERIOD);
	SET_TIMER(v->uv_jp_timer, 1 + RANDOM() % PIM_JOIN_PRUNE_PERIOD);
	/* TODO: CHECK THE TIMERS!!!!! Set or reset? */
	RESET_TIMER(v->uv_gq_timer);
	v->uv_pim_neighbors = (pim_nbr_entry_t *)NULL;
#endif
    }
    
    /* Tell kernel to add, i.e. start this vif */
    k_add_vif(igmp_socket, vifi, &uvifs[vifi]);   
    log(LOG_INFO, 0, "%s comes up; vif #%u now in service", v->uv_name, vifi);
    
    if (!(v->uv_flags & VIFF_REGISTER)) {
#if 0	// by Shudo
	/*
	 * Join the PIM multicast group on the interface.
	 */
	k_join(igmp_socket, allpimrouters_group, v);
#endif
	
	/*
	 * Join the ALL-ROUTERS multicast group on the interface.
	 * This allows mtrace requests to loop back if they are run
	 * on the multicast router.
	 */
	k_join(igmp_socket, allroutersgroup, v);
	k_join(igmp_socket, allv3routersgroup, v);	// for IGMPv3
	
	/*
	 * Until neighbors are discovered, assume responsibility for sending
	 * periodic group membership queries to the subnet.  Send the first
	 * query.
	 */
	v->uv_flags |= VIFF_QUERIER;
#if 0	// by Shudo, this process is to be implemented in an upper layer
	query_groups(v);
#endif

#if 0	// by Shudo	
	/*
	 * Send a probe via the new vif to look for neighbors.
	 */
	send_pim_hello(v, PIM_TIMER_HELLO_HOLDTIME);
#endif
    }
#ifdef linux
    else {
	struct ifreq ifr;
	
	memset(&ifr, 0, sizeof(struct ifreq));
	/* strncpy(ifr.ifr_name,v->uv_name, IFNAMSIZ); */
	strncpy(ifr.ifr_name, "pimreg", IFNAMSIZ);
	if (ioctl(udp_socket, SIOGIFINDEX, (char *) &ifr) < 0) {
	    log(LOG_ERR, errno, "ioctl SIOGIFINDEX for %s", ifr.ifr_name);
	    /* Not reached */
	    return;
	}
	v->uv_ifindex = ifr.ifr_ifindex;
    }
#endif /* linux */
}


/*
 * Stop a vif (either physical interface, tunnel or
 * register.) If we are running only PIM we don't have tunnels.
 */
static void stop_vif(vifi_t vifi) {
    struct uvif *v;
    struct listaddr *a;
#if 0	// by Shudo
    register pim_nbr_entry_t *n, *next;
#endif
    struct vif_acl *acl;
    
    /*
     * TODO: make sure that the kernel viftable is 
     * consistent with the daemon table
     */	
    v = &uvifs[vifi];
    if (!(v->uv_flags & VIFF_REGISTER)) {
#if 0	// by Shudo
	k_leave(igmp_socket, allpimrouters_group, v);
#endif
	k_leave(igmp_socket, allroutersgroup, v);
	/*
	 * Discard all group addresses.  (No need to tell kernel;
	 * the k_del_vif() call will clean up kernel state.)
	 */
	while (v->uv_groups != NULL) {
	    a = v->uv_groups;
	    v->uv_groups = a->al_next;
	    free((char *)a);
	}
    }
    
    /*
     * TODO: inform (eventually) the neighbors I am going down by sending
     * PIM_HELLO with holdtime=0 so someone else should become a DR.
     */ 
    /* TODO: dummy! Implement it!! Any problems if don't use it? */
#if 0	// by Shudo
    delete_vif_from_mrt(vifi);
#endif
    
    /*
     * Delete the interface from the kernel's vif structure.
     */
    k_del_vif(igmp_socket, vifi);
    v->uv_flags     = (v->uv_flags & ~VIFF_DR & ~VIFF_QUERIER & ~VIFF_NONBRS )
	              | VIFF_DOWN;
    if (!(v->uv_flags & VIFF_REGISTER)) {
#if 0	// by Shudo
	RESET_TIMER(v->uv_pim_hello_timer);
	RESET_TIMER(v->uv_jp_timer);
	RESET_TIMER(v->uv_gq_timer);
#endif
#if 0	// by Shudo
	for (n = v->uv_pim_neighbors; n != NULL; n = next) {
	    next = n->next;	/* Free the space for each neighbour */
	    free((char *)n);
	}
	v->uv_pim_neighbors = NULL;
#endif
    }

    /* TODO: currently not used */
   /* The Access Control List (list with the scoped addresses) */
    while (v->uv_acl != NULL) {
	acl = v->uv_acl;
	v->uv_acl = acl->acl_next;
	free((char *)acl);
    }

    vifs_down = TRUE;
    log(LOG_INFO, 0,
	"%s goes down; vif #%u out of service", v->uv_name, vifi);
}

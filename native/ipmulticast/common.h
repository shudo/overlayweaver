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

#ifndef IGMPCOMMON_H_
#define IGMPCOMMON_H_

#include <stdio.h>		// for printf(3), stdout and ...
#include <stdlib.h>		// for malloc(3)
#include <stdint.h>		// for uint32_t and uint8_t
#include <stdarg.h>	// va_start(3) and others
#include <string.h>		// for memset(3)
#include <netinet/ip.h>	// for struct ip
#include <netinet/igmp.h>	// for struct igmp
#include <netinet/in.h>	// for htonl(3) and IPPROTO_IN
#include <netinet/udp.h>	// for struct udphdr
#include <unistd.h>		// for close(2)
#include <errno.h>		// for perror(3) and errno
#include <sys/types.h>	// for setsockopt(2) and recv(2)
#include <sys/socket.h>	// for PF_PACKET, setsockopt(2), recv(2) and SIOC[GS]IFFLAG
#include <netpacket/packet.h>	// for PF_PACKET
#include <net/ethernet.h>	// for PF_PACKET
#include <sys/ioctl.h>	// for ioctl(2)
#include <sys/syslog.h>	// for LOG_*
#include <linux/if.h>	// for IFF_ALLMULTI
#ifdef linux
#  define _LINUX_IN_H
#  include <linux/mroute.h>	// for vifi_t, MAXVIFS
#else
#  include <netinet/ip_mroute.h>
#endif
#include "vif.h"

#ifndef IGMP_V2_MEMBERSHIP_REPORT
#  define IGMP_V2_MEMBERSHIP_REPORT	0x22	// Ver. 3 membership report
#endif
#ifndef INADDR_ALLRPTS_GROUP
#  define INADDR_ALLRPTS_GROUP	0xe0000016U	// 224.0.0.22 for IGMPv3
#endif

#define TRUE 1
#define FALSE 0

#ifdef linux
#  define RAW_INPUT_IS_RAW
#  define RAW_OUTPUT_IS_RAW
#  undef HAVE_SA_LEN
#else
#  undef RAW_INPUT_IS_RAW
#  undef RAW_INPUT_IS_RAW
#  undef HAVE_SA_LEN
#endif

#ifndef MAXHOSTNAMELEN
#  define MAXHOSTNAMELEN	64
#endif

// used by DVMRP in pimd
#define DEFAULT_METRIC		1
#define DEFAULT_THRESHOLD	1

// used if no reliable unicast routing information available
#define UCAST_DEFAULT_SOURCE_METRIC		1024
#define UCAST_DEFAULT_SOURCE_PREFERENCE	1024

// used by PIM in pimd
#define DEFAULT_PHY_RATE_LIMIT	0
#define DEFAULT_REG_RATE_LIMIT	0

#define INADDR_ANY_N	(uint32_t)0x00000000	/* INADDR_ANY in network byteorder */


// parameters

#define RECV_BUF_MAX_SIZE (256 * 1024)
#define RECV_BUF_MIN_SIZE (48 * 1024)
#define SEND_BUF_MAX_SIZE (256 * 1024)
#define SEND_BUF_MIN_SIZE (48 * 1024)

#define MINTTL	1
#define DEFAULT_DGRAM_TTL	128

#define CALCULATE_UDP_CHECKSUM

#define MULTICAST_LOOP	TRUE


// in igmp.c
#define MIN(a, b)	((a) > (b) ? (b) : (a))
#define BUF_SIZE	8192

extern int igmp_socket;
extern uint32_t selfaddr;
extern uint32_t allhostsgroup;
extern uint32_t allroutersgroup;
extern uint32_t allv3routersgroup;

int init_igmp(char *ifName);
void stop_igmp();
int send_igmp(uint32_t src, uint32_t dest,
		int type, int code, int32_t group,
		char *data, int datalen);
int recv_igmp(struct ip **ipp, struct igmp **igmpp, char *data, int *datalenp, int maxdatasize);

// in dgram.c
extern int sock_dgram_send, sock_dgram_recv;

int init_dgram();
void stop_dgram();
int send_dgram(uint32_t srcaddr, uint16_t srcport, uint32_t destaddr, uint16_t destport, uint16_t id, uint8_t ttl, char *data, uint16_t datalen);
int recv_dgram(struct ip **ipp, struct udphdr **udpp, char *data, int *datalenp, int maxdatasize, int only_mcast);

// in util.c
#define LOG_BUF_SIZE	512

void log(int severity, int syserr, char *format, ...);

// vif.c
extern struct uvif uvifs[MAXVIFS];
extern vifi_t numvifs;
extern int vifs_down;
extern int phys_vif;
extern vifi_t reg_vif_num;
extern int udp_socket;
extern int total_interfaces;

void init_vifs();
void zero_vif(struct uvif *v, int t);
void stop_all_vifs();

// config.c
void config_vifs_from_kernel();

// in kern.c
#ifdef RAW_OUTPUT_IS_RAW
extern int curttl;
#endif

void k_init_pim(int socket);
void k_stop_pim(int socket);
void k_set_sndbuf(int sock, int bufSize, int minSize);
void k_set_rcvbuf(int sock, int bufSize, int minSize);
void k_hdr_include(int socket, int bool);
void k_set_ttl(int socket, int t);
void k_set_loop(int socket, int flag);
void k_set_if(int socket, uint32_t ifa);
void k_join(int socket, uint32_t grp, struct uvif *v);
void k_leave(int socket, uint32_t grp, struct uvif *v);
void k_add_vif(int socket, vifi_t vifi, struct uvif *v);
void k_del_vif(int socket, vifi_t vifi);
#if 0
int k_del_mfc(int socket, uint32_t source, uint32_t group);
int k_chg_mfc(int socket, uint32_t source, uint32_t group, vifi_t iif, vifbitmap_t oifs, uint32_t rp_addr);
int k_get_vif_count(vifi_t vifi, struct vif_count *retval);
int k_get_sg_cnt(int socket, uint32_t source, uint32_t group, struct sg_count *retval);
#endif

// inet.c
extern char s1[19];
extern char s2[19];
extern char s3[19];
extern char s4[19];

int inet_valid_host(uint32_t naddr);
int inet_valid_subnet(uint32_t nsubnet, uint32_t nmask);
char *inet_fmt(uint32_t addr, char *s);
int inet_cksum(uint16_t *addr, unsigned int len, int add);
char *netname(uint32_t addr, uint32_t mask);

#endif /*IGMPCOMMON_H_*/

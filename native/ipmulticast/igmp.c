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

#include "common.h"

int igmp_socket;
uint32_t selfaddr;
uint32_t allhostsgroup;
uint32_t allroutersgroup;
uint32_t allv3routersgroup;

static char *recvbuf = NULL;
static char *sendbuf = NULL;
static char igmp_kind_string[21];

static int loop = MULTICAST_LOOP;

static char *igmp_packet_kind(char *buf, size_t bufLen, uint8_t type, uint8_t code);


/*
 * Open and initialize an IGMP socket.
 */
int init_igmp(char *ifName) {
	struct ip *ip;

	if (recvbuf != NULL) free(recvbuf);
	if (sendbuf != NULL) free(sendbuf);
	recvbuf = malloc(BUF_SIZE);
	sendbuf = malloc(BUF_SIZE);

	// create a raw socket
	if ((igmp_socket = socket(PF_INET, SOCK_RAW, IPPROTO_IGMP)) < 0) {
		log(LOG_ERR, errno, "Failed to create a raw socket. You may have to be the \"root\" user to have priviledge.");
		return -1;
	}

#if 0
	// examine self IP addresses
	{
		struct ifconf ifc;
		int numIfreq = 32;
		struct ifreq *ifrp;
		struct ifreq *ifrEnd;
		int n;
		uint32_t addr;

		ifc.ifc_len = numIfreq * sizeof(struct ifreq);
		ifc.ifc_buf = calloc(ifc.ifc_len, sizeof(uint8_t));
		while (ifc.ifc_buf) {
			char * newBuf;

			ioctl(igmp_socket, SIOCGIFCONF, (char *)&ifc);

			if ((ifc.ifc_len + sizeof(struct ifreq)) <= (numIfreq * sizeof(struct ifreq))) {
				break;
			}

			numIfreq *= 2;
			ifc.ifc_len = numIfreq * sizeof(struct ifreq);
			newBuf = realloc(ifc.ifc_buf, ifc.ifc_len);
			if (newBuf == NULL) {
				free(ifc.ifc_buf);
			}
			ifc.ifc_buf = newBuf;
		}
		if (ifc.ifc_buf == NULL) {
			log(LOG_ERR, 0, "Failed in malloc().");
			return -1;
		}

		ifrp = (struct ifreq *)ifc.ifc_buf;
		ifrEnd = (struct ifreq *)(ifc.ifc_buf + ifc.ifc_len);
		for ( ; ifrp < ifrEnd; ifrp = (struct ifreq *)((char *)ifrp + n)) {
#ifdef HAVE_SA_LEN
			n = ifrp->ifr_addr.sa_len + sizeof(ifrp->ifr_name);
			if (n < sizeof(*ifrp)) {
				n = sizeof(*ifrp);
			}
#else
			n = sizeof(*ifrp);
#endif

			if (ifrp->ifr_addr.sa_family != AF_INET) {
				// ignore
				continue;
			}

			memcpy(ifr.ifr_name, ifrp->ifr_name, sizeof(ifr.ifr_name));
			if (ioctl(igmp_socket, SIOCGIFFLAGS, (char *)&ifr) < 0) {
				log(LOG_ERR, errno, "Failed in ioctl(2) SIOCGIFFLAGS for %s.\n", ifrp->ifr_name);
				continue;
			}

			if ((ifr.ifr_flags & (IFF_LOOPBACK | IFF_MULTICAST)) != IFF_MULTICAST) {
				// ignore loopback interface
				continue;
			}

			if (ifName != NULL) {
				if (strncmp(ifName, ifrp->ifr_name, sizeof(ifrp->ifr_name))) {
					// found interface has a different name from the specified one
					continue;
				}
			}

			// use an interface found first
			ifName = ifrp->ifr_name;
			selfaddr = ((struct sockaddr_in *)&ifrp->ifr_addr)->sin_addr.s_addr;
printf("name: %s\n", ifName);
printf("addr: %s\n", inet_fmt(selfaddr, s1));
		}

		free(ifc.ifc_buf);
	}
#endif

	k_hdr_include(igmp_socket, TRUE);
	k_set_sndbuf(igmp_socket, SEND_BUF_MAX_SIZE, SEND_BUF_MIN_SIZE);
	k_set_rcvbuf(igmp_socket, RECV_BUF_MAX_SIZE, RECV_BUF_MIN_SIZE);
	k_set_ttl(igmp_socket, 1);
	k_set_loop(igmp_socket, loop);

	k_hdr_include(igmp_socket, TRUE);
	k_set_sndbuf(igmp_socket, SEND_BUF_MAX_SIZE, SEND_BUF_MIN_SIZE);
	k_set_rcvbuf(igmp_socket, RECV_BUF_MAX_SIZE, RECV_BUF_MIN_SIZE);
	k_set_ttl(igmp_socket, 1);
	k_set_loop(igmp_socket, loop);

	// initialize IP header
	ip = (struct ip *)sendbuf;
	memset((void *)ip, 0, sizeof(struct ip));

	ip->ip_v = IPVERSION;
	ip->ip_hl = sizeof(struct ip) >> 2;
	ip->ip_tos = IPTOS_PREC_INTERNETCONTROL;	// 0xc0
	ip->ip_ttl = MAXTTL;
	ip->ip_p = IPPROTO_IGMP;
	ip->ip_sum = 0;		// kernel fills in

	// initialize addresses
	allhostsgroup = htonl(INADDR_ALLHOSTS_GROUP);
	allroutersgroup = htonl(INADDR_ALLRTRS_GROUP);
	allv3routersgroup = htonl(INADDR_ALLRPTS_GROUP);

	return 0;
}


void stop_igmp() {
	if (igmp_socket > 0) {
		close(igmp_socket);
		igmp_socket = -1;
	}

	if (igmp_socket > 0) {
		close(igmp_socket);
		igmp_socket = -1;
	}

	if (recvbuf != NULL) {
		free(recvbuf);
		recvbuf = NULL;
	}

	if (sendbuf != NULL) {
		free(sendbuf);
		sendbuf = NULL;
	}
}


int send_igmp(uint32_t src, uint32_t dest,
		int type, int code, int32_t group,
		char *data, int datalen) {
	struct ip *ip;
	struct igmp *igmp;
	int sendlen;
	int setloop = FALSE;
	struct sockaddr_in destsock;
	char *buf = sendbuf;

	// prepare IP header
	ip = (struct ip *)buf;
	ip->ip_len = sizeof(struct ip) + IGMP_MINLEN + datalen;
	ip->ip_src.s_addr = src;
	ip->ip_dst.s_addr = dest;
	sendlen = ip->ip_len;
#if defined(RAW_OUTPUT_IS_RAW) || defined(OpenBSD)
	ip->ip_len = htons(ip->ip_len);
#endif

	igmp = (struct igmp *)(buf + sizeof(struct ip));
	igmp->igmp_type = (uint8_t)type;
	igmp->igmp_code = (uint8_t)code;
	igmp->igmp_group.s_addr = group;
	if (data != NULL) {
		if (sizeof(struct ip) + IGMP_MINLEN + datalen > BUF_SIZE) {
			datalen = BUF_SIZE - (sizeof(struct ip) + IGMP_MINLEN);
			log(LOG_WARNING, 0, "Given data is too large: %d\n", datalen);
		}
		memcpy((char *)(buf + sizeof(struct ip) + IGMP_MINLEN), data, datalen);
	}
	igmp->igmp_cksum = 0;
	igmp->igmp_cksum = inet_cksum((uint16_t *)igmp, IGMP_MINLEN + datalen, 0);

	if (IN_MULTICAST(ntohl(dest))) {
		k_set_if(igmp_socket, src);
		if (type != IGMP_DVMRP || dest == allhostsgroup) {
			setloop = TRUE;
			k_set_loop(igmp_socket, TRUE);
		}
#ifdef RAW_OUTPUT_IS_RAW
		ip->ip_ttl = curttl;
	}
	else {
		ip->ip_ttl = MAXTTL;
#endif
	}

	memset((void *)&destsock, 0, sizeof(destsock));
	destsock.sin_family = PF_INET;
#ifdef HAVE_SA_LEN
	destsock.sin_len = sizeof(destsock);
#endif
	destsock.sin_addr.s_addr = dest;

	if (sendto(igmp_socket, sendbuf, sendlen, 0,
			(struct sockaddr *)&destsock, sizeof(destsock)) < 0) {
		log(LOG_ERR, errno, "Failed in sendto(2) to %s on %s.",
			inet_fmt(dest, s1),
			inet_fmt(src, s2));

		if (setloop) {
			k_set_loop(igmp_socket, loop);
		}

		return -1;
	}

	if (setloop) {
		k_set_loop(igmp_socket, loop);
	}

	return 0;
}


int recv_igmp(struct ip **ipp, struct igmp **igmpp, char *data, int *datalenp, int maxdatasize) {
	uint32_t src, dest, group;
	struct ip *ip;
	struct igmp *igmp;
	int ipheaderlen, ipdatalen, igmpdatalen;
	int recvlen;

	// receive
	while (TRUE) {
		socklen_t fromlen;

		recvlen = recvfrom(igmp_socket, recvbuf, BUF_SIZE, 0, NULL, &fromlen);
		if (recvlen < 0) {
			if (errno != EINTR) {
				log(LOG_ERR, errno, "recv(2) returned -1.");
			}
			return -1;
		}

		if (recvbuf == NULL) {
			// IGMP subsystem has been stopped
			// Note that recvfrom(2) returns a non-negative number even though interrupted.
			return -1;
		}

		if (recvlen < sizeof (struct ip)) {
			log(LOG_WARNING, 0, "Received packet is too short: %d", recvlen);
			continue;
		}

		ip = (struct ip *)recvbuf;
		src = ip->ip_src.s_addr;
		dest = ip->ip_dst.s_addr;

		if (ip->ip_p == 0) {
			// message from kernel
			if (src == 0 || dest == 0) {
				log(LOG_WARNING, 0, "Message from kernel is not accurate.");
			}
			else {
				// do nothing
			}
			continue;
		}

		ipheaderlen = ip->ip_hl << 2;
#ifdef RAW_INPUT_IS_RAW
		ipdatalen = ntohs(ip->ip_len) - ipheaderlen;
#else
		ipdatalen = ip->ip_len;
#endif
		if (ipheaderlen + ipdatalen != recvlen) {
			log(LOG_WARNING, 0, "Received packet from %s is shorter (%u bytes) than header+data length (%u+%u).",
				inet_fmt(src, s1), recvlen, ipheaderlen, ipdatalen);
			continue;
		}

		igmp = (struct igmp *)(recvbuf + ipheaderlen);
		group = igmp->igmp_group.s_addr;
		igmpdatalen = ipdatalen - IGMP_MINLEN;
		if (igmpdatalen < 0) {
			log(LOG_WARNING, 0, "Received IP data field is too short (%u bytes) for IGMP, from %s",
				ipdatalen, inet_fmt(src, s1));
			continue;
		}

		if (ipp != NULL) *ipp = ip;

		if (igmpp != NULL) *igmpp = igmp;

		if (datalenp != NULL) {
			*datalenp = ipdatalen;
		}

		if ((ipdatalen > 0) && (data != NULL)) {
			int len = MIN(ipdatalen, maxdatasize);
			memcpy(data, recvbuf + ipheaderlen, len);	// including entire IGMP packet
		}

#if 0
		printf("received packet: %s\n",
			igmp_packet_kind(igmp_kind_string, sizeof(igmp_kind_string),
						igmp->igmp_type, igmp->igmp_code));
		printf("  src:  %s\n", inet_fmt(src, s1));
		printf("  dest: %s\n", inet_fmt(dest, s1));
		fflush(stdout);
#endif

		return 0;
	}
}


static char *igmp_packet_kind(char *buf, size_t bufLen, uint8_t type, uint8_t code) {
	switch (type) {
		case IGMP_MEMBERSHIP_QUERY:
			return "membership query";
		case IGMP_V1_MEMBERSHIP_REPORT:
			return "V1 membership report";
		case IGMP_V2_MEMBERSHIP_REPORT:
			return "V2 membership report";
		case IGMP_V2_LEAVE_GROUP:
			return "V2 leave group";
		case IGMP_DVMRP:
			return "DVMRP related";
		case IGMP_PIM:
			return "PIM related";
		case IGMP_MTRACE:
			return "trace query";
		case IGMP_MTRACE_RESP:
			return "trace reply";
		default:
			snprintf(buf, bufLen, "unknown: 0x%02x/0x%02x", type, code);
			return buf;
	}
}

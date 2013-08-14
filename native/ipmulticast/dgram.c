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

int sock_dgram_send, sock_dgram_recv;

static char *recvbuf = NULL;
static char *sendbuf = NULL;

static int loop = MULTICAST_LOOP;

int init_dgram() {
	struct ip *ip;

	if (recvbuf != NULL) free(recvbuf);
	if (sendbuf != NULL) free(sendbuf);
	recvbuf = malloc(BUF_SIZE);
	sendbuf = malloc(BUF_SIZE);

	// create a raw socket
	if (((sock_dgram_send = socket(PF_INET, SOCK_RAW, IPPROTO_UDP)) < 0)
	 || ((sock_dgram_recv = socket(PF_INET, SOCK_RAW, IPPROTO_UDP)) < 0)) {
		log(LOG_ERR, errno, "Failed to create a raw socket. You may have to be the \"root\" user to have priviledge.");
		return -1;
	}

	k_hdr_include(sock_dgram_send, TRUE);
	k_set_sndbuf(sock_dgram_send, SEND_BUF_MAX_SIZE, SEND_BUF_MIN_SIZE);
	k_set_rcvbuf(sock_dgram_send, RECV_BUF_MAX_SIZE, RECV_BUF_MIN_SIZE);
	k_set_loop(sock_dgram_send, loop);

	k_hdr_include(sock_dgram_recv, TRUE);
	k_set_sndbuf(sock_dgram_recv, SEND_BUF_MAX_SIZE, SEND_BUF_MIN_SIZE);
	k_set_rcvbuf(sock_dgram_recv, RECV_BUF_MAX_SIZE, RECV_BUF_MIN_SIZE);
	k_set_loop(sock_dgram_recv, loop);

	// initialize IP header
	ip = (struct ip *)sendbuf;
	memset((void *)ip, 0, sizeof(struct ip));

	ip->ip_v = IPVERSION;
	ip->ip_hl = sizeof(struct ip) >> 2;
	//ip->ip_tos = IPTOS_MINCOST;
	ip->ip_off = htons(IP_DF);		// don't fragment
	ip->ip_ttl = DEFAULT_DGRAM_TTL;
	ip->ip_p = IPPROTO_UDP;
	ip->ip_sum = 0;		// kernel fills in

	return 0;
}


void stop_dgram() {
	if (sock_dgram_send > 0) {
		close(sock_dgram_send);
		sock_dgram_send = -1;
	}

	if (sock_dgram_recv > 0) {
		close(sock_dgram_recv);
		sock_dgram_recv = -1;
	}

	if (recvbuf != NULL)
		free(recvbuf);
	if (sendbuf != NULL)
		free(sendbuf);
}


int send_dgram(uint32_t srcaddr, uint16_t srcport, uint32_t destaddr, uint16_t destport, uint16_t id, uint8_t ttl, char *data, uint16_t datalen) {
	struct ip *ip;
	struct udphdr *udp;
	int ipheaderlen;
	int sendlen;
	int setloop = FALSE;
	struct sockaddr_in destsock;
	char *buf = sendbuf;

	ip = (struct ip *)buf;

	ipheaderlen = (ip->ip_hl << 2);

	if (ipheaderlen + sizeof(struct udphdr) + datalen > BUF_SIZE) {
		datalen = BUF_SIZE - (ipheaderlen + sizeof(struct udphdr));
		log(LOG_WARNING, 0, "Given data is too large: %d\n", datalen);
	}

	// prepare IP header
	ip->ip_len = ipheaderlen + sizeof(struct udphdr) + datalen;
	ip->ip_src.s_addr = srcaddr;
	ip->ip_dst.s_addr = destaddr;
	sendlen = ip->ip_len;
#if defined(RAW_OUTPUT_IS_RAW) || defined(OpenBSD)
	ip->ip_len = htons(ip->ip_len);
#endif
	ip->ip_id = id;
	ip->ip_ttl = ttl;

	// prepare UDP header
	udp = (struct udphdr *)(buf + ipheaderlen);
	udp->source = htons(srcport);
	udp->dest = htons(destport);
	udp->len = sizeof(struct udphdr) + datalen;
#if defined(RAW_OUTPUT_IS_RAW) || defined(OpenBSD)
	udp->len = htons(udp->len);
#endif

	// copy data
	if (data != NULL) {
		memcpy(((char *)udp) + sizeof(struct udphdr), data, datalen);
	}

	// calculate checksum
#ifdef CALCULATE_UDP_CHECKSUM
	{
		int add = 0;
		uint16_t i = 0;

		add += srcaddr >> 16;
		add += srcaddr & 0xffff;
		add += destaddr >> 16;
		add += destaddr & 0xffff;
		*(((uint8_t *)&i) + 1) = ip->ip_p;
		add += i;
		add += udp->len;

		udp->check = 0;
		udp->check = inet_cksum((uint16_t *)udp, sizeof(struct udphdr) + datalen, add);
	}
#else
	udp->check = 0;
#endif

	if (IN_MULTICAST(ntohl(destaddr))) {
		k_set_if(sock_dgram_send, srcaddr);
		if (destaddr == allhostsgroup) {
			setloop = TRUE;
			k_set_loop(sock_dgram_send, TRUE);
		}
	}

	memset((void *)&destsock, 0, sizeof(destsock));
	destsock.sin_family = PF_INET;
#ifdef HAVE_SA_LEN
	destsock.sin_len = sizeof(destsock);
#endif
	destsock.sin_addr.s_addr = destaddr;
	destsock.sin_port = destport;

	if (sendto(sock_dgram_send, sendbuf, sendlen, 0,
			(struct sockaddr *)&destsock, sizeof(destsock)) < 0) {
		log(LOG_ERR, errno, "Failed in sendto(2) to %s on %s.",
			inet_fmt(destaddr, s1),
			inet_fmt(srcaddr, s2));

		if (setloop) {
			k_set_loop(sock_dgram_send, loop);
		}

		return -1;
	}

	if (setloop) {
		k_set_loop(sock_dgram_send, loop);
	}

	return 0;
}


int recv_dgram(struct ip **ipp, struct udphdr **udpp, char *data, int *datalenp, int maxdatasize, int only_mcast) {
	uint32_t src, dest;
	struct ip *ip;
	struct udphdr *udp;
	int ipheaderlen, ipdatalen, udpdatalen;
	int recvlen;

	// receive
	while (TRUE) {
		socklen_t fromlen;

		recvlen = recvfrom(sock_dgram_recv, recvbuf, BUF_SIZE, 0, NULL, &fromlen);
		if (recvlen < 0) {
			if (errno != EINTR) {
				log(LOG_ERR, errno, "recv(2) returned -1.");
			}
			return -1;
		}

		if (recvlen < sizeof (struct ip)) {
			log(LOG_WARNING, 0, "Received packet is too short: %d", recvlen);
			continue;
		}

		ip = (struct ip *)recvbuf;
		src = ip->ip_src.s_addr;
		dest = ip->ip_dst.s_addr;

		if (only_mcast && !IN_MULTICAST(ntohl(dest))) {
			// ignore unicast message
			continue;
		}

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

		udp = (struct udphdr *)(recvbuf + ipheaderlen);
		udpdatalen = ipdatalen - sizeof(struct udphdr);

		if (ipp != NULL) *ipp = ip;

		if (udpp != NULL) *udpp = udp;

		if (datalenp != NULL) {
			*datalenp = udpdatalen;
		}

		if ((ipdatalen > 0) && (data != NULL)) {
			int len = MIN(udpdatalen, maxdatasize);
			memcpy(data, ((char *)udp) + sizeof(struct udphdr), len);
		}

		return 0;
	}
}

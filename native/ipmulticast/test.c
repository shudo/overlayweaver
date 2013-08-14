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

#include <stdio.h>
#include <stdlib.h>	// for exit(3)
#include "common.h"

#undef TEST_IGMP

static void start();

int main(int argc, char **argv) {
#ifdef TEST_IGMP
	if (init_igmp("eth0") < 0) {
		exit(1);
	}
#else
	init_dgram();
#endif

	init_vifs();

	start();

	return 0;
}

static void start() {
#ifdef TEST_IGMP
	send_igmp(selfaddr, allhostsgroup, IGMP_MEMBERSHIP_QUERY, 0, 0, NULL, 0);
#endif

	while (TRUE) {
		int recvLen;
		socklen_t fromLen;

#ifdef TEST_IGMP
		if (recv_igmp(NULL, NULL, NULL, NULL, 0) < 0) break;
#else
		if (recv_dgram(NULL, NULL, NULL, NULL, 0, TRUE) < 0) break;
#endif
	}
}

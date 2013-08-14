/*
 * Copyright 2006,2008 National Institute of Advanced Industrial Science
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

#include <jni.h>
#include "ow_ipmulticast_Native.h"
#include "common.h"


#define IOEXCEPTION_CLASS_NAME "java/io/IOException"
#define NATIVEVIF_CLASS_NAME "ow/ipmulticast/VirtualInterface$NativeVIF"
#define IGMPMessage_CLASS_NAME "ow/ipmulticast/IGMP$IGMPMessage"
#define MulticastMessage_CLASS_NAME "ow/ipmulticast/IPMulticast$MulticastMessage"


static jclass clazz_IOException = NULL;
static jfieldID field_NativeVIF_isRegisterVIF = NULL;
static jfieldID field_NativeVIF_localAddress = NULL;
static jfieldID field_NativeVIF_netmask = NULL;
static jfieldID field_NativeVIF_name = NULL;
static jfieldID field_NativeVIF_ifIndex = NULL;
static jfieldID field_IGMPMessage_src = NULL;
static jfieldID field_IGMPMessage_dest = NULL;
static jfieldID field_IGMPMessage_type = NULL;
static jfieldID field_IGMPMessage_code = NULL;
static jfieldID field_IGMPMessage_group = NULL;
static jfieldID field_IGMPMessage_data = NULL;
static jfieldID field_MulticastMessage_srcaddr = NULL;
static jfieldID field_MulticastMessage_srcport = NULL;
static jfieldID field_MulticastMessage_destaddr = NULL;
static jfieldID field_MulticastMessage_destport = NULL;
static jfieldID field_MulticastMessage_id = NULL;
static jfieldID field_MulticastMessage_ttl = NULL;
static jfieldID field_MulticastMessage_data = NULL;

static int initialized = FALSE;


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_initNative(JNIEnv *env, jclass clazz) {
	if (initialized) return;

	if (field_IGMPMessage_src == NULL) {
		jclass class_NativeVIF, class_IGMPMessage, class_MulticastMessage;

		clazz_IOException = (*env)->FindClass(env, IOEXCEPTION_CLASS_NAME);

		class_NativeVIF = (*env)->FindClass(env, NATIVEVIF_CLASS_NAME);
		field_NativeVIF_isRegisterVIF = (*env)->GetFieldID(env, class_NativeVIF, "isRegisterVIF", "Z");
		field_NativeVIF_localAddress = (*env)->GetFieldID(env, class_NativeVIF, "localAddress", "I");
		field_NativeVIF_netmask = (*env)->GetFieldID(env, class_NativeVIF, "netmask", "I");
		field_NativeVIF_name = (*env)->GetFieldID(env, class_NativeVIF, "name", "Ljava/lang/String;");
		field_NativeVIF_ifIndex = (*env)->GetFieldID(env, class_NativeVIF, "ifIndex", "I");

		class_IGMPMessage = (*env)->FindClass(env, IGMPMessage_CLASS_NAME);
		field_IGMPMessage_src = (*env)->GetFieldID(env, class_IGMPMessage, "src", "I");
		field_IGMPMessage_dest = (*env)->GetFieldID(env, class_IGMPMessage, "dest", "I");
		field_IGMPMessage_type = (*env)->GetFieldID(env, class_IGMPMessage, "type", "I");
		field_IGMPMessage_code = (*env)->GetFieldID(env, class_IGMPMessage, "code", "I");
		field_IGMPMessage_group = (*env)->GetFieldID(env, class_IGMPMessage, "group", "I");
		field_IGMPMessage_data = (*env)->GetFieldID(env, class_IGMPMessage, "data", "[B");

		class_MulticastMessage = (*env)->FindClass(env, MulticastMessage_CLASS_NAME);
		field_MulticastMessage_srcaddr = (*env)->GetFieldID(env, class_MulticastMessage, "srcaddr", "I");
		field_MulticastMessage_srcport = (*env)->GetFieldID(env, class_MulticastMessage, "srcport", "I");
		field_MulticastMessage_destaddr = (*env)->GetFieldID(env, class_MulticastMessage, "destaddr", "I");
		field_MulticastMessage_destport = (*env)->GetFieldID(env, class_MulticastMessage, "destport", "I");
		field_MulticastMessage_id = (*env)->GetFieldID(env, class_MulticastMessage, "id", "I");
		field_MulticastMessage_ttl = (*env)->GetFieldID(env, class_MulticastMessage, "ttl", "I");
		field_MulticastMessage_data = (*env)->GetFieldID(env, class_MulticastMessage, "data", "[B");
	}

	// initialize IGMP-related stuff
	if (init_igmp(NULL) < 0) {
		(*env)->ThrowNew(env, clazz_IOException,
			"Failed to create a raw socket for IGMP. It needs \"root\" proviledge.");
	}

	// initialize multicast-related stuff
	if (init_dgram() < 0) {
		(*env)->ThrowNew(env, clazz_IOException,
		"Failed to create a raw socket for multicast. It needs \"root\" priviledge.");
	}

	// initialize virtual interfaces
	init_vifs();

	initialized = TRUE;
}


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_stopNative(JNIEnv *env, jclass clazz) {
	stop_all_vifs();
	stop_igmp();

	initialized = FALSE;
}


JNIEXPORT jint JNICALL Java_ow_ipmulticast_Native_numberOfVIFs(JNIEnv *env, jclass clazz) {
	return (jint) /* vifi_t is unsigned short */ numvifs;
}


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_fillVIFs(JNIEnv *env, jclass clazz, jobjectArray nativeVIFArray) {
	jsize arraylen = (*env)->GetArrayLength(env, nativeVIFArray);
	jsize i;

	for (i = 0; i < arraylen; i++) {
		struct uvif *v = &uvifs[i];
		jobject nativeVIF, nameString;

		nameString = (*env)->NewStringUTF(env, v->uv_name);

		nativeVIF = (*env)->GetObjectArrayElement(env, nativeVIFArray, i);
		(*env)->SetBooleanField(env, nativeVIF, field_NativeVIF_isRegisterVIF, (jboolean) (v->uv_flags & VIFF_REGISTER));
		(*env)->SetIntField(env, nativeVIF, field_NativeVIF_localAddress, (jint) /*uint32_t*/ htonl(v->uv_lcl_addr));
		(*env)->SetIntField(env, nativeVIF, field_NativeVIF_netmask, (jint) /*uint32_t*/ htonl(v->uv_subnetmask));
		(*env)->SetObjectField(env, nativeVIF, field_NativeVIF_name, nameString);
		(*env)->SetIntField(env, nativeVIF, field_NativeVIF_ifIndex, (jint) /*int*/ v->uv_ifindex);
	}
}


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_receiveIGMP(JNIEnv *env, jclass clazz, jobject container) {
	struct ip *ip;
	struct igmp *igmp;
	char data[BUF_SIZE];
	int datalen = 0;
	jbyteArray jdata;

	if (recv_igmp(&ip, &igmp, data, &datalen, BUF_SIZE) < 0) {
		(*env)->ThrowNew(env, clazz_IOException, "recvfrom(2) returned -1.");
	}

	if (datalen >= BUF_SIZE) {
		log(LOG_WARNING, 0, "Buffer is possiblly too short.");

		datalen = BUF_SIZE;
	}

	(*env)->SetIntField(env, container, field_IGMPMessage_src, (jint)(ntohl(ip->ip_src.s_addr)));
	(*env)->SetIntField(env, container, field_IGMPMessage_dest, (jint)(ntohl(ip->ip_dst.s_addr)));
	(*env)->SetIntField(env, container, field_IGMPMessage_type, (jint)(igmp->igmp_type));
	(*env)->SetIntField(env, container, field_IGMPMessage_code, (jint)(igmp->igmp_code));
	(*env)->SetIntField(env, container, field_IGMPMessage_group, (jint)(ntohl(igmp->igmp_group.s_addr)));

	if (datalen > 0) {
		// jdata = new byte[datalen]; jdata[0..datale-1] = ...
		jdata = (*env)->NewByteArray(env, (jsize)datalen);
		(*env)->SetByteArrayRegion(env, jdata, (jsize)0, (jsize)datalen, (jbyte *)data);

		// container.data = jdata
		(*env)->SetObjectField(env, container, field_IGMPMessage_data, (jobject)jdata);
	}
}


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_sendIGMP(JNIEnv *env, jclass clazz,
		jint src, jint dest, jint type, jint code, jint group, jbyteArray jdata) {
	jsize datalen;
	jbyte *data;

	src = htonl(src);
	dest = htonl(dest);
	group = htonl(group);

	if (jdata != NULL) {
		datalen = (*env)->GetArrayLength(env, jdata);
		data = (*env)->GetByteArrayElements(env, jdata, NULL);
	}
	else {
		datalen = 0;
		data = NULL;
	}
#if 0
printf("send\n");
printf("  src:   %s\n", inet_fmt(src, s1));
printf("  dest:  %s\n", inet_fmt(dest, s1));
printf("  group: %s\n", inet_fmt(group, s1));
printf("  datalen: %d\n", datalen);
fflush(stdout);
#endif

	send_igmp((uint32_t)src, (uint32_t)dest, (int)type, (int)code, (uint32_t)group, (char *)data, (int)datalen);

	if (jdata != NULL) {
		(*env)->ReleaseByteArrayElements(env, jdata, data, JNI_ABORT);
	}
}


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_receiveMulticast(JNIEnv *env, jclass clazz, jobject container) {
	struct ip *ip;
	struct udphdr *udp;
	char data[BUF_SIZE];
	int datalen = 0;
	jbyteArray jdata;

	if (recv_dgram(&ip, &udp, data, &datalen, BUF_SIZE, TRUE) < 0) {
		(*env)->ThrowNew(env, clazz_IOException, "recvfrom(2) returned -1.");
	}

	if (datalen >= BUF_SIZE) {
		log(LOG_WARNING, 0, "Buffer is possiblly too short.");

		datalen = BUF_SIZE;
	}

#ifdef RAW_OUTPUT_IS_RAW
	ip->ip_src.s_addr = ntohl(ip->ip_src.s_addr);
	ip->ip_dst.s_addr = ntohl(ip->ip_dst.s_addr);
	udp->source = ntohs(udp->source);
	udp->dest = ntohs(udp->dest);
#endif
	(*env)->SetIntField(env, container, field_MulticastMessage_srcaddr, (jint)(ip->ip_src.s_addr));
	(*env)->SetIntField(env, container, field_MulticastMessage_srcport, (jint)(udp->source));
	(*env)->SetIntField(env, container, field_MulticastMessage_destaddr, (jint)(ip->ip_dst.s_addr));
	(*env)->SetIntField(env, container, field_MulticastMessage_destport, (jint)(udp->dest));
	(*env)->SetIntField(env, container, field_MulticastMessage_id, (jint)(ip->ip_id));
	(*env)->SetIntField(env, container, field_MulticastMessage_ttl, (jint)(ip->ip_ttl));

	if (datalen > 0) {
		// jdata = new byte[datalen]; jdata[0..datale-1] = ...
		jdata = (*env)->NewByteArray(env, (jsize)datalen);
		(*env)->SetByteArrayRegion(env, jdata, (jsize)0, (jsize)datalen, (jbyte *)data);

		// container.data = jdata
		(*env)->SetObjectField(env, container, field_MulticastMessage_data, (jobject)jdata);
	}
}


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_sendMulticast(JNIEnv *env, jclass clazz,
		jint srcaddr, jint srcport, jint destaddr, jint destport, jint id, jint ttl, jint datalen, jbyteArray jdata) {
	jsize datasize;
	jbyte *data;

	srcaddr = htonl(srcaddr);
	destaddr = htonl(destaddr);

	if (jdata != NULL) {
		datasize = (*env)->GetArrayLength(env, jdata);
		data = (*env)->GetByteArrayElements(env, jdata, NULL);
	}
	else {
		datasize = 0;
		data = NULL;
	}

	if (datalen > datasize) {
		datalen = datasize;
	}
#if 0
printf("send\n");
printf("  src:   %s\n", inet_fmt(srcaddr, s1));
printf("  dest:  %s:%d\n", inet_fmt(destaddr, s1), destport);
printf("  datasize: %d\n", datasize);
printf("  datalen:  %d\n", datalen);
fflush(stdout);
#endif

	send_dgram((uint32_t)srcaddr, (uint16_t)srcport, (uint32_t)destaddr, (uint16_t)destport, (uint16_t)id, (uint8_t)ttl, (char *)data, (int)datalen);

	if (jdata != NULL) {
		(*env)->ReleaseByteArrayElements(env, jdata, data, JNI_ABORT);
	}
}


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_joinGroup(JNIEnv *env, jclass clazz,
		jint group, jint ifLocalAddr, jint ifIndex) {
	struct uvif u;

	u.uv_ifindex = (int)ifIndex;
	u.uv_lcl_addr = (uint32_t)htonl(ifLocalAddr);

	k_join(sock_dgram_send, (uint32_t)htonl(group), &u);
}


JNIEXPORT void JNICALL Java_ow_ipmulticast_Native_leaveGroup(JNIEnv *env, jclass clazz,
		jint group, jint ifLocalAddr, jint ifIndex) {
	struct uvif u;

	u.uv_ifindex = (int)ifIndex;
	u.uv_lcl_addr = (uint32_t)htonl(ifLocalAddr);

	k_leave(sock_dgram_send, (uint32_t)htonl(group), &u);
}

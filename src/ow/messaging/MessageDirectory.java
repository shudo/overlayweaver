/*
 * Copyright 2011 Kazuyuki Shudo, and contributors.
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

package ow.messaging;

import java.awt.Color;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.util.ClassProcessor;
import ow.util.ClassTraverser;

public final class MessageDirectory {
	private final static Logger logger = Logger.getLogger("messaging");

	public final static Color DEFAULT_COLOR = Color.GRAY;

	private final static Map<Integer,String> nameTable = new HashMap<Integer,String>();
	private final static Map<Class<? extends Message>,Integer> classToTagTable = new HashMap<Class<? extends Message>,Integer>();
	private final static Map<Integer,Class<? extends Message>> tagToClassTable = new HashMap<Integer,Class<? extends Message>>();
	private final static Map<Integer,Boolean> toBeReportedTable = new HashMap<Integer,Boolean>();
	private final static Map<Integer,Color> colorTable = new HashMap<Integer,Color>();

	// for message digest
	private static MessageDigest md = null;
	private final static String mdAlgoName = "SHA1";

	static {
		// for message digest
		try {
			md = MessageDigest.getInstance(mdAlgoName);
		}
		catch (NoSuchAlgorithmException e) { /* NOTREACHED */ }

		// register messages
		Class[] rootClasses = {
			ow.routing.RoutingServiceFactory.class,
			ow.routing.RoutingAlgorithmFactory.class,
			ow.dht.DHTFactory.class,
			ow.mcast.McastFactory.class,
			ow.stat.StatFactory.class
		};
		MessageDirectory.registerMessageReferredBy(rootClasses);
	}

	private static void registerMessageReferredBy(Class[] clazzes) {
		ClassProcessor proc = new ClassProcessor() {
			public void process(String className) {
				if (className.endsWith("Message")) {
					try {
						registerMessage(Class.forName(className));
					}
					catch (ClassNotFoundException e) { /* ignore */ }
				}
			}
		};

		String[] classNameArray = new String[clazzes.length];
		int i = 0;
		for (Class clazz: clazzes) {
			classNameArray[i++] = clazz.getName();
		}

		ClassTraverser traverser = new ClassTraverser("^ow\\.", proc);
		traverser.traversal(classNameArray);
	}

	private static void registerMessage(Class clazz) {
		if (classToTagTable.containsKey(clazz)
				|| !(Message.class.isAssignableFrom(clazz))
				|| Modifier.isAbstract(clazz.getModifiers())) return;

		String name = null;
		boolean toBeReported = false;
		Color color = null;

		try {
			Field f = clazz.getDeclaredField("NAME");
			name = (String)f.get(clazz);
		}
		catch (Exception e) { /* ignore */ }

		try {
			Field f = clazz.getDeclaredField("TO_BE_REPORTED");
			toBeReported = f.getBoolean(clazz);
		}
		catch (Exception e) { /* ignore */ }

		try {
			Field f = clazz.getDeclaredField("COLOR");
			color = (Color)f.get(clazz);
		}
		catch (Exception e) { /* ignore */ }
		if (color == null) color = DEFAULT_COLOR;

		int tag = getSHA1BasedIntFromString(name);
		tag = (tag ^ (tag >>> 8) ^ (tag >>> 16) ^ (tag >>> 24)) & 0x7f;	// 7 bit

		int repeat = 10;
		while (true) {
			String existingName = nameTable.get(tag);
			if (existingName == null) break;

			logger.log(Level.INFO, "A tag " + tag + " was duplicated for message " + name + " and " + existingName);
			if (repeat-- <= 0) System.exit(1);

			tag = (tag + 1) & 0x7f;	// 7 bit
		}
//System.out.println("message: " + tag + ", " + clazz.getName());

		synchronized (MessageDirectory.class) {
			nameTable.put(tag, name);
			tagToClassTable.put(tag, clazz);
			classToTagTable.put(clazz, tag);
			toBeReportedTable.put(tag, toBeReported);
			colorTable.put(tag, color);
		}
	}

	private static int getSHA1BasedIntFromString(String input) {
		byte[] bytes = null;
		try {
			bytes = input.getBytes("UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			// NOTREACHED
		}

		byte[] value;
		synchronized (md) {
			value = md.digest(bytes);
		}

		int ret = 0;
		int i = 0;
		for (byte b: value) {
			ret ^= (b & 0xff) << (3 - i);
			if (++i > 3) i = 0;
		}

		return ret;
	}

	public static Class<? extends Message> getClassByTag(int tag) { return tagToClassTable.get(tag); }
	public static int getTagByClass(Class<? extends Message> c) { return classToTagTable.get(c); }
	public static String getName(int tag) { return nameTable.get(tag); }
	static boolean getToBeReported(int tag) { return toBeReportedTable.get(tag); }
	public static Color getColor(int tag) { return colorTable.get(tag); }
}

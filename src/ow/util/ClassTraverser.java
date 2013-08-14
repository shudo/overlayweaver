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

package ow.util;

import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.org.apache.bcel.internal.classfile.ClassParser;
import com.sun.org.apache.bcel.internal.classfile.Constant;
import com.sun.org.apache.bcel.internal.classfile.ConstantClass;
import com.sun.org.apache.bcel.internal.classfile.ConstantPool;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;

/**
 * An utility to traverse class dependency.
 */
public final class ClassTraverser {
	private final Pattern pattern;
	private final ClassProcessor proc;

	public ClassTraverser(String regex, ClassProcessor processor) {
		this.pattern = Pattern.compile(regex);
		this.proc = processor;
	}

	public void traversal(String fullyQualifiedClassName) {
		this.traversal(fullyQualifiedClassName, this.getClass().getClassLoader());
	}

	public void traversal(String fullyQualifiedClassName, ClassLoader classLoader) {
		String[] classNameArray = new String[1];
		classNameArray[0] = fullyQualifiedClassName;

		this.traversal(classNameArray, classLoader);
	}

	public void traversal(String[] fullyQualifiedClassNames) {
		this.traversal(fullyQualifiedClassNames, this.getClass().getClassLoader());
	}

	public void traversal(String[] fullyQualifiedClassNames, ClassLoader classLoader) {
		Queue<String> classQueue = new LinkedList<String>();
		Set<String> queuedClasses = new HashSet<String>();

		for (String className: fullyQualifiedClassNames) {
			this.canonicalizeAndOffer(className, classQueue, queuedClasses);
		}

		while (!classQueue.isEmpty()) {
			String className = classQueue.poll();

			this.proc.process(className);

			// analyze dependency with BCEL
			String path;
			InputStream in;
			path = className
				.replace(".", System.getProperty("file.separator"))
				.concat(".class");
			in = classLoader.getResourceAsStream(path);
			if (in == null) {
				path = className.replace(".", "/").concat(".class");
				in = classLoader.getResourceAsStream(path);
				if (in == null) continue;
			}

			JavaClass javaClass;
			try {
				javaClass = (new ClassParser(in, path)).parse();
			}
			catch (Exception e) { continue; }

			ConstantPool cp = javaClass.getConstantPool();
			for (Constant c: cp.getConstantPool()) {
				if (c instanceof ConstantClass) {
					String cName = (String)((ConstantClass)c).getConstantValue(cp);
					cName = cName.replace("/", ".");
					this.canonicalizeAndOffer(cName, classQueue, queuedClasses);
				}
			}
		}
	}

	private void canonicalizeAndOffer(String className,
			Queue<String> queue, Set<String> queuedClasses) {
		if (queuedClasses.contains(className)) return;

		Matcher m = this.pattern.matcher(className);
		if (!m.find()) return;

		queuedClasses.add(className);

		queue.offer(className);
	}
}

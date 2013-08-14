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

package ow.directory;

import java.io.File;

public class FileTest {
	public final static String COMMAND = "java FileTest";

	private static void usage(String command) {
		System.err.print("usage: ");
		System.err.print(command);
		System.err.println(" <filename>");
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			usage(COMMAND);
			System.exit(1);
		}

		File f = new File(args[0]);
		System.out.println(f);
		f.createNewFile();
		f.renameTo(new File(args[0] + ".tmp"));
		System.out.println(f);
	}
}

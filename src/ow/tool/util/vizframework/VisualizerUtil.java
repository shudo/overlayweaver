/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.tool.util.vizframework;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JOptionPane;

class VisualizerUtil {
	public final static String CREDIT =
		"Overlay Visualizer for Overlay Weaver\n" +
		"Copyright 2006-2007 Kazuyuki Shudo, National Institute of Advanced Industrial Science and Technology (AIST), and contributors";

	public final static String[] SUPPORTED_SCALES = {
		"0.1", "0.25", "0.5", "0.75",
		"1.0",
		"1.25", "1.5", "2.0", "2.5", "3.0", "3.5", "4.0"
	};

	public final static String GEOMETRY_MANAGER_PKG = "geom";

	public final static String[][] GEOMETRY_MANAGERS = {
		{ "CircleGeometryManager", "Circle" },
		{ "VortexGeometryManager", "Vortex" },
		{ "StraightLineGeometryManager", "Straight Line" },
		{ "WavingLineGeometryManager", "Waving Line" },
		{ "GridGeometryManager", "Grid" }
	};

	public final static String[][] LOOK_AND_FEELS = {
		{ "javax.swing.plaf.metal.MetalLookAndFeel", "Metal" },
		{ "com.sun.java.swing.plaf.gtk.GTKLookAndFeel", "GTK" },
		{ "com.sun.java.swing.plaf.motif.MotifLookAndFeel", "Motif" },
		{ "com.sun.java.swing.plaf.windows.WindowsLookAndFeel", "Windows" },
		{ "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel", "Windows Classic" }
		// { "sun.awt.X11.XAWTLookAndFeel", "XAWT" }
	};

	public final static Color[] CONNECTION_COLORS = {
		Color.MAGENTA,
		Color.BLUE,
		Color.GREEN,
		Color.ORANGE
	};

	public static void showMessage(Component c, String msg) {
		JOptionPane.showMessageDialog(c, msg);
	}

	public static void fatal(String msg) {
		System.err.print("fatal: ");
		System.err.println(msg);

		VisualizerUtil.exit(1);
	}

	public static void quit(Component c, int status) {
		int quitp = JOptionPane.showConfirmDialog(c, "Quit ?");
		if (quitp == JOptionPane.YES_OPTION)
			VisualizerUtil.exit(status);
	}

	public static void exit(int status) {
		System.exit(status);
	}
}

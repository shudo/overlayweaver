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

package ow.tool.util.vizframework;

import java.awt.BasicStroke;
import java.awt.Image;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.net.URL;

import javax.swing.ImageIcon;

import ow.id.ID;

class ImageManager {
	public final static int NUM_NODE_IMAGES = 3;
	public final static String[] NODE_IMAGE_NAMES = {
		"resources/node0.gif",
		"resources/node1.gif",
		"resources/node2.gif"
	};
	public final static int NUM_MARK_IMAGES = 1;
	public final static String[] MARK_IMAGE_NAMES = {
		"resources/mark0.gif"
	};

	private double scale, imageScale;

	GeometryManager geometryManager;

	// original images
	private final Image[] origNodeImage;
	private final int origNodeWidth[], origNodeHeight[];

	private final Image[] origMarkImage;
	private final int origMarkWidth[], origMarkHeight[];

	// scaled images
	private Image[] nodeImage;
	private int nodeWidth[], nodeHeight[];

	private Image[] markImage;
	private int markWidth[], markHeight[];

	// stroke for lines
	private Stroke strokeForMessaging;
	private Stroke strokeForConnection;

	protected ImageManager() {
		// load images
		URL iconURL;
		ClassLoader cl = ImageManager.class.getClassLoader();
		ImageIcon icon = null;

		// load node image
		this.origNodeImage = new Image[NUM_NODE_IMAGES];
		this.origNodeWidth = new int[NUM_NODE_IMAGES];
		this.origNodeHeight = new int[NUM_NODE_IMAGES];
		this.nodeImage = new Image[NUM_NODE_IMAGES];
		this.nodeWidth = new int[NUM_NODE_IMAGES];
		this.nodeHeight = new int[NUM_NODE_IMAGES];

		for (int i = 0; i < NUM_NODE_IMAGES; i++) {
			iconURL = cl.getResource(NODE_IMAGE_NAMES[i]);
			if (iconURL == null) VisualizerUtil.fatal(NODE_IMAGE_NAMES[i] + " not found.");
			icon = new ImageIcon(iconURL);
			this.origNodeImage[i] = icon.getImage();
			this.origNodeWidth[i] = icon.getIconWidth();
			this.origNodeHeight[i] = icon.getIconHeight();
		}

		// load mark images
		this.origMarkImage = new Image[NUM_MARK_IMAGES];
		this.origMarkWidth = new int[NUM_MARK_IMAGES];
		this.origMarkHeight = new int [NUM_MARK_IMAGES];
		this.markImage = new Image[NUM_MARK_IMAGES];
		this.markWidth = new int[NUM_MARK_IMAGES];
		this.markHeight = new int [NUM_MARK_IMAGES];

		for (int i = 0; i < NUM_MARK_IMAGES; i++) { 
			iconURL = cl.getResource(MARK_IMAGE_NAMES[i]);
			if (iconURL == null) VisualizerUtil.fatal(MARK_IMAGE_NAMES[i] + " not found.");
			icon = new ImageIcon(iconURL);
			this.origMarkImage[i] = icon.getImage();
			this.origMarkWidth[i] = icon.getIconWidth();
			this.origMarkHeight[i] = icon.getIconHeight();
		}

		// prepare 1.0 scaled images
		this.setScale(Visualizer.DEFAULT_SCALE);
	}

	public GeometryManager getGeometryManager() {
		return this.geometryManager;
	}

	public synchronized GeometryManager setGeometryManager(GeometryManager gm) {
		GeometryManager old = this.geometryManager;
		this.geometryManager = gm;
		return old;
	}

	public synchronized void setScale(double scale) {
		this.setImageScale(scale);

		if (scale <= 0.0 || scale == this.scale)  return;

		this.scale = scale;

		if (this.geometryManager != null) {
			this.geometryManager.setScale(this.scale);
		}
	}

	public synchronized void setImageScale(double scale) {
		if (scale <= 0.0 || scale == this.imageScale)  return;

		this.imageScale = scale;

		for (int i = 0; i < origNodeImage.length; i++) {
			nodeWidth[i] = (int)(origNodeWidth[i] * scale);
			nodeHeight[i] = (int)(origNodeHeight[i] * scale);
			nodeImage[i] = origNodeImage[i].getScaledInstance(
					nodeWidth[i], nodeHeight[i], Visualizer.IMAGE_SCALING_ALGORITHM);
		}

		for (int i = 0; i < origMarkImage.length; i++) {
			markWidth[i] = (int)(origMarkWidth[i] * scale);
			markHeight[i] = (int)(origMarkHeight[i] * scale);
			markImage[i] = origMarkImage[i].getScaledInstance(
					markWidth[i], markHeight[i], Visualizer.IMAGE_SCALING_ALGORITHM);
		}

		float lineWidth;

		lineWidth = (float)(Visualizer.MESSAGING_LINE_WIDTH * scale);
		if (lineWidth < 1.0f) lineWidth = 1.0f;
		strokeForMessaging = new BasicStroke(lineWidth);

		lineWidth = (float)(Visualizer.CONNECTION_LINE_WIDTH * scale);
		if (lineWidth < 1.0f) lineWidth = 1.0f;
		strokeForConnection = new BasicStroke(lineWidth);
	}

	public Point getNodeLocation(ID id, int type) {
		Point2D p = this.geometryManager.getNodePoint2D(id);

		double x = p.getX();
		double y = p.getY();

		x -= nodeWidth[type] / 2;
		y -= nodeHeight[type] / 2;

		return new Point((int)x, (int)y);
	}

	public Point getMarkLocation(ID id, int type) {
		Point2D p = this.geometryManager.getNodePoint2D(id);

		double x = p.getX();
		double y = p.getY();

		x -= markWidth[type] / 2;
		y -= markHeight[type] / 2;

		return new Point((int)x, (int)y);
	}

	//
	// Accessors
	//

	public Image getNodeImage(int type) {
		Image ret;
		try {
			ret = this.nodeImage[type];
		}
		catch (ArrayIndexOutOfBoundsException e) {
			ret = this.nodeImage[0];
		}
		return ret;
	}
	public int getNodeWidth(int type) { return this.nodeWidth[type]; }
	public int getNodeHeight(int type) { return this.nodeHeight[type]; }

	public Image getMarkImage(int type) {
		Image ret;
		try {
			ret = this.markImage[type];
		}
		catch (ArrayIndexOutOfBoundsException e) {
			ret = this.markImage[0];
		}
		return ret;
	}
	public int getMarkWidth(int type) { return this.markWidth[type]; }
	public int getMarkHeight(int type) { return this.markHeight[type]; }

	public Stroke getStrokeForMessaging() { return this.strokeForMessaging; }
	public Stroke getStrokeForConnection() { return this.strokeForConnection; }
}

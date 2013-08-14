/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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

package ow.tool.util.vizframework.geom;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.math.BigInteger;
import java.util.Map;

import ow.id.ID;
import ow.tool.util.vizframework.GeometryManager;
import ow.tool.util.vizframework.IDSpacePanel;
import ow.tool.util.vizframework.Visualizer;
import ow.util.Timer;

public abstract class AbstractGeometryManager implements GeometryManager {
	int idSizeInBit;
	double idSpaceSize;

	private int numOfNodes;
	private Map<ID,Integer> nodeOrderMap;

	// scale
	private double scale;

	// incline parameters
	private double inclineX = 0.0;
	private double inclineY = 0.0;
	double inclineAngle;
	double inclineScale;
	private double inclineDegree;

	private double momentX;
	private double momentY;

	// original size
	private final int origIDSpaceWidth = 500;
	private final int origIDSpaceHeight = 500;

	// scaled size
	private int idSpaceWidth;
	private int idSpaceHeight;

	protected AbstractGeometryManager(int idSizeInBit) {
		this.setIDSizeInBit(idSizeInBit);

		this.setScale(Visualizer.DEFAULT_SCALE);
	}

	public synchronized int getIDSizeInBit() {
		return this.idSizeInBit;
	}

	public synchronized void setIDSizeInBit(int idSizeInBit) {
		this.idSizeInBit = idSizeInBit;
		this.idSpaceSize = BigInteger.ONE.shiftLeft(this.idSizeInBit).doubleValue();
	}

	public synchronized int getNumOfNodes() {
		return this.numOfNodes;
	}

	public synchronized void setNumOfNodes(int num) {
		this.numOfNodes = num;
	}

	public synchronized Map<ID,Integer> getNodeOrderMap() {
		return this.nodeOrderMap;
	}

	public synchronized void setNodeOrderMap(Map<ID,Integer> map) {
		this.nodeOrderMap = map;
	}

	public synchronized void setScale(double scale) {
		if (scale <= 0 || scale == this.scale)  return;

		this.scale = scale;

		this.inclineX *= scale;
		this.inclineY *= scale;

		this.idSpaceWidth = (int)(this.origIDSpaceWidth * scale);
		this.idSpaceHeight = (int)(this.origIDSpaceHeight * scale);

		this.atanInclineXY();
	}

	public double getScale() { return this.scale; }

	public void addIncline(double x, double y) {
		this.inclineX += x;
		this.inclineY += y;

		this.momentX = x;
		this.momentY = y;

		this.atanInclineXY();

		this.inclineChangedTime = Timer.currentTimeMillis();
	}

	public void resetIncline() {
		this.inclineX = 0;
		this.inclineY = 0;

		this.atanInclineXY();

		this.stopMoment();
	}

	public void normalizeIncline() {
		double radius = this.idSpaceWidth * (this.inclineDegree / (Math.PI));

		this.inclineX = radius * Math.sin(this.inclineAngle);
		this.inclineY = radius * - Math.cos(this.inclineAngle);
	}

	private long inclineChangedTime;
	private MomentRunner momentRunner;

	public void startMoment(IDSpacePanel panel) {
		long curTime = Timer.currentTimeMillis();
		if (curTime - this.inclineChangedTime > Visualizer.MOMENT_EXPIRATION) {
			return;
		}

		if ((int)this.momentX == 0 && (int)this.momentY == 0) {
			this.momentX = 0.0;
			this.momentY = 0.0;

			return;
		}

		this.momentRunner = new MomentRunner(panel, 100L);
		Thread t = new Thread(this.momentRunner);
		t.setName("MomentRunner in Overlay Visualizer");
		t.setDaemon(true);
		t.start();
	}

	public void stopMoment() {
		this.momentX = 0;
		this.momentY = 0;

		if (this.momentRunner != null) {
			this.momentRunner.stop();
			this.momentRunner = null;
		}
	}

	private void atanInclineXY() {
		this.inclineAngle = Math.atan2(this.inclineY, this.inclineX) + 0.5 * Math.PI;

		double inclineAbs = Math.sqrt(this.inclineX * this.inclineX + this.inclineY * this.inclineY);
		this.inclineDegree = Math.PI * (inclineAbs / this.idSpaceWidth);
		this.inclineScale = Math.cos(this.inclineDegree);
	}

	private class MomentRunner implements Runnable {
		IDSpacePanel panel;
		long interval;
		private boolean stopped = false;

		MomentRunner(IDSpacePanel panel, long interval) {
			this.panel = panel;
			this.interval = interval;
		}

		void stop() { this.stopped = true; }

		public void run() {
			while (true) {
				try {
					Thread.sleep(this.interval);
				}
				catch (InterruptedException e) {
					this.stopped = true;
				}

				if (this.stopped) { break; }

				addIncline(momentX, momentY);

				this.panel.resetLocation();
			}
		}
	}

	public abstract Shape getShapeForMessage(ID src, ID dest);

	public abstract Shape getShapeForConnection(ID from, ID to);

	public abstract Point2D getNodePoint2D(ID id);

	double getAngle(double id) {
		double ratio = id / this.idSpaceSize;
		double angle = 2.0 * Math.PI * ratio;

		return angle;
	}

	double getCenterX() { return idSpaceWidth / 2.0; }
	double getCenterY() { return idSpaceHeight / 2.0; }

	public int getIDSpaceWidth() { return this.idSpaceWidth; }
	public int getIDSpaceHeight() { return this.idSpaceHeight; }

	protected Point2D getRotatedPointByRectangular(double x, double y) {
		double angle = Math.atan2(x, -y);
		double radius = Math.sqrt(x * x + y * y);

		return getRotatedPointByPolar(angle, radius);
	}

	protected Point2D getRotatedPointByPolar(double angle, double radius) {
		double biasedAngle = angle - this.inclineAngle;
		double biasedX = radius * Math.sin(biasedAngle);
		double biasedY = radius * - Math.cos(biasedAngle) * this.inclineScale;
		double biasedAbs = Math.sqrt(biasedX * biasedX + biasedY * biasedY);
		biasedAngle = Math.atan2(biasedY, biasedX) + 0.5 * Math.PI + this.inclineAngle;

		double x = biasedAbs * Math.sin(biasedAngle);
		double y = biasedAbs * - Math.cos(biasedAngle);

		x += this.getCenterX();
		y += this.getCenterY();

		return new Point2D.Double(x, y);
	}
}

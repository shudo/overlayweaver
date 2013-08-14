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

package ow.tool.util.vizframework.geom;

import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Map;

import ow.id.ID;
import ow.routing.RoutingAlgorithmConfiguration;

public class GridGeometryManager extends AbstractGeometryManager {
	private final static int DEFAULT_SIDE_LENGTH = 420;

	private int numOfNodes;
	private int sideNodes;
	private double sideLength;
	private double halfSideLength;

	public GridGeometryManager() {
		this(RoutingAlgorithmConfiguration.DEFAULT_ID_SIZE * 8);
	}

	private GridGeometryManager(int idSizeInBit) {
		super(idSizeInBit);

		this.sideLength = DEFAULT_SIDE_LENGTH;
		this.halfSideLength = this.sideLength * 0.5;
	}

	public synchronized void setNumOfNodes(int num) {
		// overrides AbstractGeometryManager#setNumOfNodes(int num)
		super.setNumOfNodes(num);
		this.numOfNodes = num;

		this.sideNodes = 1 + (int)Math.sqrt((double)(this.numOfNodes - 1));
	}

	public synchronized void setScale(double scale) {
		super.setScale(scale);

		this.sideLength = DEFAULT_SIDE_LENGTH * scale;
		this.halfSideLength = this.sideLength * 0.5;
	}

	public Shape getShapeForMessage(ID src, ID dest) {
		Point2D srcPoint = this.getNodePoint2D(src);
		Point2D destPoint = this.getNodePoint2D(dest);

		return new Line2D.Double(srcPoint, destPoint);
	}

	public Shape getShapeForConnection(ID from, ID to) {
		Point2D srcPoint = this.getNodePoint2D(from);
		Point2D destPoint = this.getNodePoint2D(to);

		return new Line2D.Double(srcPoint, destPoint);
	}

	public Point2D getNodePoint2D(ID id) {
		double x, y;

		Map<ID,Integer> nodeOrderMap = this.getNodeOrderMap();
		Integer orderInteger = nodeOrderMap.get(id);

		int order;
		if (orderInteger != null) {
			order = (int)orderInteger;

			int xIndex = order % this.sideNodes;
			int yIndex = order / this.sideNodes;

			x = this.sideLength * xIndex / (this.sideNodes - 1);
			y = this.sideLength * yIndex / (this.sideNodes - 1);
		}
		else {
			double ratio = id.toBigInteger().doubleValue() / this.idSpaceSize;

			double d = ratio * this.sideNodes;
			int yIndex = (int)d;
			double xRatio = (d - (double)yIndex);

			x = this.sideLength * xRatio;
			y = this.sideLength * yIndex / (this.sideNodes - 1);
		}

		x = x - this.halfSideLength;
		y = y - this.halfSideLength;

		return this.getRotatedPointByRectangular(x, y);
	}
}

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
import java.awt.geom.QuadCurve2D;

import ow.routing.RoutingAlgorithmConfiguration;
import ow.tool.util.vizframework.LineType;
import ow.tool.util.vizframework.Visualizer;

public abstract class AbstractCircleGeometryManager extends AbstractGeometryManager {
	// original size
	private final static double DEFAULT_ID_CIRCLE_RADIUS = 230.0;

	// scaled size
	private double idCircleRadius;

	public AbstractCircleGeometryManager() {
		this(RoutingAlgorithmConfiguration.DEFAULT_ID_SIZE * 8);
	}

	private AbstractCircleGeometryManager(int idSizeInBit) {
		super(idSizeInBit);
	}

	public synchronized void setScale(double scale) {
		super.setScale(scale);

		this.idCircleRadius = DEFAULT_ID_CIRCLE_RADIUS * scale;
	}

	Shape getShapeForLineBetweenTwoPoints(
			double fromDbl, double fromHeight, double toDbl, double toHeight,
			double ctrlHeight, double ctrlPhase) {
		Shape shape;

		if (Visualizer.LINE_TYPE == LineType.BEZIER_CURVE) {
			double distance = toDbl - fromDbl;
			if (Math.abs(distance) > (idSpaceSize * 0.5)) {
				if (distance > 0)
					distance -= idSpaceSize;
				else
					distance += idSpaceSize;
			}
			double ctrlDbl = fromDbl + (distance * ctrlPhase);

			Point2D fromPoint = this.getNodePoint2D(fromDbl, fromHeight);
			Point2D toPoint = this.getNodePoint2D(toDbl, toHeight);
			Point2D ctrlPoint = this.getNodePoint2D(ctrlDbl, (fromHeight * toHeight) * 0.5 * ctrlHeight);

			shape = new QuadCurve2D.Double(
					fromPoint.getX(), fromPoint.getY(),
					ctrlPoint.getX(), ctrlPoint.getY(),
					toPoint.getX(), toPoint.getY());
		}
		else {	// STRAIGHT
			Point2D fromPoint = this.getNodePoint2D(fromDbl, 1.0);
			Point2D toPoint = this.getNodePoint2D(toDbl, 1.0);

			shape = new Line2D.Double(fromPoint, toPoint);
		}

		return shape;
	}

	Point2D getNodePoint2D(double id, double radiusRatio) {
		double angle = this.getAngle(id);
		double radius = this.idCircleRadius * radiusRatio;

		return this.getRotatedPointByPolar(angle, radius);
	}
}

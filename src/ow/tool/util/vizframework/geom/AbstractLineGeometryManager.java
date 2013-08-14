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
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.math.BigInteger;

import ow.id.ID;
import ow.routing.RoutingAlgorithmConfiguration;

public abstract class AbstractLineGeometryManager extends AbstractGeometryManager {
	// original size
	private final static double DEFAULT_HALF_LINE_LENGTH = 240.0;

	double idSpaceSize;
	private double halfIDSpaceSize;

	// scaled size
	private double halfLineLength;

	protected AbstractLineGeometryManager() {
		this(RoutingAlgorithmConfiguration.DEFAULT_ID_SIZE * 8);
	}

	protected AbstractLineGeometryManager(int idSizeInBit) {
		super(idSizeInBit);
	}

	public void setIDSizeInBit(int idSizeInBit) {
		super.setIDSizeInBit(idSizeInBit);

		this.idSpaceSize = BigInteger.ONE.shiftLeft(idSizeInBit).doubleValue();
		this.halfIDSpaceSize = BigInteger.ONE.shiftLeft(idSizeInBit - 1).doubleValue();
	}

	public synchronized void setScale(double scale) {
		super.setScale(scale);

		this.halfLineLength = DEFAULT_HALF_LINE_LENGTH * scale;
	}

	Shape getShapeForMessage(ID src, ID dest, double heightBase) {
		Point2D srcPoint = this.getNodePoint2D(src);
		Point2D destPoint = this.getNodePoint2D(dest);

		double srcDbl = src.toBigInteger().doubleValue();
		double destDbl = dest.toBigInteger().doubleValue();

		// determin height of control point for a bezier curve
		double ctrlPointHeight = (destDbl - srcDbl) / this.idSpaceSize * 1.5;
		if (ctrlPointHeight >= 0) {
			ctrlPointHeight += 0.5;
		}
		else {
			ctrlPointHeight -= 0.5;
		}

		Point2D ctrlPoint = this.getNodePoint2D(
				(srcDbl + destDbl) / 2.0,
				heightBase + ctrlPointHeight);

		return new QuadCurve2D.Double(
				srcPoint.getX(), srcPoint.getY(),
				ctrlPoint.getX(), ctrlPoint.getY(),
				destPoint.getX(), destPoint.getY());
	}

	Shape getShapeForConnection(ID from, ID to, double heightBase) {
		Point2D fromPoint = this.getNodePoint2D(from);
		Point2D toPoint = this.getNodePoint2D(to);

		double fromDbl = from.toBigInteger().doubleValue();
		double toDbl = to.toBigInteger().doubleValue();

		// determin height of control point for a bezier curve
		double ctrlPointHeight = (fromDbl - toDbl) / this.idSpaceSize * 1.3;
		if (ctrlPointHeight >= 0) {
			ctrlPointHeight += 0.4;
		}
		else {
			ctrlPointHeight -= 0.4;
		}

		Point2D ctrlPoint = this.getNodePoint2D(
				(fromDbl + toDbl) / 2.0,
				heightBase + ctrlPointHeight);

		return new QuadCurve2D.Double(
				fromPoint.getX(), fromPoint.getY(),
				ctrlPoint.getX(), ctrlPoint.getY(),
				toPoint.getX(), toPoint.getY());
	}

	Point2D getNodePoint2D(double idDbl, double height) {
		double angle;

		double radius = (idDbl - this.halfIDSpaceSize) / this.halfIDSpaceSize;
			// from -1.0 to 1.0

		angle = Math.atan2(radius, height);
		radius = Math.sqrt(radius * radius + height * height);

		radius *= this.halfLineLength;

		return this.getRotatedPointByPolar(angle, radius);
	}
}

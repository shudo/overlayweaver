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

import ow.id.ID;

public class CircleGeometryManager extends AbstractCircleGeometryManager {
	public Shape getShapeForMessage(ID src, ID dest) {
		double srcDbl = src.toBigInteger().doubleValue();
		double destDbl = dest.toBigInteger().doubleValue();

		return this.getShapeForLineBetweenTwoPoints(srcDbl, 1.0, destDbl, 1.0, 0.0, 0.5);
	}

	public Shape getShapeForConnection(ID from, ID to) {
		double fromDbl = from.toBigInteger().doubleValue();
		double toDbl = to.toBigInteger().doubleValue();

		return this.getShapeForLineBetweenTwoPoints(fromDbl, 1.0, toDbl, 1.0, 0.7, 1.1);
	}

	public Point2D getNodePoint2D(ID id) {
		return this.getNodePoint2D(id.toBigInteger().doubleValue(), 1.0);
	}
}

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

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.util.Map;

import ow.id.ID;

public interface GeometryManager {
	public int getIDSizeInBit();
	public void setIDSizeInBit(int idSizeInBit);

	public int getNumOfNodes();
	public void setNumOfNodes(int num);
	public Map<ID,Integer> getNodeOrderMap();
	public void setNodeOrderMap(Map<ID,Integer> map);

	public void setScale(double scale);
	public double getScale();

	public void addIncline(double x, double y);
	public void resetIncline();
	public void normalizeIncline();
	public void startMoment(IDSpacePanel panel);
	public void stopMoment();

	public Shape getShapeForMessage(ID src, ID dest);
	public Shape getShapeForConnection(ID from, ID to);

	public Point2D getNodePoint2D(ID id);

	public int getIDSpaceWidth();
	public int getIDSpaceHeight();
}

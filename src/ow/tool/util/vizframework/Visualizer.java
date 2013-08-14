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

import java.awt.Image;

import ow.id.ID;
import ow.messaging.MessagingAddress;
import ow.stat.NodeCollectorCallback;

/**
 * Overlay Visualizer.
 * Instantiated by the {@link ow.tool.visualizer.OverlayVisualizer OverlayVisualizer} class.
 */
public class Visualizer implements NodeCollectorCallback {
	public final static String SOFTWARE_NAME = "Overlay Visualizer";

	public final static boolean SELF_DOUBLE_BUFFERING = true;
	public final static boolean SELF_DRAWING = true;

	public final static double DEFAULT_SCALE = 1.0;
	public final static int IMAGE_SCALING_ALGORITHM = Image.SCALE_SMOOTH;
	public final static LineType LINE_TYPE = LineType.BEZIER_CURVE;
	public final static float MESSAGING_LINE_WIDTH = 2.0f;
	public final static float CONNECTION_LINE_WIDTH = 2.5f;
	public final static int MESSAGING_LINE_ALPHA_DECREASEMENT = 0x30;
	public final static int CONNECTION_LINE_ALPHA = 192;	// 0 - 255
	public final static long EXPIRATION = 1000;
	public final static long EXPIRATION_CHECK_INTERVAL = 200;
	public final static long MOMENT_EXPIRATION = 100L;
	public final static double DISPLACEMENT_BY_ARROW_KEYS = 15.0;

	private VisualizerFrame vizFrame;
	private ImageManager imageManager;
	private IDSpacePanel idSpacePanel;

	private int numOfNodes = 0;

	public Visualizer(int idSizeInBit) throws Exception {
		this.imageManager = new ImageManager();

		// initialize GUI components
		this.vizFrame = new VisualizerFrame(this.imageManager, idSizeInBit);
		this.idSpacePanel = this.vizFrame.getIDSpacePanel();
	}

	//
	// operations
	//

	public void setIDSizeInBit(int size) {
		int idSizeInBit = this.imageManager.getGeometryManager().getIDSizeInBit();

		if (idSizeInBit != size) {
			GeometryManager[] geomManagers = vizFrame.getGeometryManagers();
			if (geomManagers != null) {
				for (int i = 0; i < geomManagers.length; i++) {
					geomManagers[i].setIDSizeInBit(size);
				}
			}

			idSpacePanel.resetSize();
			idSpacePanel.repaint();
		}
	}

	public void addNode(ID nodeID, MessagingAddress address) {
		this.idSpacePanel.addNode(nodeID, address);

		// set number of nodes
		int lastNum = this.numOfNodes;
		this.numOfNodes = this.idSpacePanel.getNumOfNodes();

		if (this.numOfNodes != lastNum) {
			this.setNumOfNodeToGeometryManagers(this.numOfNodes);
		}
	}

	public void /*NodePanel*/ removeNode(ID nodeID) {
		/*NodePanel ret =*/ this.idSpacePanel.removeNode(nodeID);

		// set number of nodes
		int lastNum = this.numOfNodes;
		this.numOfNodes = this.idSpacePanel.getNumOfNodes();

		if (this.numOfNodes != lastNum) {
			this.setNumOfNodeToGeometryManagers(this.numOfNodes);
		}

		//return ret;
	}

	private void setNumOfNodeToGeometryManagers(int num) {
		GeometryManager[] geomManagers = vizFrame.getGeometryManagers();
		if (geomManagers != null) {
			for (int i = 0; i < geomManagers.length; i++) {
				geomManagers[i].setNumOfNodes(this.numOfNodes);
			}
		}
	}

	public void emphasizeNode(ID nodeID) {
		this.idSpacePanel.emphasizeNode(nodeID);
	}

	public void addMessage(ID src, ID dest, int tag) {
		this.idSpacePanel.addMessage(src, dest, tag);
	}

	public void addMark(ID id, int hint) {
		this.idSpacePanel.addMark(id, hint);
	}

	public void connectNodes(ID from, ID to, int colorHint) {
		this.idSpacePanel.connectNodes(from, to, colorHint);
	}

	public void disconnectNodes(ID from, ID to, int colorHint) {
		this.idSpacePanel.disconnectNodes(from, to, colorHint);
	}
}

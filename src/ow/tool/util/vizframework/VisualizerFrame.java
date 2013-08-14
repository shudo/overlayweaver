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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

class VisualizerFrame extends JFrame {
	private ImageManager imageManager;
	private IDSpacePanel idSpacePanel;

	private GeometryManager[] geometryManagers;
	private int geometryManagerIndex;

	public VisualizerFrame(ImageManager imageManager, int idSizeInBit) {
		this.imageManager = imageManager;

		this.setBackground(Color.WHITE);

		// initialize components
		initComponents(idSizeInBit);

	    this.setTitle("Overlay Visualizer");
	    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

	    pack();
	    setVisible(true);
	}

	private boolean imageToBeUpdated = true;
	protected synchronized boolean imageToBeUpdated() { return this.imageToBeUpdated; }
	protected synchronized boolean imageToBeUpdated(boolean flag) {
		boolean old = this.imageToBeUpdated;
		this.imageToBeUpdated = flag;
		return old;
	}

	protected void paintComponent(Graphics g) {	// overrides superclass' one
		if (!Visualizer.SELF_DRAWING) {
			super.paintComponents(g);
		}
	}

	/**
	 * Initializes components working on this frame.
	 */
	private void initComponents(int idSizeInBit) {
		Action quitAction = new QuitAction();
		Action resetAction = new ResetAction();
		Action aboutAction = new AboutAction();

		Container contentPane = this.getContentPane();
		contentPane.setLayout(new BorderLayout());

		// prepare menubar
		JMenu fileMenu = new JMenu("File");
		fileMenu.add(resetAction);
		fileMenu.add(quitAction);

		JMenu scaleMenu = new JMenu("Scale");
		JMenu scaleImageMenu = new JMenu("Scale Image");
		for (int i = 0; i < VisualizerUtil.SUPPORTED_SCALES.length; i++) {
			String strScale = VisualizerUtil.SUPPORTED_SCALES[i];
			double scale = 1.0;
			try {
				scale = Double.parseDouble(strScale);
			} catch (Exception e) { /* ignore */ }

			scaleMenu.add(new ScaleAction(this, strScale, scale));
			scaleImageMenu.add(new ScaleImageAction(this, strScale, scale));
		}

		JMenu idSpaceStyleMenu = new JMenu("ID Space Style");

		String packageName = VisualizerFrame.class.getPackage().getName() + ".";
		String geomMgrPkg = VisualizerUtil.GEOMETRY_MANAGER_PKG;
		if (geomMgrPkg.length() > 0) {
			packageName = packageName + geomMgrPkg + ".";
		}

		int nGeometryManager = VisualizerUtil.GEOMETRY_MANAGERS.length;
		this.geometryManagers = new GeometryManager[nGeometryManager];
		for (int i = 0; i < nGeometryManager; i++) {
			String cname = packageName + VisualizerUtil.GEOMETRY_MANAGERS[i][0];
			String entryname = VisualizerUtil.GEOMETRY_MANAGERS[i][1];

			// check if the class exists
			try {
				Class c = Class.forName(cname);
				this.geometryManagers[i] = (GeometryManager)c.newInstance();
			}
			catch (Exception e) {
				// NOTREACHED
				System.err.println("Could not instantiate: " + cname);
				e.printStackTrace();
				System.exit(1);
			}

			idSpaceStyleMenu.add(new IDSpaceStyleAction(entryname, i, this));
		}
		this.geometryManagerIndex = 0;
		this.imageManager.setGeometryManager(this.geometryManagers[0]);

		JMenu lfMenu = new JMenu("Look & Feel");
		for (int i = 0; i < VisualizerUtil.LOOK_AND_FEELS.length; i++) {
			String cname = VisualizerUtil.LOOK_AND_FEELS[i][0];
			String entryname = VisualizerUtil.LOOK_AND_FEELS[i][1];

			try {
				// check if the class exists
				Class c = Class.forName(cname);
			}
			catch (ClassNotFoundException e) {
				continue;
			}

			lfMenu.add(new LFAction(this, entryname, cname));
		}

		JMenu helpMenu = new JMenu("Help");
		helpMenu.add(aboutAction);

		JMenuBar menubar = new JMenuBar();
		menubar.add(fileMenu);
		menubar.add(scaleMenu);
		menubar.add(scaleImageMenu);
		menubar.add(idSpaceStyleMenu);
		menubar.add(lfMenu);
		menubar.add(helpMenu);
		this.setJMenuBar(menubar);

		// prepare toolbar
		JToolBar toolbar = new JToolBar();
		JButton resetButton = toolbar.add(resetAction);
		JButton quitButton = toolbar.add(quitAction);
		contentPane.add(toolbar, BorderLayout.NORTH);

		resetButton.setFocusable(false);
		quitButton.setFocusable(false);

		// prepare ID space panel
		this.idSpacePanel = new IDSpacePanel(this, this.imageManager);
		contentPane.add(this.idSpacePanel, BorderLayout.WEST);

		// set node order map to geometry managers
		for (GeometryManager geom: this.geometryManagers) {
			geom.setNodeOrderMap(this.idSpacePanel.getNodeOrderMap());
		}

	    // set a FocusTraversalPolicy
	    FocusTraversalPolicy ftPolicy = new VisualizerFocusTraversalPolicy(this.idSpacePanel);
	    this.setFocusTraversalPolicy(ftPolicy);
	}

	public IDSpacePanel getIDSpacePanel() {
		return this.idSpacePanel;
	}

	/**
	 * Change the scale.
	 */
	protected void setScale(double scale) {
		this.imageManager.setScale(scale);

		this.idSpacePanel.resetSize();

		this.pack();
		this.repaint();
	}

	/**
	 * Change the scale of images.
	 */
	protected void setImageScale(double scale) {
		this.imageManager.setImageScale(scale);

		this.idSpacePanel.resetSize();

		this.pack();
		this.repaint();
	}

	/**
	 * Change GeometryManger.
	 */
	protected void setGeometryManager(int index) {
		GeometryManager currentGM = this.geometryManagers[this.geometryManagerIndex];
		GeometryManager nextGM = this.geometryManagers[index];

		currentGM.stopMoment();
		this.imageManager.setGeometryManager(nextGM);
		this.setScale(nextGM.getScale());
	}

	protected void nextGeometryManager() {
		GeometryManager currentGM = this.geometryManagers[this.geometryManagerIndex];

		this.geometryManagerIndex++;
		if (this.geometryManagerIndex >= this.geometryManagers.length) {
			this.geometryManagerIndex = 0;
		}

		GeometryManager nextGM = this.geometryManagers[this.geometryManagerIndex];

		currentGM.stopMoment();
		this.imageManager.setGeometryManager(nextGM);
		this.setScale(nextGM.getScale());
	}

	protected void previousGeometryManager() {
		GeometryManager currentGM = this.geometryManagers[this.geometryManagerIndex];

		this.geometryManagerIndex--;
		if (this.geometryManagerIndex < 0) {
			this.geometryManagerIndex = this.geometryManagers.length - 1;
		}

		GeometryManager nextGM = this.geometryManagers[this.geometryManagerIndex];

		currentGM.stopMoment();
		this.imageManager.setGeometryManager(nextGM);
		this.setScale(nextGM.getScale());
	}

	protected GeometryManager[] getGeometryManagers() {
		return this.geometryManagers;
	}

	/**
	 * Prohibit resizng.
	 */
	public boolean isResizable() { return false; }

	/**
	 * An inner class implementing the "Quit" menu.
	 */
	private class QuitAction extends AbstractAction {
		QuitAction() {
			super("Quit");
		}

		public void actionPerformed(ActionEvent e) {
			VisualizerUtil.quit(VisualizerFrame.this, 0);
		}
	}

	/**
	 * An inner class implementing the "Reset" menu.
	 */
	private class ResetAction extends AbstractAction {
		ResetAction() {
			super("Reset");
		}

		public void actionPerformed(ActionEvent e) {
			imageManager.getGeometryManager().resetIncline();

			idSpacePanel.resetLocation();
		}
	}

	/**
	 * An inner class implementing the "Help" menu.
	 */
	private class AboutAction extends AbstractAction {
		AboutAction() {
			super("About " + Visualizer.SOFTWARE_NAME);
		}

		public void actionPerformed(ActionEvent e) {
			VisualizerUtil.showMessage(VisualizerFrame.this, VisualizerUtil.CREDIT);
		}
	}

	/**
	 * An inner class implementing entries in the "Rescale" menu.
	 */
	public class ScaleAction extends AbstractAction {
		// the JFrame to be rescaled
		private VisualizerFrame vizframe;

		// scale
		private double scale;

		ScaleAction(VisualizerFrame f, String entryname, double scale) {
			super(entryname);
			this.vizframe = f;
			this.scale = scale;
		}

		public void actionPerformed(ActionEvent ev) {
			this.vizframe.setScale(this.scale);
		}
	}

	/**
	 * An inner class implementing entries in the "Rescale" menu.
	 */
	public class ScaleImageAction extends AbstractAction {
		// the JFrame to be rescaled
		private VisualizerFrame vizframe;

		// scale
		private double scale;

		ScaleImageAction(VisualizerFrame f, String entryname, double scale) {
			super(entryname);
			this.vizframe = f;
			this.scale = scale;
		}

		public void actionPerformed(ActionEvent ev) {
			this.vizframe.setImageScale(this.scale);
		}
	}

	/**
	 * An inner class implementing entries in the "ID Space Style" menu.
	 */
	private class IDSpaceStyleAction extends AbstractAction {
		int geometryManagerIndex;
		VisualizerFrame vizFrame;

		IDSpaceStyleAction(String entryname, int geometryManagerIndex, VisualizerFrame f) {
			super(entryname);
			this.geometryManagerIndex = geometryManagerIndex;
			this.vizFrame = f;
		}

		public void actionPerformed(ActionEvent ev) {
			this.vizFrame.setGeometryManager(this.geometryManagerIndex);
		}
	}

	/**
	 * An inner class implementing entries in the "Look & Feel" menu.
	 */
	private class LFAction extends AbstractAction {
		// the Window to be updated
		private Window windowToBeUpdated;

		// L&F class name
		private String classLF;

		LFAction(Window w, String entryname, String classLF) {
			super(entryname);
			this.windowToBeUpdated = w;
			this.classLF = classLF;
		}

		public void actionPerformed(ActionEvent ev) {
			try {
				UIManager.setLookAndFeel(this.classLF);
				SwingUtilities.updateComponentTreeUI(this.windowToBeUpdated);
				this.windowToBeUpdated.pack();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * A FocusTraversalPolicy keeps the focus on the specified component.
	 */
	private static class VisualizerFocusTraversalPolicy extends FocusTraversalPolicy {
		private final Component focused;

		VisualizerFocusTraversalPolicy(Component focused) {
			this.focused = focused;
		}

		public Component getInitialComponent(Window w) {
			return focused;
		}
		public Component getDefaultComponent(Container focusCycleRoot) {
			return focused;
		}
		public Component getFirstComponent(Container root) {
			return focused;
		}
		public Component getLastComponent(Container root) {
			return focused;
		}
		public Component getComponentAfter(Container root, Component c) {
			return focused;
		}
		public Component getComponentBefore(Container root, Component c) {
			return focused;
		}
	}
}

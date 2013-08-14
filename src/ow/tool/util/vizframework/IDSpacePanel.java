/*
 * Copyright 2006-2007,2009,2011 National Institute of Advanced Industrial Science
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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.JComponent;

import ow.id.ID;
import ow.messaging.MessageDirectory;
import ow.messaging.MessagingAddress;
import ow.util.Timer;

public class IDSpacePanel extends JComponent implements KeyListener, MouseListener, MouseMotionListener {
	private VisualizerFrame vizFrame;
	private ImageManager imageManager;
	private Map<ID,NodePanel> nodePanelTable = new HashMap<ID,NodePanel>();
	private Set<MessagingEntry> messageSet = new HashSet<MessagingEntry>();
	private Set<MarkEntry> markSet = new HashSet<MarkEntry>();
	private Map<IDPairAndTag,ConnectionEntry> connectionTable = new HashMap<IDPairAndTag,ConnectionEntry>();
	private Map<Integer,Color> colorTable = new HashMap<Integer,Color>();

	private SortedSet<ID> sortedNodeSet = new TreeSet<ID>();
	private Map<ID,Integer> nodeOrderMap = new HashMap<ID,Integer>();

	// for double buffering
	private BufferedImage buffer = null;
	private Graphics2D offScreenGraphics = null;

	/**
	 * The constructor.
	 * Note that a member imageManager is not initialized.
	 */
	protected IDSpacePanel(VisualizerFrame vizFrame, ImageManager imageManager) {
		this.vizFrame = vizFrame;
		this.imageManager = imageManager;

		// register key and mouse event listener
		this.addKeyListener(this);
		this.addMouseListener(this);
		this.addMouseMotionListener(this);

		this.resetSize();

		// start daemons
		Runnable r = new ExpiringDaemon();
		Thread t = new Thread(r);
		t.setName("MessageEntry expiring daemon");
		t.setDaemon(true);
		t.start();
	}

	protected int addNode(ID nodeID, MessagingAddress address) {
		NodePanel nodePanel = new NodePanel(this.imageManager, nodeID, address);
		int numNodes;

		synchronized (this.nodePanelTable) {
			// add or replace
			this.nodePanelTable.put(nodeID, nodePanel);

			numNodes = this.nodePanelTable.size();
		}

		synchronized (this.sortedNodeSet) {
			this.sortedNodeSet.add(nodeID);

			synchronized (this.nodeOrderMap) {
				int order = 0;
				for (ID id: this.sortedNodeSet) {
					this.nodeOrderMap.put(id, order++);
				}
			}
		}

		// add to this IDSpacePanel
		this.add(nodePanel);

		vizFrame.imageToBeUpdated(true);

		this.repaint();

		return numNodes;
	}

	protected NodePanel removeNode(ID nodeID) {
		// remove NodePanel
		NodePanel nodePanel;

		synchronized (this.nodePanelTable) {
			nodePanel = this.nodePanelTable.remove(nodeID);
		}

		synchronized (this.sortedNodeSet) {
			this.sortedNodeSet.remove(nodeID);

			synchronized (this.nodeOrderMap) {
				this.nodeOrderMap.remove(nodeID);

				int order = 0;
				for (ID id: this.sortedNodeSet) {
					this.nodeOrderMap.put(id, order++);
				}
			}
		}

		// remove from this IDSpacePanel
		if (nodePanel != null) {
			this.remove(nodePanel);
			nodePanel.setEnabled(false);
			nodePanel.setVisible(false);
		}

		// remove MessagingEntry
		synchronized (this.connectionTable) {
			for (Iterator it = this.connectionTable.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<ID,ConnectionEntry> setEntry = (Map.Entry<ID,ConnectionEntry>)it.next();
				ConnectionEntry connEntry = setEntry.getValue();
				if (nodeID.equals(connEntry.from) || nodeID.equals(connEntry.to)) {
					it.remove();
				}
			}
		}

		vizFrame.imageToBeUpdated(true);

		this.repaint();

		return nodePanel;
	}

	protected int getNumOfNodes() {
		synchronized (this.nodePanelTable) {
			return this.nodePanelTable.size();
		}
	}

	protected Map<ID,Integer> getNodeOrderMap() {
		return this.nodeOrderMap;
	}

	protected void emphasizeNode(ID nodeID) {
		NodePanel nodePanel = null;

		synchronized (this.nodePanelTable) {
			nodePanel = this.nodePanelTable.get(nodeID);
		}

		if (nodePanel != null) {
			nodePanel.emphasize();
		}

		vizFrame.imageToBeUpdated(true);
	}

	protected void addMessage(ID src, ID dest, int tag) {
		synchronized (this.messageSet) {
			this.messageSet.add(new MessagingEntry(src, dest, tag));
		}

		vizFrame.imageToBeUpdated(true);

		this.repaint();
	}

	protected void addMark(ID id, int hint) {
		NodePanel nodePanel = null;
		synchronized (this.nodePanelTable) {
			nodePanel = this.nodePanelTable.get(id);
		}

		if (nodePanel != null) {
			// there is a node whose ID is same as the specified one
			nodePanel.mark();
		}

		if (nodePanel == null) {
			MarkEntry entry = new MarkEntry(this.imageManager, id, hint);
			synchronized (this.markSet) {
				this.markSet.add(entry);
			}

			// add to this IDSpacePanel
			this.add(entry.getPanel());
		}

		vizFrame.imageToBeUpdated(true);

		this.repaint();
	}

	protected void removeMark(MarkEntry entry) {
		boolean removed = false;

		synchronized (this.markSet) {
			removed = this.markSet.remove(entry);
		}

		if (removed) {
			// remove from this IDSpacePanel
			this.remove(entry.getPanel());
		}

		vizFrame.imageToBeUpdated(true);
	}

	protected void connectNodes(ID from, ID to, int colorHint) {
		synchronized (this.connectionTable) {
			this.connectionTable.put(new IDPairAndTag(from, to, colorHint), new ConnectionEntry(from, to, colorHint));
		}

		vizFrame.imageToBeUpdated(true);

		this.repaint();
	}

	protected void disconnectNodes(ID from, ID to, int colorHint) {
		synchronized (this.connectionTable) {
			this.connectionTable.remove(new IDPairAndTag(from, to, colorHint));
		}

		vizFrame.imageToBeUpdated(true);

		this.repaint();
	}

	/**
	 * Adjust the size according to ImageManager.
	 */
	protected void resetSize() {
		GeometryManager geometryManager = this.imageManager.getGeometryManager();

		// set size
		this.setPreferredSize(new Dimension(
				geometryManager.getIDSpaceWidth(),
				geometryManager.getIDSpaceHeight()));

		NodePanel[] nodePanelArray = null;
		synchronized (this.nodePanelTable) {
			int size = this.nodePanelTable.size();
			if (size > 0) {
				nodePanelArray = new NodePanel[size];
				this.nodePanelTable.values().toArray(nodePanelArray);
			}
		}
		if (nodePanelArray != null) {
			for (NodePanel n: nodePanelArray) {
				n.resetSize();
			}
		}

		MarkEntry[] markEntryArray = null;
		synchronized (this.markSet) {
			int size = this.markSet.size();
			if (size > 0) {
				markEntryArray = new MarkEntry[size];
				this.markSet.toArray(markEntryArray);
			}
		}
		if (markEntryArray != null) {
			for (MarkEntry m: markEntryArray) {
				m.getPanel().resetSize();
			}
		}

		MessagingEntry[] messagingEntryArray = null;
		synchronized (this.messageSet) {
			int size = this.messageSet.size();
			if (size > 0) {
				messagingEntryArray = new MessagingEntry[size];
				this.messageSet.toArray(messagingEntryArray);
			}
		}
		if (messagingEntryArray != null) {
			for (MessagingEntry m: messagingEntryArray) {
				m.resetSize();
			}
		}

		ConnectionEntry[] connectionEntryArray = null;
		synchronized (this.connectionTable) {
			int size = this.connectionTable.size();
			if (size > 0) {
				connectionEntryArray = new ConnectionEntry[size];
				this.connectionTable.values().toArray(connectionEntryArray);
			}
		}
		if (connectionEntryArray != null) {
			for (ConnectionEntry c: connectionEntryArray) {
				c.resetSize();
			}
		}

		// prepare for double buffering
		if (Visualizer.SELF_DOUBLE_BUFFERING) {
			int newWidth = geometryManager.getIDSpaceWidth();
			int newHeight = geometryManager.getIDSpaceHeight();

			if (this.buffer == null
					|| this.buffer.getWidth() != newWidth
					|| this.buffer.getHeight() != newHeight) {
				this.buffer = new BufferedImage(newWidth, newHeight,
						BufferedImage.TYPE_INT_ARGB);
				this.offScreenGraphics = this.buffer.createGraphics();
			}

			this.offScreenGraphics.setStroke(this.imageManager.getStrokeForMessaging());
		}

		this.vizFrame.imageToBeUpdated(true);
	}

	/**
	 * Re-calculate the location.
	 */
	public void resetLocation() {
		NodePanel[] nodePanelArray = null;
		synchronized (this.nodePanelTable) {
			int size = this.nodePanelTable.size();
			if (size > 0) {
				nodePanelArray = new NodePanel[size];
				this.nodePanelTable.values().toArray(nodePanelArray);
			}
		}
		if (nodePanelArray != null) {
			for (NodePanel n: nodePanelArray) {
				n.resetLocation();
			}
		}

		MarkEntry[] markEntryArray = null;
		synchronized (this.markSet) {
			int size = this.markSet.size();
			if (size > 0) {
				markEntryArray = new MarkEntry[size];
				this.markSet.toArray(markEntryArray);
			}
		}
		if (markEntryArray != null) {
			for (MarkEntry m: markEntryArray) {
				m.getPanel().resetLocation();
			}
		}

		MessagingEntry[] messagingEntryArray = null;
		synchronized (this.messageSet) {
			int size = this.messageSet.size();
			if (size > 0) {
				messagingEntryArray = new MessagingEntry[size];
				this.messageSet.toArray(messagingEntryArray);
			}
		}
		if (messagingEntryArray != null) {
			for (MessagingEntry m: messagingEntryArray) {
				m.resetLocation();
			}
		}

		ConnectionEntry[] connectionEntryArray = null;
		synchronized (this.connectionTable) {
			int size = this.connectionTable.size();
			if (size > 0) {
				connectionEntryArray = new ConnectionEntry[size];
				this.connectionTable.values().toArray(connectionEntryArray);
			}
		}
		if (connectionEntryArray != null) {
			for (ConnectionEntry c: connectionEntryArray) {
				c.resetLocation();
			}
		}

		vizFrame.imageToBeUpdated(true);
	}

	public boolean isDoubleBuffered() {
		return !Visualizer.SELF_DOUBLE_BUFFERING;
	}

	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D)g;

		boolean toBeUpdated = this.vizFrame.imageToBeUpdated(false);

		if (Visualizer.SELF_DOUBLE_BUFFERING) {
			if (toBeUpdated) {
				// clear
				this.offScreenGraphics.setColor(this.getBackground());
				this.offScreenGraphics.fillRect(0, 0, this.buffer.getWidth(), this.buffer.getHeight());

				// draw lines
				this.offScreenGraphics.setStroke(this.imageManager.getStrokeForConnection());
				this.drawConnectionEntriesOnGraphics(this.offScreenGraphics);

				this.offScreenGraphics.setStroke(this.imageManager.getStrokeForMessaging());
				this.drawMessagingEntriesOnGraphics(this.offScreenGraphics);

				// paint node panels
				this.drawNodePanelsOnGraphics(this.offScreenGraphics);

				// paint mark panels
				this.drawMarkPanelsOnGraphics(this.offScreenGraphics);
			}

			// copy
			g2.drawImage(this.buffer, 0, 0, this);
		}
		else {
			// draw lines
			g2.setStroke(this.imageManager.getStrokeForConnection());
			this.drawConnectionEntriesOnGraphics(g2);

			g2.setStroke(this.imageManager.getStrokeForMessaging());
			this.drawMessagingEntriesOnGraphics(g2);

			if (Visualizer.SELF_DRAWING) {
				// paint node panels
				this.drawNodePanelsOnGraphics(g2);

				// paint mark panels
				this.drawMarkPanelsOnGraphics(g2);
			}
		}
	}

	private void drawConnectionEntriesOnGraphics(Graphics2D g2) {
		ConnectionEntry[] connectionEntryArray = null;
		synchronized (this.connectionTable) {
			int size = this.connectionTable.size();
			if (size > 0) {
				connectionEntryArray = new ConnectionEntry[size];
				this.connectionTable.values().toArray(connectionEntryArray);
			}
		}
		if (connectionEntryArray != null) {
			for (ConnectionEntry c: connectionEntryArray) {
				g2.setColor(c.getColor());
				g2.draw(c.getShape());
			}
		}
	}

	private void drawMessagingEntriesOnGraphics(Graphics2D g2) {
		MessagingEntry[] messagingEntryArray = null;
		synchronized (this.messageSet) {
			int size = this.messageSet.size();
			if (size > 0) {
				messagingEntryArray = new MessagingEntry[size];
				this.messageSet.toArray(messagingEntryArray);
			}
		}
		if (messagingEntryArray != null) {
			for (MessagingEntry m: messagingEntryArray) {
				g2.setColor(m.getColor());
				g2.draw(m.getShape());
			}
		}
	}

	private void drawNodePanelsOnGraphics(Graphics2D g2) {
		NodePanel[] nodePanelArray = null;
		synchronized (this.nodePanelTable) {
			int size = this.nodePanelTable.size();
			if (size > 0) {
				nodePanelArray = new NodePanel[size];
				this.nodePanelTable.values().toArray(nodePanelArray);
			}
		}
		if (nodePanelArray != null) {
			for (NodePanel n: nodePanelArray) {
				n.drawComponent(g2);
			}
		}
	}

	private void drawMarkPanelsOnGraphics(Graphics2D g2) {
		MarkEntry[] markEntryArray = null;
		synchronized (this.markSet) {
			int size = this.markSet.size();
			if (size > 0) {
				markEntryArray = new MarkEntry[size];
				this.markSet.toArray(markEntryArray);
			}
		}
		if (markEntryArray != null) {
			for (MarkEntry m: markEntryArray) {
				m.getPanel().drawComponent(g2);
			}
		}
	}

	//
	// Methods requested by KeyListener
	//

	public void keyTyped(KeyEvent e) {}

	public void keyPressed(KeyEvent e) {
		int code = e.getKeyCode();

		switch (code) {
		case KeyEvent.VK_Q:
			// Quit
			VisualizerUtil.quit(this, 0);
			break;
		case KeyEvent.VK_R:
			// Reset
			this.imageManager.getGeometryManager().resetIncline();
			this.resetLocation();
			break;
		case KeyEvent.VK_SPACE:
		case KeyEvent.VK_N:
			// change ID Space Style
			this.vizFrame.nextGeometryManager();
			break;
		case KeyEvent.VK_BACK_SPACE:
		case KeyEvent.VK_DELETE:
		case KeyEvent.VK_P:
			// change ID Space Style
			this.vizFrame.previousGeometryManager();
			break;
		case KeyEvent.VK_RIGHT:
			this.lastX += Visualizer.DISPLACEMENT_BY_ARROW_KEYS;
			this.imageManager.getGeometryManager().addIncline(Visualizer.DISPLACEMENT_BY_ARROW_KEYS, 0.0);
			this.resetLocation();
			break;
		case KeyEvent.VK_LEFT:
			this.lastX -= Visualizer.DISPLACEMENT_BY_ARROW_KEYS;
			this.imageManager.getGeometryManager().addIncline(-Visualizer.DISPLACEMENT_BY_ARROW_KEYS, 0.0);
			this.resetLocation();
			break;
		case KeyEvent.VK_UP:
			this.lastY -= Visualizer.DISPLACEMENT_BY_ARROW_KEYS;
			this.imageManager.getGeometryManager().addIncline(0.0, -Visualizer.DISPLACEMENT_BY_ARROW_KEYS);
			this.resetLocation();
			break;
		case KeyEvent.VK_DOWN:
			this.lastY += Visualizer.DISPLACEMENT_BY_ARROW_KEYS;
			this.imageManager.getGeometryManager().addIncline(0.0, Visualizer.DISPLACEMENT_BY_ARROW_KEYS);
			this.resetLocation();
			break;
		}
	}

	public void keyReleased(KeyEvent e) {}

	//
	// Methods requested by MouseListener
	//

	private int lastX, lastY;

	public void mouseClicked(MouseEvent e) {}

	public void mousePressed(MouseEvent e) {
		this.lastX = e.getX();
		this.lastY = e.getY();

		this.imageManager.getGeometryManager().stopMoment();
	}

	public void mouseReleased(MouseEvent e) {
		this.imageManager.getGeometryManager().normalizeIncline();

		this.imageManager.getGeometryManager().startMoment(this);
	}

	public void mouseEntered(MouseEvent e) {}

	public void mouseExited(MouseEvent e) {}

	//
	// Methods requested by MouseMotionListener
	//

	public void mouseDragged(MouseEvent e) {
		int x = e.getX(), y = e.getY();

		this.imageManager.getGeometryManager().addIncline(x - this.lastX, y - this.lastY);

		this.lastX = x;
		this.lastY = y;

		this.resetLocation();
	}

	public void mouseMoved(MouseEvent e) {}

	//
	// Inner classes
	//

	private class MessagingEntry {
		private long addedTime;
		private ID src, dest;
		private Shape shape;
		private Color color;

		MessagingEntry(ID src, ID dest, int tag) {
			this.src = src;
			this.dest = dest;
			this.addedTime = Timer.currentTimeMillis();
			this.color = MessageDirectory.getColor(tag);

			this.resetSize();
		}

		void resetSize() {
			this.shape = imageManager.getGeometryManager().getShapeForMessage(this.src, this.dest);
		}

		void resetLocation() {
			this.resetSize();
		}

		long getAddedTime() { return this.addedTime; }
		Shape getShape() { return this.shape; }
		Color getColor() { return this.color; }
		Color setColor(Color newColor) {
			Color old = this.color;
			this.color = newColor;
			return old;
		}
	}

	private class MarkEntry {
		private long addedTime;
		private ID id;
		private MarkPanel panel;

		MarkEntry(ImageManager imageManager, ID id, int hint) {
			this.addedTime = Timer.currentTimeMillis();
			this.id = id;
			this.panel = new MarkPanel(imageManager, id, hint);
		}

		long getAddedTime() { return this.addedTime; }
		ID getID() { return this.id; }
		MarkPanel getPanel() { return this.panel; }
	}

	private class ConnectionEntry {
		private ID from, to;
		private Shape shape;
		private Color color;

		ConnectionEntry(ID from, ID to, int colorHint) {
			this.from = from;
			this.to = to;

			if (colorHint < 0) colorHint = -colorHint;
			int colorIndex = colorHint % VisualizerUtil.CONNECTION_COLORS.length;
			this.color = VisualizerUtil.CONNECTION_COLORS[colorIndex];

			int rgb = this.color.getRGB();
			rgb &= 0x00ffffff;
			rgb |= Visualizer.CONNECTION_LINE_ALPHA << 24;
			this.color = new Color(rgb, true);

			this.resetSize();
		}

		void resetSize() {
			this.shape = imageManager.getGeometryManager().getShapeForConnection(this.from, this.to);
		}

		void resetLocation() {
			this.resetSize();
		}

		Shape getShape() { return this.shape; }
		Color getColor() { return this.color; }
		Color setColor(Color newColor) {
			Color old = this.color;
			this.color = newColor;
			return old;
		}
	}

	private class ExpiringDaemon implements Runnable {
		public void run() {
			while (true) {
				try {
					Thread.sleep(Visualizer.EXPIRATION_CHECK_INTERVAL);
				}
				catch (InterruptedException e) {
					System.err.println("Thread#sleep() interrupted.");
					e.printStackTrace(System.err);
					break;
				}

				boolean changed;
				long threshold = Timer.currentTimeMillis() - Visualizer.EXPIRATION;

				// nodes
				synchronized (nodePanelTable) {
					for (NodePanel n: nodePanelTable.values()) {
						if (n.getEmphasizedTime() <= threshold) {
							n.deemphasize();

							vizFrame.imageToBeUpdated(true);
						}
					}
				}

				// marks
				changed = false;
				Set<MarkEntry> expiredMarkSet = new HashSet<MarkEntry>();
				synchronized (markSet) {
					for (MarkEntry entry: markSet) {
						if (entry.getAddedTime() <= threshold) {
							// expire
							expiredMarkSet.add(entry);
							changed = true;
						}
					}
				}

				if (changed) {
					for (MarkEntry entry: expiredMarkSet) {
						removeMark(entry);

						vizFrame.imageToBeUpdated(true);
					}
				}

				// messages
				changed = false;
				Set<MessagingEntry> expiredMessageSet = new HashSet<MessagingEntry>();
				synchronized (messageSet) {
					for (MessagingEntry msg: messageSet) {
						if (msg.getAddedTime() <= threshold) {
							// expire
							expiredMessageSet.add(msg);
							changed = true;
						}
						else {
							// make the line more transparent
							int rgb = msg.getColor().getRGB();
							int alpha = (rgb & 0xff000000) >>> 24;

							alpha -= Visualizer.MESSAGING_LINE_ALPHA_DECREASEMENT;
							if (alpha < 0) alpha = 0;

							rgb = rgb & 0x00ffffff | (alpha << 24);

							Color c = null;
							synchronized (colorTable) {
								c = colorTable.get(rgb);
								if (c == null) {
									c = new Color(rgb, true);
									colorTable.put(rgb, c);
								}
							}

							msg.setColor(c);
						}
					}

					if (changed) {
						messageSet.removeAll(expiredMessageSet);
					}

					if (!messageSet.isEmpty()) {
						vizFrame.imageToBeUpdated(true);
					}
				}

				repaint();
			}
		}
	}
}

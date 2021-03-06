/*
 * The Unified Mapping Platform (JUMP) is an extensible, interactive GUI for
 * visualizing and manipulating spatial features with geometry and attributes.
 *
 * Copyright (C) 2003 Vivid Solutions
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * For more information, contact:
 *
 * Vivid Solutions Suite #1A 2328 Government Street Victoria BC V8T 5G5 Canada
 *
 * (250)385-6040 www.vividsolutions.com
 */
package com.vividsolutions.jump.workbench.ui;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.util.Assert;
import com.vividsolutions.jump.util.Block;
import com.vividsolutions.jump.util.CollectionUtil;
import com.vividsolutions.jump.util.StringUtil;
import com.vividsolutions.jump.workbench.WorkbenchContext;
import com.vividsolutions.jump.workbench.model.Category;
import com.vividsolutions.jump.workbench.model.CategoryEvent;
import com.vividsolutions.jump.workbench.model.FeatureEvent;
import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.model.LayerEvent;
import com.vividsolutions.jump.workbench.model.LayerEventType;
import com.vividsolutions.jump.workbench.model.LayerListener;
import com.vividsolutions.jump.workbench.model.LayerManager;
import com.vividsolutions.jump.workbench.model.LayerManagerProxy;
import com.vividsolutions.jump.workbench.model.Layerable;
import com.vividsolutions.jump.workbench.model.StandardCategoryNames;
import com.vividsolutions.jump.workbench.model.Task;
import com.vividsolutions.jump.workbench.model.UndoableEditReceiver;
import com.vividsolutions.jump.workbench.model.WMSLayer;
import com.vividsolutions.jump.workbench.plugin.*;
import com.vividsolutions.jump.workbench.plugin.EnableCheck;
import com.vividsolutions.jump.workbench.plugin.PlugIn;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;
import com.vividsolutions.jump.workbench.plugin.ThreadedPlugIn;
import com.vividsolutions.jump.workbench.ui.plugin.CloneWindowPlugIn;
import com.vividsolutions.jump.workbench.ui.plugin.FeatureInstaller;
import com.vividsolutions.jump.workbench.ui.renderer.style.ChoosableStyle;
import com.vividsolutions.jump.workbench.ui.task.TaskMonitorManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.swing.*;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
/**
 * This class is responsible for the main window of the JUMP application.
 */
public class WorkbenchFrame extends JFrame implements LayerViewPanelContext,
		ViewportListener {
	BorderLayout borderLayout1 = new BorderLayout();
	JLabel coordinateLabel = new JLabel();
	JMenuBar menuBar = new JMenuBar();
	JMenu fileMenu = (JMenu) FeatureInstaller.installMnemonic(
			new JMenu("File"), menuBar);
	JMenuItem exitMenuItem = FeatureInstaller.installMnemonic(new JMenuItem(
			"E&xit"), fileMenu);
	GridBagLayout gridBagLayout1 = new GridBagLayout();
	JLabel messageLabel = new JLabel();
	JPanel statusPanel = new JPanel();
	JLabel timeLabel = new JLabel();
	//<<TODO:FEATURE>> Before JUMP Workbench closes, prompt the user to save
	// any
	//unsaved layers [Jon Aquino]
	WorkbenchToolBar toolBar;
	JMenu windowMenu = (JMenu) FeatureInstaller.installMnemonic(new JMenu(
			"Window"), menuBar);
	private TitledPopupMenu categoryPopupMenu = new TitledPopupMenu() {
		{
			addPopupMenuListener(new PopupMenuListener() {
				public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
					LayerNamePanel panel = ((LayerNamePanelProxy) getActiveInternalFrame())
							.getLayerNamePanel();
					setTitle((panel.selectedNodes(Category.class).size() != 1) ? ("("
							+ panel.selectedNodes(Category.class).size() + " categories selected)")
							: ((Category) panel.selectedNodes(Category.class)
									.iterator().next()).getName());
				}
				public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				}
				public void popupMenuCanceled(PopupMenuEvent e) {
				}
			});
		}
	};
	private JDesktopPane desktopPane = new JDesktopPane();
	//<<TODO:REMOVE>> Actually we're not using the three optimization
	// parameters
	//below. Remove. [Jon Aquino]
	private int envelopeRenderingThreshold = 500;
	private HTMLFrame outputFrame = new HTMLFrame(this) {
		public void setTitle(String title) {
			//Don't allow the title of the output frame to be changed.
		}
		{
			super.setTitle("Output");
		}
	};
	private ImageIcon icon;
	private TitledPopupMenu layerNamePopupMenu = new TitledPopupMenu() {
		{
			addPopupMenuListener(new PopupMenuListener() {
				public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
					LayerNamePanel panel = ((LayerNamePanelProxy) getActiveInternalFrame())
							.getLayerNamePanel();
					setTitle((panel.selectedNodes(Layer.class).size() != 1) ? ("("
							+ panel.selectedNodes(Layer.class).size() + " layers selected)")
							: ((Layerable) panel.selectedNodes(Layer.class)
									.iterator().next()).getName());
				}
				public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				}
				public void popupMenuCanceled(PopupMenuEvent e) {
				}
			});
		}
	};
	private TitledPopupMenu wmsLayerNamePopupMenu = new TitledPopupMenu() {
		{
			addPopupMenuListener(new PopupMenuListener() {
				public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
					LayerNamePanel panel = ((LayerNamePanelProxy) getActiveInternalFrame())
							.getLayerNamePanel();
					setTitle((panel.selectedNodes(WMSLayer.class).size() != 1) ? ("("
							+ panel.selectedNodes(WMSLayer.class).size() + " WMS layers selected)")
							: ((Layerable) panel.selectedNodes(WMSLayer.class)
									.iterator().next()).getName());
				}
				public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
				}
				public void popupMenuCanceled(PopupMenuEvent e) {
				}
			});
		}
	};
	private LayerNamePanelListener layerNamePanelListener = new LayerNamePanelListener() {
		public void layerSelectionChanged() {
			toolBar.updateEnabledState();
		}
	};
	//Here is a small patch to JUMP to avoid creating a StringBuffer every
	//coordinate change (which could be many thoustands). Replace the innter
	//class in WorkbenchFrame.java with the following. I am assuming only one
	//thread can call the listener at a time. If that is untrue please
	// synchronize
	//cursorPositionChanged().
	//
	//Sheldon Young 2004-01-30
	private LayerViewPanelListener layerViewPanelListener = new LayerViewPanelListener() {
		// Avoid creating an expensive StringBuffer when the cursor position
		// changes.
		private StringBuffer positionStatusBuf = new StringBuffer("(");
		public void cursorPositionChanged(String x, String y) {
			positionStatusBuf.setLength(1);
			positionStatusBuf.append(x).append(", ").append(y).append(")");
			coordinateLabel.setText(positionStatusBuf.toString());
		}
		public void selectionChanged() {
			toolBar.updateEnabledState();
		}
		public void fenceChanged() {
			toolBar.updateEnabledState();
		}
		public void painted(Graphics graphics) {
		}
	};
	//<<TODO:NAMING>> This name is not clear [Jon Aquino]
	private int maximumFeatureExtentForEnvelopeRenderingInPixels = 10;
	//<<TODO:NAMING>> This name is not clear [Jon Aquino]
	private int minimumFeatureExtentForAnyRenderingInPixels = 2;
	private StringBuffer log = new StringBuffer();
	private int taskSequence = 1;
	private WorkbenchContext workbenchContext;
	private JLabel memoryLabel = new JLabel();
	private String lastStatusMessage = "";
	private Set choosableStyleClasses = new HashSet();
	private JLabel wmsLabel = new JLabel();
	private ArrayList easyKeyListeners = new ArrayList();
	private Map nodeClassToLayerNamePopupMenuMap = CollectionUtil
			.createMap(new Object[]{Layer.class, layerNamePopupMenu,
					WMSLayer.class, wmsLayerNamePopupMenu, Category.class,
					categoryPopupMenu});
	private int positionIndex = -1;
	private int primaryInfoFrameIndex = -1;
	public WorkbenchFrame(String title, ImageIcon icon,
			final WorkbenchContext workbenchContext) throws Exception {
		setTitle(title);
		new Timer(1000, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				memoryLabel.setText(getMBCommittedMemory()
						+ " MB Committed Memory");
				memoryLabel.setToolTipText(LayerManager.layerManagerCount()
						+ " Layer Manager"
						+ StringUtil.s(LayerManager.layerManagerCount()));
			}
		}).start();
		this.workbenchContext = workbenchContext;
		this.icon = icon;
		toolBar = new WorkbenchToolBar(workbenchContext);
		try {
			jbInit();
			configureStatusLabel(messageLabel, 300);
			configureStatusLabel(coordinateLabel, 150);
			configureStatusLabel(timeLabel, 200);
			configureStatusLabel(wmsLabel, 100);
		} catch (Exception e) {
			e.printStackTrace();
		}
		new RecursiveKeyListener(this) {
			public void keyTyped(KeyEvent e) {
				for (Iterator i = easyKeyListeners.iterator(); i.hasNext();) {
					KeyListener l = (KeyListener) i.next();
					l.keyTyped(e);
				}
			}
			public void keyPressed(KeyEvent e) {
				for (Iterator i = new ArrayList(easyKeyListeners).iterator(); i
						.hasNext();) {
					KeyListener l = (KeyListener) i.next();
					l.keyPressed(e);
				}
			}
			public void keyReleased(KeyEvent e) {
				for (Iterator i = new ArrayList(easyKeyListeners).iterator(); i
						.hasNext();) {
					KeyListener l = (KeyListener) i.next();
					l.keyReleased(e);
				}
			}
		};
		installKeyboardShortcutListener();
	}
	/**
	 * Unlike #add(KeyListener), listeners registered using this method are
	 * notified when KeyEvents occur on this frame's child components. Note:
	 * Bug: KeyListeners registered using this method may receive events
	 * multiple times.
	 * 
	 * @see #addKeyboardShortcut
	 */
	public void addEasyKeyListener(KeyListener l) {
		easyKeyListeners.add(l);
	}
	public void removeEasyKeyListener(KeyListener l) {
		easyKeyListeners.remove(l);
	}
	public String getMBCommittedMemory() {
		return new DecimalFormat("###,###").format((Runtime.getRuntime()
				.totalMemory() - Runtime.getRuntime().freeMemory())
				/ (1024 * 1024d));
	}
	/**
	 * @param newEnvelopeRenderingThreshold
	 *                  the number of on-screen features above which envelope
	 *                  rendering should occur
	 */
	public void setEnvelopeRenderingThreshold(int newEnvelopeRenderingThreshold) {
		envelopeRenderingThreshold = newEnvelopeRenderingThreshold;
	}
	public void setMaximumFeatureExtentForEnvelopeRenderingInPixels(
			int newMaximumFeatureExtentForEnvelopeRenderingInPixels) {
		maximumFeatureExtentForEnvelopeRenderingInPixels = newMaximumFeatureExtentForEnvelopeRenderingInPixels;
	}
	public void log(String message) {
		log.append(new Date() + "  " + message
				+ System.getProperty("line.separator"));
	}
	public String getLog() {
		return log.toString();
	}
	public void setMinimumFeatureExtentForAnyRenderingInPixels(
			int newMinimumFeatureExtentForAnyRenderingInPixels) {
		minimumFeatureExtentForAnyRenderingInPixels = newMinimumFeatureExtentForAnyRenderingInPixels;
	}
	public void displayLastStatusMessage() {
		setStatusMessage(lastStatusMessage);
	}
	public void setStatusMessage(String message) {
		lastStatusMessage = message;
		setStatusBarText(message);
		setStatusBarTextHighlighted(false, null);
	}
	private void setStatusBarText(String message) {
		//<<TODO:IMPROVE>> Treat null messages like "" [Jon Aquino]
		messageLabel.setText((message == "") ? " " : message);
		messageLabel.setToolTipText(message);
		//Make message at least a space so that status bar won't collapse [Jon
		// Aquino]
	}
	/**
	 * To highlight a message, call #warnUser.
	 */
	private void setStatusBarTextHighlighted(boolean highlighted, Color color) {
		//Use #coordinateLabel rather than (unattached) dummy label because
		//dummy label's background does not change when L&F changes. [Jon
		// Aquino]
		messageLabel.setForeground(highlighted ? Color.black : coordinateLabel
				.getForeground());
		messageLabel.setBackground(highlighted ? color : coordinateLabel
				.getBackground());
	}
	public void setTimeMessage(String message) {
		//<<TODO:IMPROVE>> Treat null messages like "" [Jon Aquino]
		timeLabel.setText((message == "") ? " " : message);
		//Make message at least a space so that status bar won't collapse [Jon
		// Aquino]
	}
	public JInternalFrame getActiveInternalFrame() {
		return desktopPane.getSelectedFrame();
	}
	public JInternalFrame[] getInternalFrames() {
		return desktopPane.getAllFrames();
	}
	public TitledPopupMenu getCategoryPopupMenu() {
		return categoryPopupMenu;
	}
	public WorkbenchContext getContext() {
		return workbenchContext;
	}
	public JDesktopPane getDesktopPane() {
		return desktopPane;
	}
	public int getEnvelopeRenderingThreshold() {
		return envelopeRenderingThreshold;
	}
	public TitledPopupMenu getLayerNamePopupMenu() {
		return layerNamePopupMenu;
	}
	public TitledPopupMenu getWMSLayerNamePopupMenu() {
		return wmsLayerNamePopupMenu;
	}
	public LayerViewPanelListener getLayerViewPanelListener() {
		return layerViewPanelListener;
	}
	public Map getNodeClassToPopupMenuMap() {
		return nodeClassToLayerNamePopupMenuMap;
	}
	public LayerNamePanelListener getLayerNamePanelListener() {
		return layerNamePanelListener;
	}
	public int getMaximumFeatureExtentForEnvelopeRenderingInPixels() {
		return maximumFeatureExtentForEnvelopeRenderingInPixels;
	}
	public int getMinimumFeatureExtentForAnyRenderingInPixels() {
		return minimumFeatureExtentForAnyRenderingInPixels;
	}
	public HTMLFrame getOutputFrame() {
		return outputFrame;
	}
	public WorkbenchToolBar getToolBar() {
		return toolBar;
	}
	public void activateFrame(JInternalFrame frame) {
		try {
			if (frame.isIcon()) {
				frame.setIcon(false);
			}
			frame.moveToFront();
			frame.requestFocus();
			frame.setSelected(true);
			if (!(frame instanceof TaskFrame)) {
				frame.setMaximum(false);
			}
		} catch (PropertyVetoException e) {
			warnUser(StringUtil.stackTrace(e));
		}
	}
	/**
	 * If internalFrame is a LayerManagerProxy, the close behaviour will be
	 * altered so that the user is prompted if it is the last window on the
	 * LayerManager.
	 */
	public void addInternalFrame(final JInternalFrame internalFrame) {
		addInternalFrame(internalFrame, false, true);
	}
	public void addInternalFrame(final JInternalFrame internalFrame,
			boolean alwaysOnTop, boolean autoUpdateToolBar) {
		if (internalFrame instanceof LayerManagerProxy) {
			setClosingBehaviour((LayerManagerProxy) internalFrame);
			installTitleBarModifiedIndicator((LayerManagerProxy) internalFrame);
		}
		//<<TODO:IMPROVE>> Listen for when the frame closes, and when it does,
		//activate the topmost frame. Because Swing does not seem to do this
		//automatically. [Jon Aquino]
		internalFrame.setFrameIcon(icon);
		//Call JInternalFrame#setVisible before JDesktopPane#add; otherwise,
		// the
		//TreeLayerNamePanel starts too narrow (100 pixels or so) for some
		// reason.
		//<<TODO>>Investigate. [Jon Aquino]
		internalFrame.setVisible(true);
		desktopPane.add(internalFrame, alwaysOnTop ? JLayeredPane.PALETTE_LAYER
				: JLayeredPane.DEFAULT_LAYER);
		if (autoUpdateToolBar) {
			internalFrame.addInternalFrameListener(new InternalFrameListener() {
				public void internalFrameActivated(InternalFrameEvent e) {
					toolBar.updateEnabledState();
					//Associate current cursortool with the new frame [Jon
					// Aquino]
					toolBar.reClickSelectedCursorToolButton();
				}
				public void internalFrameClosed(InternalFrameEvent e) {
					toolBar.updateEnabledState();
				}
				public void internalFrameClosing(InternalFrameEvent e) {
					toolBar.updateEnabledState();
				}
				public void internalFrameDeactivated(InternalFrameEvent e) {
					toolBar.updateEnabledState();
				}
				public void internalFrameDeiconified(InternalFrameEvent e) {
					toolBar.updateEnabledState();
				}
				public void internalFrameIconified(InternalFrameEvent e) {
					toolBar.updateEnabledState();
				}
				public void internalFrameOpened(InternalFrameEvent e) {
					toolBar.updateEnabledState();
				}
			});
			//Call #activateFrame *after* adding the listener. [Jon Aquino]
			activateFrame(internalFrame);
			position(internalFrame);
		}
	}
	private void installTitleBarModifiedIndicator(
			final LayerManagerProxy internalFrame) {
		final JInternalFrame i = (JInternalFrame) internalFrame;
		new Block() {
			//Putting updatingTitle in a Block is better than making it an
			//instance variable, because this way there is one updatingTitle
			// for each
			//internal frame, rather than one for all internal frames. [Jon
			// Aquino]
			private boolean updatingTitle = false;
			private void updateTitle() {
				if (updatingTitle) {
					return;
				}
				updatingTitle = true;
				try {
					String newTitle = i.getTitle();
					if (newTitle.charAt(0) == '*') {
						newTitle = newTitle.substring(1);
					}
					if (!internalFrame.getLayerManager()
							.getLayersWithModifiedFeatureCollections()
							.isEmpty()) {
						newTitle = '*' + newTitle;
					}
					i.setTitle(newTitle);
				} finally {
					updatingTitle = false;
				}
			}
			public Object yield() {
				internalFrame.getLayerManager().addLayerListener(
						new LayerListener() {
							public void layerChanged(LayerEvent e) {
								if ((e.getType() == LayerEventType.METADATA_CHANGED)
										|| (e.getType() == LayerEventType.REMOVED)) {
									updateTitle();
								}
							}
							public void categoryChanged(CategoryEvent e) {
							}
							public void featuresChanged(FeatureEvent e) {
							}
						});
				i.addPropertyChangeListener(JInternalFrame.TITLE_PROPERTY,
						new PropertyChangeListener() {
							public void propertyChange(PropertyChangeEvent e) {
								updateTitle();
							}
						});
				return null;
			}
		}.yield();
	}
	private void setClosingBehaviour(final LayerManagerProxy proxy) {
		final JInternalFrame internalFrame = (JInternalFrame) proxy;
		internalFrame
				.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		internalFrame.addInternalFrameListener(new InternalFrameAdapter() {
			public void internalFrameClosing(InternalFrameEvent e) {
				internalFrameCloseHandler.close(internalFrame);
			}
		});
	}
	private Collection getInternalFramesAssociatedWith(LayerManager layerManager) {
		ArrayList internalFramesAssociatedWithLayerManager = new ArrayList();
		JInternalFrame[] internalFrames = getInternalFrames();
		for (int i = 0; i < internalFrames.length; i++) {
			if (internalFrames[i] instanceof LayerManagerProxy
					&& (((LayerManagerProxy) internalFrames[i])
							.getLayerManager() == layerManager)) {
				internalFramesAssociatedWithLayerManager.add(internalFrames[i]);
			}
		}
		return internalFramesAssociatedWithLayerManager;
	}
	public TaskFrame addTaskFrame() {
		TaskFrame f = addTaskFrame(createTask());
		return f;
	}
	public Task createTask() {
		Task task = new Task();
		//LayerManager shouldn't automatically add categories in its
		// constructor.
		//Sometimes we want to create a LayerManager with no categories
		//(e.g. in OpenProjectPlugIn). [Jon Aquino]
		task.getLayerManager().addCategory(StandardCategoryNames.WORKING);
		task.getLayerManager().addCategory(StandardCategoryNames.SYSTEM);
		task.setName("Task " + taskSequence++);
		return task;
	}
	public TaskFrame addTaskFrame(Task task) {
		return addTaskFrame(new TaskFrame(task, workbenchContext));
	}
	public TaskFrame addTaskFrame(TaskFrame taskFrame) {
		taskFrame.getTask().getLayerManager().addLayerListener(
				new LayerListener() {
					public void featuresChanged(FeatureEvent e) {
					}
					public void categoryChanged(CategoryEvent e) {
						toolBar.updateEnabledState();
					}
					public void layerChanged(LayerEvent layerEvent) {
						toolBar.updateEnabledState();
					}
				});
		addInternalFrame(taskFrame);
		taskFrame.getLayerViewPanel().getLayerManager()
				.getUndoableEditReceiver().add(
						new UndoableEditReceiver.Listener() {
							public void undoHistoryChanged() {
								toolBar.updateEnabledState();
							}
							public void undoHistoryTruncated() {
								toolBar.updateEnabledState();
								log("Undo history was truncated");
							}
						});
		return taskFrame;
	}
	public void flash(final HTMLFrame frame) {
		final Color originalColor = frame.getBackgroundColor();
		new Timer(100, new ActionListener() {
			private int tickCount = 0;
			public void actionPerformed(ActionEvent e) {
				try {
					tickCount++;
					frame
							.setBackgroundColor(((tickCount % 2) == 0) ? originalColor
									: Color.yellow);
					if (tickCount == 2) {
						Timer timer = (Timer) e.getSource();
						timer.stop();
					}
				} catch (Throwable t) {
					handleThrowable(t);
				}
			}
		}).start();
	}
	private void flashStatusMessage(final String message, final Color color) {
		new Timer(100, new ActionListener() {
			private int tickCount = 0;
			public void actionPerformed(ActionEvent e) {
				tickCount++;
				//This message is important, so overwrite whatever is on the
				// status bar. [Jon Aquino]
				setStatusBarText(message);
				setStatusBarTextHighlighted((tickCount % 2) == 0, color);
				if (tickCount == 4) {
					Timer timer = (Timer) e.getSource();
					timer.stop();
				}
			}
		}).start();
	}
	/**
	 * Can be called regardless of whether the current thread is the AWT event
	 * dispatch thread.
	 * 
	 * @param t
	 *                  Description of the Parameter
	 */
	public void handleThrowable(final Throwable t) {
		log(StringUtil.stackTrace(t));
		Component parent = this;
		Window[] ownedWindows = getOwnedWindows();
		for (int i = 0; i < ownedWindows.length; i++) {
			if (ownedWindows[i] instanceof Dialog
					&& ownedWindows[i].isVisible()
					&& ((Dialog) ownedWindows[i]).isModal()) {
				parent = ownedWindows[i];
				break;
			}
		}
		handleThrowable(t, parent);
	}
	public static void handleThrowable(final Throwable t, final Component parent) {
		t.printStackTrace(System.err);
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				ErrorDialog.show(parent, StringUtil.toFriendlyName(t.getClass()
						.getName()), toMessage(t), StringUtil.stackTrace(t));
			}
		});
	}
	private ArrayList lastFiveThrowableDates = new ArrayList() {
		public boolean add(Object o) {
			if (size() == 5) {
				remove(0);
			}
			return super.add(o);
		}
	};
	public static String toMessage(Throwable t) {
		String message;
		if (t.getLocalizedMessage() == null) {
			message = "No description was provided";
		} else if (t.getLocalizedMessage().toLowerCase().indexOf(
				"side location conflict") > -1) {
			message = t.getLocalizedMessage()
					+ " -- Check for invalid geometries.";
		} else {
			message = t.getLocalizedMessage();
		}
		return message + " ("
				+ StringUtil.toFriendlyName(t.getClass().getName()) + ")";
	}
	public boolean hasInternalFrame(JInternalFrame internalFrame) {
		JInternalFrame[] frames = desktopPane.getAllFrames();
		for (int i = 0; i < frames.length; i++) {
			if (frames[i] == internalFrame) {
				return true;
			}
		}
		return false;
	}
	public void removeInternalFrame(JInternalFrame internalFrame) {
		//Looks like #closeFrame is the proper way to remove an internal
		// frame.
		//It will activate the next frame. [Jon Aquino]
		desktopPane.getDesktopManager().closeFrame(internalFrame);
	}
	public void warnUser(String warning) {
		log("Warning: " + warning);
		flashStatusMessage(warning, Color.yellow);
	}
	public void zoomChanged(Envelope modelEnvelope) {
		toolBar.updateEnabledState();
	}
	void exitMenuItem_actionPerformed(ActionEvent e) {
		closeApplication();
	}
	void this_componentShown(ComponentEvent e) {
		try {
			//If the first internal frame is not a TaskWindow (as may be the
			// case in
			//custom workbenches), #updateEnabledState() will ensure that the
			//cursor-tool buttons are disabled. [Jon Aquino]
			toolBar.updateEnabledState();
		} catch (Throwable t) {
			handleThrowable(t);
		}
	}
	void this_windowClosing(WindowEvent e) {
		closeApplication();
	}
	void windowMenu_menuSelected(MenuEvent e) {
		//<<TODO:MAINTAINABILITY>> This algorithm is not robust. It assumes
		// the Window
		//menu has exactly one "regular" menu item (newWindowMenuItem). [Jon
		// Aquino]
		if (windowMenu.getItemCount() > 0
				&& windowMenu.getItem(0) != null
				&& windowMenu.getItem(0).getText().equals(
						AbstractPlugIn.createName(CloneWindowPlugIn.class))) {
			JMenuItem newWindowMenuItem = windowMenu.getItem(0);
			windowMenu.removeAll();
			windowMenu.add(newWindowMenuItem);
			windowMenu.addSeparator();
		} else {
			//ezLink doesn't have a Clone Window menu [Jon Aquino]
			windowMenu.removeAll();
		}
		final JInternalFrame[] frames = desktopPane.getAllFrames();
		for (int i = 0; i < frames.length; i++) {
			JMenuItem menuItem = new JMenuItem();
			//Increase truncation threshold from 20 to 40, for eziLink [Jon
			// Aquino]
			menuItem.setText(GUIUtil.truncateString(frames[i].getTitle(), 40));
			associate(menuItem, frames[i]);
			windowMenu.add(menuItem);
		}
		if (windowMenu.getItemCount() == 0) {
			//For ezLink [Jon Aquino]
			windowMenu.add(new JMenuItem("(No Windows)"));
		}
	}
	private void associate(JMenuItem menuItem, final JInternalFrame frame) {
		menuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					activateFrame(frame);
				} catch (Throwable t) {
					handleThrowable(t);
				}
			}
		});
	}
	private void closeApplication() {
		applicationExitHandler.exitApplication(this);
	}
	private Collection getLayersWithModifiedFeatureCollections() {
		ArrayList layersWithModifiedFeatureCollections = new ArrayList();
		for (Iterator i = getLayerManagers().iterator(); i.hasNext();) {
			LayerManager layerManager = (LayerManager) i.next();
			layersWithModifiedFeatureCollections.addAll(layerManager
					.getLayersWithModifiedFeatureCollections());
		}
		return layersWithModifiedFeatureCollections;
	}
	private Collection getLayerManagers() {
		//Multiple windows may point to the same LayerManager, so use
		//a Set. [Jon Aquino]
		HashSet layerManagers = new HashSet();
		JInternalFrame[] internalFrames = getInternalFrames();
		for (int i = 0; i < internalFrames.length; i++) {
			if (internalFrames[i] instanceof LayerManagerProxy) {
				layerManagers.add(((LayerManagerProxy) internalFrames[i])
						.getLayerManager());
			}
		}
		return layerManagers;
	}
	private void configureStatusLabel(JLabel label, int width) {
		label.setMinimumSize(new Dimension(width, (int) label.getMinimumSize()
				.getHeight()));
		label.setMaximumSize(new Dimension(width, (int) label.getMaximumSize()
				.getHeight()));
		label.setPreferredSize(new Dimension(width, (int) label
				.getPreferredSize().getHeight()));
	}
	private void jbInit() throws Exception {
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		this.setIconImage(icon.getImage());
		this.addComponentListener(new java.awt.event.ComponentAdapter() {
			public void componentShown(ComponentEvent e) {
				this_componentShown(e);
			}
		});
		this.getContentPane().setLayout(borderLayout1);
		this.addWindowListener(new java.awt.event.WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				this_windowClosing(e);
			}
		});
		this.setJMenuBar(menuBar);
		//This size is chosen so that when the user hits the Info tool, the
		// window
		//fits between the lower edge of the TaskFrame and the lower edge of
		// the
		//WorkbenchFrame. See the call to #setSize in InfoFrame. [Jon Aquino]
		setSize(900, 665);
		//OUTLINE_DRAG_MODE is excruciatingly slow in JDK 1.4.1, so don't use
		// it.
		//(although it's supposed to be fixed in 1.4.2, which has not yet been
		//released). (see Sun Java Bug ID 4665237). [Jon Aquino]
		//desktopPane.setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
		messageLabel.setOpaque(true);
		memoryLabel.setText("jLabel1");
		wmsLabel.setHorizontalAlignment(SwingConstants.LEFT);
		wmsLabel.setText(" ");
		this.getContentPane().add(statusPanel, BorderLayout.SOUTH);
		exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				exitMenuItem_actionPerformed(e);
			}
		});
		windowMenu.addMenuListener(new javax.swing.event.MenuListener() {
			public void menuCanceled(MenuEvent e) {
			}
			public void menuDeselected(MenuEvent e) {
			}
			public void menuSelected(MenuEvent e) {
				windowMenu_menuSelected(e);
			}
		});
		coordinateLabel.setBorder(BorderFactory.createLoweredBevelBorder());
		wmsLabel.setBorder(BorderFactory.createLoweredBevelBorder());
		coordinateLabel.setText(" ");
		statusPanel.setLayout(gridBagLayout1);
		statusPanel.setBorder(BorderFactory.createRaisedBevelBorder());
		messageLabel.setBorder(BorderFactory.createLoweredBevelBorder());
		messageLabel.setText(" ");
		timeLabel.setBorder(BorderFactory.createLoweredBevelBorder());
		timeLabel.setText(" ");
		memoryLabel.setBorder(BorderFactory.createLoweredBevelBorder());
		memoryLabel.setText(" ");
		menuBar.add(fileMenu);
		menuBar.add(windowMenu);
		getContentPane().add(toolBar, BorderLayout.NORTH);
		getContentPane().add(desktopPane, BorderLayout.CENTER);
		fileMenu.addSeparator();
		fileMenu.add(exitMenuItem);
		statusPanel.add(coordinateLabel, new GridBagConstraints(5, 1, 1, 1,
				0.0, 0.0, GridBagConstraints.WEST,
				GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
		statusPanel.add(timeLabel, new GridBagConstraints(2, 1, 1, 1, 0.0, 0.0,
				GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
		statusPanel.add(messageLabel, new GridBagConstraints(1, 1, 1, 1, 0.0,
				0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
		//Give memoryLabel the 1.0 weight. All the rest should have their
		// sizes
		//configured using #configureStatusLabel. [Jon Aquino]
		statusPanel.add(memoryLabel, new GridBagConstraints(3, 1, 1, 1, 1.0,
				0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
				new Insets(0, 0, 0, 0), 0, 0));
		statusPanel.add(wmsLabel, new GridBagConstraints(4, 1, 1, 1, 0.0, 0.0,
				GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,
						0, 0, 0), 0, 0));
	}
	private void position(JInternalFrame internalFrame) {
		final int STEP = 5;
		GUIUtil.Location location = null;
		if (internalFrame instanceof PrimaryInfoFrame) {
			primaryInfoFrameIndex++;
			int offset = (primaryInfoFrameIndex % 3) * STEP;
			location = new GUIUtil.Location(offset, true, offset, true);
		} else {
			positionIndex++;
			int offset = (positionIndex % 5) * STEP;
			location = new GUIUtil.Location(offset, false, offset, false);
		}
		GUIUtil.setLocation(internalFrame, location, desktopPane);
	}
	/**
	 * Fundamental Style classes (like BasicStyle, VertexStyle, and LabelStyle)
	 * cannot be removed, and are thus excluded from the choosable Style
	 * classes.
	 */
	public Set getChoosableStyleClasses() {
		return Collections.unmodifiableSet(choosableStyleClasses);
	}
	public void addChoosableStyleClass(Class choosableStyleClass) {
		Assert.isTrue(ChoosableStyle.class
				.isAssignableFrom(choosableStyleClass));
		choosableStyleClasses.add(choosableStyleClass);
	}
	private HashMap keyCodeAndModifiersToPlugInAndEnableCheckMap = new HashMap();
	/**
	 * Adds a keyboard shortcut for a plugin. logs plugin exceptions.
	 * 
	 * note - attaching to keyCode 'a', modifiers =1 will detect shift-A
	 * events. It will *not* detect caps-lock-'a'. This is due to
	 * inconsistencies in java.awt.event.KeyEvent. In the unlikely event you
	 * actually do want to also also attach to caps-lock-'a', then make two
	 * shortcuts - one to keyCode 'a' and modifiers =1 (shift-A) and one to
	 * keyCode 'A' and modifiers=0 (caps-lock A).
	 * 
	 * For more details, see the java.awt.event.KeyEvent class - it has a full
	 * explaination.
	 * 
	 * @param keyCode
	 *                  What key to attach to (See java.awt.event.KeyEvent)
	 * @param modifiers 0=
	 *                  none, 1=shift, 2= cntrl, 8=alt, 3=shift+cntrl, etc... See the
	 *                  modifier mask constants in the Event class
	 * @param plugIn
	 *                  What plugin to execute
	 * @param enableCheck
	 *                  Is the key enabled at the moment?
	 */
	public void addKeyboardShortcut(final int keyCode, final int modifiers,
			final PlugIn plugIn, final EnableCheck enableCheck) {
		//Overwrite existing shortcut [Jon Aquino]
		keyCodeAndModifiersToPlugInAndEnableCheckMap.put(keyCode + ":"
				+ modifiers, new Object[]{plugIn, enableCheck});
	}
	private void installKeyboardShortcutListener() {
		addEasyKeyListener(new KeyListener() {
			public void keyTyped(KeyEvent e) {
			}
			public void keyReleased(KeyEvent e) {
			}
			public void keyPressed(KeyEvent e) {
				Object[] plugInAndEnableCheck = (Object[]) keyCodeAndModifiersToPlugInAndEnableCheckMap
						.get(e.getKeyCode() + ":" + e.getModifiers());
				if (plugInAndEnableCheck == null) {
					return;
				}
				PlugIn plugIn = (PlugIn) plugInAndEnableCheck[0];
				EnableCheck enableCheck = (EnableCheck) plugInAndEnableCheck[1];
				if (enableCheck != null && enableCheck.check(null) != null) {
					return;
				}
				//#toActionListener handles checking if the plugIn is a
				// ThreadedPlugIn,
				//and making calls to UndoableEditReceiver if necessary. [Jon
				// Aquino 10/15/2003]
				AbstractPlugIn.toActionListener(plugIn, workbenchContext,
						new TaskMonitorManager()).actionPerformed(null);
			}
		});
	}
	//==========================================================================
	// Applications (such as EziLink) want to override the default JUMP
	// frame closing behaviour and application exit behaviour with their own
	// behaviours.
	//
	InternalFrameCloseHandler internalFrameCloseHandler = new DefaultInternalFrameCloser();
	ApplicationExitHandler applicationExitHandler = new DefaultApplicationExitHandler();
	public InternalFrameCloseHandler getInternalFrameCloseHandler() {
		return internalFrameCloseHandler;
	}
	public void setInternalFrameCloseHandler(InternalFrameCloseHandler value) {
		internalFrameCloseHandler = value;
	}
	public ApplicationExitHandler getApplicationExitHandler() {
		return applicationExitHandler;
	}
	public void setApplicationExitHandler(ApplicationExitHandler value) {
		applicationExitHandler = value;
	}
	private class DefaultInternalFrameCloser implements
			InternalFrameCloseHandler {
		public void close(JInternalFrame internalFrame) {
			if (internalFrame instanceof TaskFrame) {
				closeTaskFrame((TaskFrame) internalFrame);
			} else {
				GUIUtil.dispose(internalFrame, desktopPane);
			}
		}
	}
	private class DefaultApplicationExitHandler implements
			ApplicationExitHandler {
		public void exitApplication(JFrame mainFrame) {
			if (confirmClose("Exit JUMP",
					getLayersWithModifiedFeatureCollections())) {
				//PersistentBlackboardPlugIn listens for when the workbench is
				// hidden [Jon Aquino]
				setVisible(false);
				//Invoke System#exit after all pending GUI events have been
				// fired
				//(e.g. the hiding of this WorkbenchFrame) [Jon Aquino]
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						System.exit(0);
					}
				});
			}
		}
	}
	private void closeTaskFrame(TaskFrame taskFrame) {
		LayerManager layerManager = taskFrame.getLayerManager();
		if (getInternalFramesAssociatedWith(layerManager).size() == 1) {
			Collection modifiedItems = layerManager
					.getLayersWithModifiedFeatureCollections();
			if (confirmClose("Close Task", modifiedItems)) {
				GUIUtil.dispose(taskFrame, desktopPane);
				layerManager.dispose();
			}
		} else {
			GUIUtil.dispose(taskFrame, desktopPane);
		}
	}
	private boolean confirmClose(String action, Collection modifiedLayers) {
		if (modifiedLayers.isEmpty()) {
			return true;
		}
		JOptionPane pane = new JOptionPane(StringUtil.split(modifiedLayers
				.size()
				+ " dataset"
				+ StringUtil.s(modifiedLayers.size())
				+ " "
				+ ((modifiedLayers.size() > 1) ? "have" : "has")
				+ " been modified ("
				+ ((modifiedLayers.size() > 3) ? "e.g. " : "")
				+ StringUtil.toCommaDelimitedString(new ArrayList(
						modifiedLayers).subList(0, Math.min(3, modifiedLayers
						.size()))) + "). Continue?", 80),
				JOptionPane.WARNING_MESSAGE);
		pane.setOptions(new String[]{action, "Cancel"});
		pane.createDialog(this, "JUMP").setVisible(true);
		return pane.getValue().equals(action);
	}
}
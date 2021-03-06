package com.vividsolutions.jump.coordsys;

import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JOptionPane;

import com.vividsolutions.jump.workbench.model.Layer;
import com.vividsolutions.jump.workbench.plugin.AbstractPlugIn;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;
import com.vividsolutions.jump.workbench.ui.EditTransaction;

/** 
 * Implements a {@link com.vividsolutions.jump.workbench.plugin.PlugIn
 * PlugIn} that allows the user to change coordinate systems.
 *
 */
public class ChangeCoordinateSystemPlugIn extends AbstractPlugIn {
    public boolean execute(PlugInContext context) throws Exception {
        //Don't make this plug-in undoable -- it's a lot of data to store in memory [Jon Aquino]
        context.getLayerManager().getUndoableEditReceiver()
               .reportIrreversibleChange();
        
        CoordinateSystem destination = (CoordinateSystem) JOptionPane
                .showInputDialog(context.getWorkbenchFrame(),
                        "Coordinate system for task:", getName(),
                        JOptionPane.PLAIN_MESSAGE, null, new ArrayList(
                                CoordinateSystemRegistry
                                        .instance(context.getWorkbenchContext()
                                                .getBlackboard())
                                        .getCoordinateSystems())
                                .toArray(),
                        context.getLayerManager().getCoordinateSystem());

        if (destination == null) {
            return false;
        }

        if (context.getLayerManager().getCoordinateSystem() == destination) {
            return true;
        }

        if (Reprojector.instance().wouldChangeValues(context.getLayerManager()
                                                                .getCoordinateSystem(),
                    destination)) {
            //Two-phase commit [Jon Aquino]
            ArrayList transactions = new ArrayList();

            for (Iterator i = context.getLayerManager().iterator();
                    i.hasNext();) {
                Layer layer = (Layer) i.next();
                EditTransaction transaction = new EditTransaction(layer.getFeatureCollectionWrapper()
                                                                       .getFeatures(),
                        getName(), layer, isRollingBackInvalidEdits(context),
                        false, context.getLayerViewPanel());

                for (int j = 0; j < transaction.size(); j++) {
                    Reprojector.instance().reproject(transaction.getGeometry(j),
                        context.getLayerManager().getCoordinateSystem(),
                        destination);
                }

                transactions.add(transaction);
            }

            EditTransaction.commit(transactions);
        }

        for (Iterator i = context.getLayerManager().iterator(); i.hasNext();) {
            Layer layer = (Layer) i.next();
            layer.getFeatureCollectionWrapper().getFeatureSchema()
                 .setCoordinateSystem(destination);
        }

        context.getLayerManager().setCoordinateSystem(destination);
        if (context.getLayerViewPanel() != null) {
            context.getLayerViewPanel().getViewport().zoomToFullExtent();
        }

        return true;
    }
}

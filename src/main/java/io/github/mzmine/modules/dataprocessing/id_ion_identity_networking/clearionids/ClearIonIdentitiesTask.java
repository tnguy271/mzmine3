/*
 * Copyright 2006-2015 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine 2; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.clearionids;


import io.github.msdk.MSDKRuntimeException;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClearIonIdentitiesTask extends AbstractTask {

  // Logger.
  private static final Logger LOG = Logger.getLogger(ClearIonIdentitiesTask.class.getName());

  private AtomicInteger finishedRows = new AtomicInteger(0);
  private int totalRows;
  private final FeatureList featureList;
  private final ParameterSet parameters;
  private final MZmineProject project;

  /**
   * Create the task.
   *
   * @param parameterSet the parameters.
   */
  public ClearIonIdentitiesTask(final MZmineProject project, final ParameterSet parameterSet,
      final FeatureList featureLists) {
    this.project = project;
    this.featureList = featureLists;
    parameters = parameterSet;
    totalRows = 0;
  }

  @Override
  public double getFinishedPercentage() {
    return totalRows == 0 ? 0 : finishedRows.get() / (double) totalRows;
  }

  @Override
  public String getTaskDescription() {
    return "Clearing ion identities and networks in " + featureList.getName() + " ";
  }

  @Override
  public void run() {
    try {
      setStatus(TaskStatus.PROCESSING);
      LOG.info("Clearing ion identities and networks in " + featureList.getName());

      // filter
      doFiltering(featureList, finishedRows);

      // Done.
      setStatus(TaskStatus.FINISHED);
      LOG.info("Clearing ion identities and networks in " + featureList);
    } catch (Exception t) {
      LOG.log(Level.SEVERE, "Clearing ion identities and networks error", t);
      setStatus(TaskStatus.ERROR);
      setErrorMessage(t.getMessage());
      throw new MSDKRuntimeException(t);
    }
  }


  /**
   * Delete all networks smaller min size
   * 
   * @param pkl
   * @param finishedRows pointer to finished networks
   * @throws Exception
   */
  public static void doFiltering(FeatureList pkl, AtomicInteger finishedRows) throws Exception {
    pkl.stream().filter(FeatureListRow::hasIonIdentity).forEach(r -> {
      r.clearIonIdentites();
      // Notify the GUI about the change in the project
      MZmineCore.getProjectManager().getCurrentProject().notifyObjectChanged(r, false);
    });
    // Repaint the window to reflect the change in the peak list
    if (MZmineCore.getDesktop().getMainWindow() != null)
      MZmineCore.getDesktop().getMainWindow().repaint();
  }

  /**
   * Create a copy of a peak list row.
   *
   * @param row the row to copy.
   * @return the newly created copy.
   */
  private static FeatureListRow copyRow(final FeatureListRow row) {
    // Copy the peak list row.
    final FeatureListRow newRow = new SimpleFeatureListRow(row.getID());
    FeatureUtils.copyFeatureListRowProperties(row, newRow);

    // Copy the peaks.
    for (final Feature feature : row.getFeatures()) {
      final Feature newFeature = new SimpleFeature(feature);
      FeatureUtils.copyFeatureProperties(feature, newFeature);
      newRow.addFeature(feature.getDataFile(), newFeature);
    }

    return newRow;
  }

}

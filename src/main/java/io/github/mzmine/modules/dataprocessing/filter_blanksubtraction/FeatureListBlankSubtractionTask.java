/*
 * Copyright (c) 2004-2022 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.filter_blanksubtraction;

import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureListRowSorter;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author SteffenHeu steffen.heuckeroth@gmx.de / s_heuc03@uni-muenster.de
 */
public class FeatureListBlankSubtractionTask extends AbstractTask {

  private static Logger logger = Logger.getLogger(FeatureListBlankSubtractionTask.class.getName());
  private final int totalRows;
  private final int minBlankDetections;
  private final String suffix;
  private final boolean createDeletedFeatureList;
  private final String suffixDeleted;
  private final boolean checkFoldChange;
  private final double foldChange;
  private final RatioType ratioType;
  private final QuantType quantType;

  private AtomicInteger processedRows = new AtomicInteger(0);
  private MZmineProject project;
  private FeatureListBlankSubtractionParameters parameters;
  private RawDataFilesSelection blankSelection;
  private List<RawDataFile> blankRaws;
  private ModularFeatureList originalFeatureList;

  public FeatureListBlankSubtractionTask(MZmineProject project,
      FeatureListBlankSubtractionParameters parameters, @Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);

    this.project = project;
    this.parameters = parameters;
    this.blankSelection = parameters.getParameter(
        FeatureListBlankSubtractionParameters.blankRawDataFiles).getValue();
    this.blankRaws = List.of(blankSelection.getMatchingRawDataFiles().clone());
    this.originalFeatureList = parameters.getParameter(
            FeatureListBlankSubtractionParameters.alignedPeakList).getValue()
        .getMatchingFeatureLists()[0];
    this.minBlankDetections = parameters.getParameter(
        FeatureListBlankSubtractionParameters.minBlanks).getValue();
    this.suffix = parameters.getParameter(FeatureListBlankSubtractionParameters.suffix).getValue();
    this.createDeletedFeatureList = parameters.getParameter(
        FeatureListBlankSubtractionParameters.createDeleted).getValue();
    this.suffixDeleted = parameters.getParameter(
        FeatureListBlankSubtractionParameters.suffixDeleted).getValue();
    checkFoldChange = parameters.getParameter(FeatureListBlankSubtractionParameters.foldChange)
        .getValue();
    foldChange = parameters.getParameter(FeatureListBlankSubtractionParameters.foldChange)
        .getEmbeddedParameter().getValue();
    this.ratioType =
        parameters.getParameter(FeatureListBlankSubtractionParameters.ratioType).getValue()
            .equals("Maximum") ? RatioType.Maximum : RatioType.Average;
    this.quantType =
        parameters.getParameter(FeatureListBlankSubtractionParameters.quantType).getValue()
            .equals("Height") ? QuantType.Height : QuantType.Area;
    totalRows = originalFeatureList.getNumberOfRows();
    logger.info(
        String.format("Blank subtraction with quantifier '%s' and ratio '%s'", this.quantType,
            this.ratioType));

    setStatus(TaskStatus.WAITING);
    logger.setLevel(Level.FINEST);
  }

  @Override
  public String getTaskDescription() {
    return "Blank subtraction task on " + originalFeatureList.getName();
  }

  @Override
  public double getFinishedPercentage() {
    return processedRows.get() / (double) totalRows;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    if (!checkBlankSelection(originalFeatureList, blankRaws)) {
      setErrorMessage("Feature list " + originalFeatureList.getName()
          + " does no contain all selected blank raw data files.");
      setStatus(TaskStatus.ERROR);
      return;
    }

    // get the files that are not considered as blank
    final List<RawDataFile> nonBlankFiles = new ArrayList<>();
    final List<RawDataFile> blankFiles = new ArrayList<>();
    for (RawDataFile file : originalFeatureList.getRawDataFiles()) {
      if (!blankRaws.contains(file)) {
        nonBlankFiles.add(file);
      } else {
        blankFiles.add(file);
      }
    }
    logger.finest(() -> originalFeatureList.getName() + " contains " + nonBlankFiles.size()
        + " raw data files not classified as blank.");

    final ModularFeatureList result = new ModularFeatureList(
        originalFeatureList.getName() + " " + suffix, getMemoryMapStorage(), nonBlankFiles);
    final ModularFeatureList blankResult = new ModularFeatureList(
        originalFeatureList.getName() + " " + suffixDeleted, getMemoryMapStorage(),
        originalFeatureList.getRawDataFiles());

    originalFeatureList.getRowTypes().values().forEach(result::addRowType);
    nonBlankFiles.forEach(f -> result.setSelectedScans(f, originalFeatureList.getSeletedScans(f)));

    originalFeatureList.getRowTypes().values().forEach(blankResult::addRowType);
    originalFeatureList.getRawDataFiles()
        .forEach(f -> blankResult.setSelectedScans(f, originalFeatureList.getSeletedScans(f)));

    final List<FeatureListRow> filteredRows = new ArrayList<>();
    final List<FeatureListRow> removedRows = new ArrayList<>();
    for (FeatureListRow originalRow : originalFeatureList.getRows()) {
      int numBlankDetections = 0;
      int numFeatures = 0;

      final ModularFeatureListRow filteredRow = new ModularFeatureListRow(result,
          originalRow.getID(), (ModularFeatureListRow) originalRow, false);
      final ModularFeatureListRow removedRow = new ModularFeatureListRow(blankResult,
          originalRow.getID(), (ModularFeatureListRow) originalRow, false);

      for (RawDataFile blankRaw : blankRaws) {
        if (originalRow.hasFeature(blankRaw)) {
          // save blank detections to a blank-list
          final Feature f = originalRow.getFeature(blankRaw);
          removedRow.addFeature(blankRaw, new ModularFeature(blankResult, f));
          numBlankDetections++;
        }
      }

      if (numBlankDetections < minBlankDetections || checkFoldChange) {
        final double blankIntensity =
            checkFoldChange ? getBlankIntensity(originalRow, blankRaws, quantType, ratioType) : 1d;
        // copy features from non-blank files.
        for (RawDataFile file : nonBlankFiles) {
          final Feature f = originalRow.getFeature(file);
          // check if there's actually a feature
          if (f != null && f.getFeatureStatus() != FeatureStatus.UNKNOWN) {
            // check validity
            double quant = getFeatureQuantifier(f, quantType);
            if (!checkFoldChange || quant / blankIntensity >= foldChange) {
              filteredRow.addFeature(file, new ModularFeature(result, f));
              numFeatures++;
            } else {
              // or put feature to not-used list
              removedRow.addFeature(file, new ModularFeature(blankResult, f));
            }
          }
        }
      }

      // copy row types
      if (numFeatures > 0) {
        // use feature in the new results feature list
        filteredRows.add(filteredRow);
      }
      if (numBlankDetections > 0 && numFeatures == 0) {
        // put not-used features on the removed list
        removedRows.add(removedRow);
      }

      processedRows.getAndIncrement();
    }

    // create the list with not-used features first so the used features are the last list to be
    // created and can be used in the next step when the "last-list" option is used
    if (this.createDeletedFeatureList) {
      removedRows.sort(FeatureListRowSorter.DEFAULT_RT);
      removedRows.forEach(blankResult::addRow);

      blankResult.getAppliedMethods().addAll(originalFeatureList.getAppliedMethods());
      blankResult.getAppliedMethods().add(
          new SimpleFeatureListAppliedMethod(FeatureListBlankSubtractionModule.class, parameters,
              getModuleCallDate()));
      project.addFeatureList(blankResult);
    }

    // create the filtered list so that the next step can use it
    filteredRows.sort(FeatureListRowSorter.DEFAULT_RT);
    filteredRows.forEach(result::addRow);

    result.getAppliedMethods().addAll(originalFeatureList.getAppliedMethods());
    result.getAppliedMethods().add(
        new SimpleFeatureListAppliedMethod(FeatureListBlankSubtractionModule.class, parameters,
            getModuleCallDate()));
    project.addFeatureList(result);

    setStatus(TaskStatus.FINISHED);
  }

  private double getFeatureQuantifier(Feature f, QuantType quantType) {
    if (quantType == QuantType.Height) {
      return f.getHeight();
    } else if (quantType == QuantType.Area) {
      return f.getArea();
    }
    throw new RuntimeException("Unknown parameter");
  }

  private double getBlankIntensity(FeatureListRow row, Collection<RawDataFile> blankRaws,
      QuantType quantType, RatioType ratioType) {
    double intensity = 0d;
    int numDetections = 0;

    for (RawDataFile file : blankRaws) {
      final Feature f = row.getFeature(file);
      if (f != null && f.getFeatureStatus() != FeatureStatus.UNKNOWN) {
        double quant = getFeatureQuantifier(f, quantType);

        if (ratioType == RatioType.Average) {
          intensity += quant;
          numDetections++;
        } else if (ratioType == RatioType.Maximum) {
          intensity = Math.max(quant, intensity);
        }
      }
    }

    return ratioType == RatioType.Average && numDetections != 0 ? intensity / numDetections
        : intensity;
  }

  private boolean checkBlankSelection(FeatureList aligned, List<RawDataFile> blankRaws) {

    List<RawDataFile> flRaws = aligned.getRawDataFiles();

    for (int i = 0; i < blankRaws.size(); i++) {
      boolean contained = false;

      for (RawDataFile flRaw : flRaws) {
        if (blankRaws.get(i) == flRaw) {
          contained = true;
        }
      }

      if (!contained) {
        final int i1 = i;
        logger.info(() -> "Feature list " + aligned.getName() + " does not contain raw data files "
            + blankRaws.get(i1).getName());
        return false;
      }
    }

    logger.finest(
        () -> "Feature list " + aligned.getName() + " contains all selected blank raw data files.");
    return true;
  }

  enum RatioType {
    Average, Maximum
  }

  enum QuantType {
    Height, Area
  }
}

/*
 * Copyright 2006-2022 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package io.github.mzmine.modules.visualization.massvoltammogram;

import com.google.common.collect.Range;
import gnu.trove.list.array.TDoubleArrayList;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.IonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.id_ecmscalcpotential.EcmsUtils;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsParameter;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.util.FeatureListRowSorter;
import io.github.mzmine.util.collections.BinarySearch;
import io.github.mzmine.util.scans.ScanUtils;
import java.awt.Color;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import org.math.plot.Plot3DPanel;

public class MassvoltammogramUtils {


  /**
   * Extracts all scans needed to draw the massvoltammogram from a raw data file.
   *
   * @param rawDataFile        Raw data file the scans will be drawn from.
   * @param scanSelection      The scan selection filter.
   * @param delayTime          Delay time between EC-cell and MS in s.
   * @param startPotential     The potential the ramp starts at in mV.
   * @param endPotential       The potential the ramp ends at in mV.
   * @param potentialSpeedRamp Speed of the potential ramp in mV/s.
   * @param stepSize           Potential step size between the individual scans in mV.
   * @return Returns a list of all scans with the given step size inside the potential range.
   * Thereby every scan is represented by a multidimensional array. Each scans datapoints are
   * represented as single arrays of the multidimensional array. In every singe array the mz-value
   * is stored at index 0, the corresponding intensity-value at index 1 and the voltage-value at
   * index 2.
   */
  public static List<double[][]> getScansFromRawDataFile(RawDataFile rawDataFile,
      ScanSelection scanSelection, double delayTime, double startPotential, double endPotential,
      double potentialSpeedRamp, double stepSize) {

    final List<double[][]> scans = new ArrayList<>();

    final NumberFormat mzFormat = MZmineCore.getConfiguration().getMZFormat();

    //Setting the starting potential
    double potential = startPotential;

    //Adding scans with the given step size until the maximal potential is reached.
    while (Math.abs(potential) <= Math.abs(endPotential)) {
      final float rt = EcmsUtils.getRtAtPotential(delayTime, potentialSpeedRamp, potential);
      final Scan scan = scanSelection.getScanAtRt(rawDataFile, rt);

      //Breaking the loop if the calculated rt exceeds the max rt of the data file.
      if (scan == null) {
        break;
      }

      final double[][] scanAsArray = new double[scan.getNumberOfDataPoints()][3];

      for (int j = 0; j < scan.getNumberOfDataPoints(); j++) {
        scanAsArray[j][0] = Double.parseDouble(mzFormat.format(scan.getMzValue(j)));
        scanAsArray[j][1] = scan.getIntensityValue(j);
        scanAsArray[j][2] = potential;
      }

      scans.add(scanAsArray);
      potential = potential + stepSize;
    }

    return scans;
  }


  public static List<double[][]> getScansFromFeatureList(ModularFeatureList featureList,
      double delayTime, double startPotential, double endPotential, double potentialSpeedRamp,
      double stepSize) {

    //Setting the number format for the mz-values.
    final NumberFormat mzFormat = MZmineCore.getConfiguration().getMZFormat();

    //Getting the rows from the feature list and sorting them to ascending mz-values.
    List<FeatureListRow> rows = featureList.getRows();
    rows.sort(FeatureListRowSorter.MZ_ASCENDING);

    //Initializing a list to add the created scans to.
    final List<double[][]> scans = new ArrayList<>();

    //Calculating the +- rt tolerance for a feature around the potential.
    final float timeBetweenScansInS = (float) (stepSize / potentialSpeedRamp);
    final double rtToleranceInMin = (timeBetweenScansInS / 2) / 60;

    //Setting the potential to the start potential
    double potential = startPotential;

    //Creating a scan for every potential value until the end potential is reached.
    while (Math.abs(potential) <= Math.abs(endPotential)) {

      //Calculating the rt fot the given applied potential.
      final float rt = (EcmsUtils.getRtAtPotential(delayTime, potentialSpeedRamp, potential));

      //Initializing lists to add the mz-values and the intensity-values to.
      final List<Double> mzs = new ArrayList<>();
      final List<Double> intensities = new ArrayList<>();

      //Going over all the rows in the feature list and adding the intensity if the rt matches.
      for (FeatureListRow row : rows) {

        //Getting the feature data from the row.
        final Feature feature = row.getBestFeature();
        final IonTimeSeries<? extends Scan> featureData = feature.getFeatureData();

        //Searching for the index of the features closest rt to the potential rt.
        final int index = BinarySearch.binarySearch(rt, true, featureData.getNumberOfValues(),
            value -> featureData.getRetentionTime(value));

        //Calculating the rt value for the found index.
        final float foundRt = featureData.getRetentionTime(index);

        //Adding the features mz and intensity if the difference between features rt and potential rt is within the tolerance.
        if (Math.abs(rt - foundRt) < rtToleranceInMin) {

          mzs.add(Double.parseDouble(mzFormat.format(feature.getMZ())));
          intensities.add(featureData.getIntensity(index));
        }
      }

      //Writing the scans data to a double array.
      double[][] scan = new double[mzs.size()][3];

      for (int i = 0; i < scan.length; i++) {

        scan[i][0] = mzs.get(i);
        scan[i][1] = intensities.get(i);
        scan[i][2] = potential;
      }

      //Adding the scan to the list of scans.
      scans.add(scan);

      //Increasing the potential by the potential step size.
      potential = potential + stepSize;
    }

    return scans;
  }

  /**
   * @param scans   List of scans the spectra will be generated from.
   * @param mzRange Range of m/z-values the spectrum will contain.
   * @return Returns list of multidimensional arrays, with each of them containing m/z, intensity
   * and voltage values of on spectrum
   */
  public static List<double[][]> extractMZRangeFromScan(List<double[][]> scans,
      Range<Double> mzRange) {

    final List<double[][]> sprectra = new ArrayList<>();
    final double minMZ = getMinMZ(scans);
    final double minMzUserInput = mzRange.lowerEndpoint();
    final double maxMzUserInput = mzRange.upperEndpoint();

    for (double[][] scan : scans) {

      //Initializing DoubleArrayLists to add the m/z and intensity values to.
      final TDoubleArrayList mzs = new TDoubleArrayList();
      final TDoubleArrayList intensities = new TDoubleArrayList();

      //Saving the voltage of the scan.
      final double voltage = scan[0][2];

      //Filling the minimal m/z-value with the first recorded intensity within the m/z-Range.
      for (double[] datapoint : scan) {
        final double mz = datapoint[0];
        if (mz == minMzUserInput) {
          break;
        } else if (mz > minMzUserInput && minMzUserInput > minMZ) {
          mzs.add(minMzUserInput);
          intensities.add(datapoint[1]);
          break;
        }
      }

      //Extracting values inside the m/z-range from the scan.
      for (int i = 0; i < scan.length; i++) {
        final double mz = scan[i][0];
        if (mzRange.contains(mz)) {
          intensities.add(scan[i][1]);
          mzs.add(mz);
        }

        //Filling the maximal m/z-value with the last recorded intensity within the m/z-range.
        if (mz == maxMzUserInput) {
          break;
        } else if (mz > maxMzUserInput) {
          mzs.add(maxMzUserInput);
          intensities.add(scan[i - 1][1]);
          break;
        }
      }

      //Adding the m/z, intensity and voltage values to the multidimensional array.
      final double[][] spectrum = new double[mzs.size()][3];
      for (int i = 0; i < spectrum.length; i++) {
        spectrum[i][0] = mzs.get(i);
        spectrum[i][1] = intensities.get(i);
        spectrum[i][2] = voltage;
      }

      //Adding the multidimensional array to the List.
      sprectra.add(spectrum);
    }
    return sprectra;
  }


  /**
   * Finds a power of 10 to scale the z-axis by.
   *
   * @param maxIntensity The max intensity of the dataset.
   * @return Returns the divisor all intensities need to be divided by.
   */
  public static double getDivisor(double maxIntensity) {

    //Getting for the next smallest power of ten to use as the divisor.
    double power = Math.log10(maxIntensity);

    return Math.pow(10, Math.floor(power));
  }


  /**
   * Removes neighbouring zeros from the whole dataset.
   *
   * @param scans The list of scans the excess zeros will be removed from.
   * @return Returns the scan data without neighbouring zeros as a list of multidimensional arrays.
   */
  public static List<double[][]> removeExcessZeros(List<double[][]> scans) {

    //Initialize a list of multidimensional double-array to store the scan data in.
    final List<double[][]> filteredScans = new ArrayList<>();

    for (double[][] scan : scans) {
      //Extracting the potential from the scan.
      final double potential = scan[0][2];

      //Removing all excess zeros from the scan.
      final double[][] filteredScan = ScanUtils.removeExtraZeros(scan);

      //Determining the number of datapoints the scan contains.
      final int numberDP = filteredScan.length;

      //Initializing a multidimensional double array to store the scan data in.
      final double[][] filteredScanWithVoltage = new double[numberDP][3];

      //Adding the scan data to the array
      for (int i = 0; i < numberDP; i++) {
        filteredScanWithVoltage[i][0] = filteredScan[i][0];
        filteredScanWithVoltage[i][1] = filteredScan[i][1];
        filteredScanWithVoltage[i][2] = potential;
      }

      //Adding the array to the List of arrays.
      filteredScans.add(filteredScanWithVoltage);
    }
    return filteredScans;
  }

  /**
   * Removes datapoints with low intensity values. Keeps datapoints with intensity of 0.
   *
   * @param scans        Dataset the datapoints will be removed from.
   * @param maxIntensity Max intensity of all datapoints in the dataset.
   * @return Returns a list of multidimensional double arrays with the filtered scan data. Thereby
   * all signals lower than 0.1% of the max intensity are considered as irrelevant for the
   * visualisation and are removed.
   */
  public static List<double[][]> removeNoise(List<double[][]> scans, double maxIntensity) {

    final List<double[][]> filteredScans = new ArrayList<>();

    for (double[][] scan : scans) {

      final int numDatapoints = scan.length;

      //Initializing ArrayLists to save the filtered data to.
      final TDoubleArrayList filteredMZ = new TDoubleArrayList();
      final TDoubleArrayList filteredIntensities = new TDoubleArrayList();

      //Saving the voltage of the scan.
      final double voltage = scan[0][2];

      //Initializing the intensity threshold.
      final double intensityThreshold =
          maxIntensity * 0.001; // Signals lower than 0.1% of the max intensity will be removed.

      //Adding the first value of the scan.
      filteredMZ.add(scan[0][0]);
      filteredIntensities.add(scan[0][1]);

      //Adding all other datapoints if the intensity is 0 or bigger than the intensity threshold.
      for (int i = 1; i < numDatapoints - 1; i++) {
        if (scan[i][1] > intensityThreshold || scan[i][1] == 0) {
          filteredMZ.add(scan[i][0]);           //Adding the m/z-value.
          filteredIntensities.add(scan[i][1]);  //Adding the intensity-value.
        }
      }

      //Adding the last value of the scan.
      filteredMZ.add(scan[numDatapoints - 1][0]);
      filteredIntensities.add(scan[numDatapoints - 1][1]);

      final int numFilteredDatapoints = filteredMZ.size();

      //Initializing an array to save the filtered values to.
      final double[][] filteredScan = new double[numFilteredDatapoints][3];

      //Writing the filtered values to the new array.
      for (int i = 0; i < numFilteredDatapoints; i++) {
        filteredScan[i][0] = filteredMZ.get(i);
        filteredScan[i][1] = filteredIntensities.get(i);
        filteredScan[i][2] = voltage;
      }

      filteredScans.add(filteredScan);
    }

    return filteredScans;
  }


  /**
   * @param spectra List containing the spectra that will be drawn as multidimensional arrays.
   * @param divisor Power of 10 used for scaling the z-axis.
   * @param plot    3D plot the spectra should be added to.
   */
  public static void addSpectraToPlot(List<double[][]> spectra, double divisor, Plot3DPanel plot) {

    for (double[][] spectrum : spectra) {

      //Initializing a double array for every of the three parameters.
      final double[] mzs = new double[spectrum.length];
      final double[] voltage = new double[spectrum.length];
      final double[] intensities = new double[spectrum.length];

      for (int i = 0; i < mzs.length; i++) {
        mzs[i] = spectrum[i][0];
        intensities[i] = spectrum[i][1] / divisor;
        voltage[i] = spectrum[0][2];
      }

      //Adding the parameter specific arrays to the plot.
      plot.addLinePlot("Spectrum at " + spectrum[0][2] + " mV.", Color.black, mzs, voltage,
          intensities);
    }
  }


  /**
   * @param superscript Integer that will be converted to superscript.
   * @return Returns the integer converted to superscript string.
   */
  public static String toSupercript(int superscript) {
    final StringBuilder output = new StringBuilder();

    //Converting the input integer to a string.
    final String input = Integer.toString(superscript);

    //Exchanging every numeric character of the string to superscript.
    for (int i = 0; i < input.length(); i++) {
      if (Character.getNumericValue(input.charAt(i)) == 0) {
        output.append("⁰");
      } else if (Character.getNumericValue(input.charAt(i)) == 1) {
        output.append("¹");
      } else if (Character.getNumericValue(input.charAt(i)) == 2) {
        output.append("²");
      } else if (Character.getNumericValue(input.charAt(i)) == 3) {
        output.append("³");
      } else if (Character.getNumericValue(input.charAt(i)) == 4) {
        output.append("⁴");
      } else if (Character.getNumericValue(input.charAt(i)) == 5) {
        output.append("⁵");
      } else if (Character.getNumericValue(input.charAt(i)) == 6) {
        output.append("⁶");
      } else if (Character.getNumericValue(input.charAt(i)) == 7) {
        output.append("⁷");
      } else if (Character.getNumericValue(input.charAt(i)) == 8) {
        output.append("⁸");
      } else if (Character.getNumericValue(input.charAt(i)) == 9) {
        output.append("⁹");
      }
    }
    return output.toString();
  }

  /**
   * Method to get the min m/z-value from a list of scans.
   *
   * @param scans The scans.
   * @return Returns the minimal m/z-value.
   */
  public static double getMinMZ(List<double[][]> scans) {

    //Setting the absolute minimal m/z-value equal to the first scans minimal m/z-value.
    double[][] firstScan = scans.get(0);
    double minMZ = firstScan[0][0];

    //Checking all the other scans in the list weather there is an even smaller m/z-value.
    for (int i = 1; i < scans.size(); i++) {
      double[][] scan = scans.get(i);
      double minMzScan = scan[0][0];
      if (minMzScan < minMZ) {
        minMZ = minMzScan;
      }
    }

    return minMZ;
  }

  /**
   * Scans a list of scans to find the max intensity value.
   *
   * @param scans The list of scans.
   * @return The max intensity.
   */
  public static double getMaxIntensity(List<double[][]> scans) {

    //Getting the maximal intensity from the list of spectra.
    double maxIntensity = 0;
    for (double[][] scan : scans) {
      for (double[] datapoints : scan) {
        if (datapoints[1] > maxIntensity) {
          maxIntensity = datapoints[1];
        }
      }
    }
    return maxIntensity;
  }

  /**
   * Adds datapoints with an intensity of 0 around each datapoint if the mass spectra are centroid.
   * Empty datapoints are added 0.0001 m/z before and after each datapoint, so the data gets
   * visualized correctly by the plot.
   *
   * @param scans The list of scans as arrays to be processed.
   * @return Returns a list of scans as arrays the plot can interpret correctly.
   */
  public static List<double[][]> addZerosToCentroidData(List<double[][]> scans) {

    final List<double[][]> processedScans = new ArrayList<>();
    final double deltaMZ = 0.0001;

    for (double[][] scan : scans) {

      final List<Double> mzs = new ArrayList<>();
      final List<Double> intensities = new ArrayList<>();

      final double potential = scan[0][2];

      for (double[] datapoint : scan) {

        final double mz = datapoint[0];
        final double intensity = datapoint[1];

        mzs.add(mz - deltaMZ);
        intensities.add(0d);

        mzs.add(mz);
        intensities.add(intensity);

        mzs.add(mz + deltaMZ);
        intensities.add(0d);
      }

      double[][] processedScan = new double[mzs.size()][3];

      for (int i = 0; i < mzs.size(); i++) {

        processedScan[i][0] = mzs.get(i);
        processedScan[i][1] = intensities.get(i);
        processedScan[i][2] = potential;
      }

      processedScans.add(processedScan);
    }

    return processedScans;
  }
}





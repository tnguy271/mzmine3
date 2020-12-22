/*
 * Copyright 2006-2020 The MZmine Development Team
 * 
 * This file is part of MZmine 3.
 * 
 * MZmine 3 is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine 3 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine 3; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.visualization.image;

import java.awt.Color;
import java.util.logging.Logger;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import io.github.mzmine.gui.chartbasics.gui.javafx.EChartViewer;
import io.github.mzmine.parameters.ParameterSet;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

public class ImageVisualizerPaneController {

  private final Logger logger = Logger.getLogger(this.getClass().getName());

  @FXML
  private BorderPane plotPane;

  @FXML
  private GridPane rawDataInfoGridPane;

  @FXML
  private GridPane imagingParameterInfoGridPane;

  @FXML
  private GridPane plotSettingsInfoGridPane;

  @FXML
  private Button backgroundButton;

  public void initialize(ParameterSet parameters) {}

  @FXML
  void toggleBackColor(ActionEvent event) {
    logger.finest("Toggle background");
    XYPlot plot = getChart().getXYPlot();
    if (plot.getBackgroundPaint() == Color.WHITE) {
      plot.setBackgroundPaint(Color.BLACK);
    } else {
      plot.setBackgroundPaint(Color.WHITE);
    }
  }

  public BorderPane getPlotPane() {
    return plotPane;
  }

  public void setPlotPane(BorderPane plotPane) {
    this.plotPane = plotPane;
  }

  public GridPane getRawDataInfoGridPane() {
    return rawDataInfoGridPane;
  }

  public void setRawDataInfoGridPane(GridPane rawDataInfoGridPane) {
    this.rawDataInfoGridPane = rawDataInfoGridPane;
  }

  public GridPane getImagingParameterInfoGridPane() {
    return imagingParameterInfoGridPane;
  }

  public void setImagingParameterInfoGridPane(GridPane imagingParameterInfoGridPane) {
    this.imagingParameterInfoGridPane = imagingParameterInfoGridPane;
  }

  public GridPane getPlotSettingsInfoGridPane() {
    return plotSettingsInfoGridPane;
  }

  public void setPlotSettingsInfoGridPane(GridPane plotSettingsInfoGridPane) {
    this.plotSettingsInfoGridPane = plotSettingsInfoGridPane;
  }

  private JFreeChart getChart() {
    if (plotPane.getChildren().get(0) instanceof EChartViewer) {
      EChartViewer viewer = (EChartViewer) plotPane.getChildren().get(0);
      return viewer.getChart();
    }
    return null;
  }

}

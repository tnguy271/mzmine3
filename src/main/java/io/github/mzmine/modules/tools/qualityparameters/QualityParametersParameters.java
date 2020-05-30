/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.tools.qualityparameters;

import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.PeakListsParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;

public class QualityParametersParameters extends SimpleParameterSet {

  public static final PeakListsParameter peakLists = new PeakListsParameter(1);

  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter("S/N tolerance",
      "Sets the tolerance range for S/N calculations. For high resolving instruments 0 "
          + "is recommended. However for instruments with less resolution a higher tolerance can be"
          + " useful. Due to peaks overlapping sometimes because of lower resolution and accuracy "
          + "the S/N could be accidentally lowered.", 0, 0, true);

  public QualityParametersParameters() {
    super(new Parameter[]{peakLists, mzTolerance});
  }
}

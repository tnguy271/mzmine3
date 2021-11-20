/*
 * Copyright 2006-2021 The MZmine Development Team
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
 *
 */

package io.github.mzmine.modules.io.projectsave;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.util.ExitCode;
import java.io.File;

public class ProjectSaveParameters extends ProjectSaveAsParameters {

  public ProjectSaveParameters() {
    super();
  }

  @Override
  public ExitCode showSetupDialog(boolean valueCheckRequired) {
    // see if current project already has a location
    // otherwise use parent SaveAs
    final MZmineProject project = MZmineCore.getProjectManager().getCurrentProject();
    final File currentProjectFile = project.getProjectFile();

    if ((currentProjectFile != null) && (currentProjectFile.canWrite())) {
      setParameter(projectFile, currentProjectFile);
      setParameter(option,
          project.isStandalone() ? ProjectSaveOption.STANDALONE : ProjectSaveOption.REFERENCING);
      return ExitCode.OK;
    } else {
      return super.showSetupDialog(valueCheckRequired);
    }
  }
}

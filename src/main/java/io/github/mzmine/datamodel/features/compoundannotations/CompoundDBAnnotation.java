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

package io.github.mzmine.datamodel.features.compoundannotations;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.IsotopePattern;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.DataType;
import io.github.mzmine.datamodel.features.types.IsotopePatternType;
import io.github.mzmine.datamodel.features.types.abstr.UrlShortName;
import io.github.mzmine.datamodel.features.types.annotations.CompoundNameType;
import io.github.mzmine.datamodel.features.types.annotations.SmilesStructureType;
import io.github.mzmine.datamodel.features.types.annotations.compounddb.DatabaseMatchInfoType;
import io.github.mzmine.datamodel.features.types.annotations.compounddb.DatabaseNameType;
import io.github.mzmine.datamodel.features.types.annotations.compounddb.Structure2dUrlType;
import io.github.mzmine.datamodel.features.types.annotations.compounddb.Structure3dUrlType;
import io.github.mzmine.datamodel.features.types.annotations.formula.FormulaType;
import io.github.mzmine.datamodel.features.types.annotations.iin.IonTypeType;
import io.github.mzmine.datamodel.features.types.numbers.CCSType;
import io.github.mzmine.datamodel.features.types.numbers.MobilityType;
import io.github.mzmine.datamodel.features.types.numbers.NeutralMassType;
import io.github.mzmine.datamodel.features.types.numbers.PrecursorMZType;
import io.github.mzmine.datamodel.features.types.numbers.RTType;
import io.github.mzmine.datamodel.features.types.numbers.scores.CompoundAnnotationScoreType;
import io.github.mzmine.datamodel.features.types.numbers.scores.IsotopePatternScoreType;
import io.github.mzmine.datamodel.identities.iontype.IonType;
import io.github.mzmine.modules.dataprocessing.id_ion_identity_networking.ionidnetworking.IonNetworkLibrary;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.mobilitytolerance.MobilityTolerance;
import io.github.mzmine.util.FeatureListUtils;
import io.github.mzmine.util.FormulaUtils;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;

public interface CompoundDBAnnotation extends Cloneable, FeatureAnnotation {

  Logger logger = Logger.getLogger(CompoundDBAnnotation.class.getName());

  String XML_ELEMENT_OLD = "compound_db_annotation";
  String XML_TYPE_ATTRIBUTE_OLD = "annotationtype";
  String XML_NUM_ENTRIES_ATTR = "entries";

  @NotNull
  static List<CompoundDBAnnotation> buildCompoundsWithAdducts(
      CompoundDBAnnotation neutralAnnotation, IonNetworkLibrary library) {
    final List<CompoundDBAnnotation> annotations = new ArrayList<>();
    for (IonType adduct : library.getAllAdducts()) {
      if (adduct.isUndefinedAdduct() || adduct.isUndefinedAdductParent() || adduct.getName()
          .contains("?")) {
        continue;
      }
      try {
        annotations.add(neutralAnnotation.ionize(adduct));
      } catch (IllegalStateException e) {
        logger.log(Level.WARNING, e.getMessage(), e);
      }
    }

    return annotations;
  }

  /**
   * Calculates the m/z for a given adduct.
   *
   * @param annotation
   * @param adduct
   * @return
   * @throws CannotDetermineMassException
   */
  static double calcMzForAdduct(@NotNull CompoundDBAnnotation annotation, @NotNull IonType adduct)
      throws CannotDetermineMassException {
    final Double neutralMass = annotation.get(NeutralMassType.class);
    if (neutralMass != null) {
      return adduct.getMZ(neutralMass);
    }

    final IonType currentAdduct = annotation.get(IonTypeType.class);
    if (currentAdduct != null && annotation.getPrecursorMZ() != null) {
      final double mass = currentAdduct.getMass(annotation.getPrecursorMZ());
      annotation.put(NeutralMassType.class, mass); // put neutral mass to speed up subsequent calls
      return adduct.getMZ(mass);
    }

    final String formulaString = annotation.getFormula();
    final String smiles = annotation.getSmiles();
    final IMolecularFormula neutralFormula =
        formulaString != null ? FormulaUtils.neutralizeFormulaWithHydrogen(formulaString)
            : FormulaUtils.neutralizeFormulaWithHydrogen(FormulaUtils.getFomulaFromSmiles(smiles));

    if (neutralFormula != null) {
      final double mass = MolecularFormulaManipulator.getMass(neutralFormula,
          MolecularFormulaManipulator.MonoIsotopic);
      annotation.put(NeutralMassType.class, mass); // put neutral mass to speed up subsequent calls
      return adduct.getMZ(mass);
    }

    throw new CannotDetermineMassException(annotation);
  }

  /**
   * @param adduct The adduct.
   * @return A new {@link CompoundDBAnnotation} with the given adduct.
   * {@link CompoundDBAnnotation#getPrecursorMZ()} is adjusted.
   * @throws CannotDetermineMassException In case the original compound does not contain enough
   *                                      information to calculate the ionized compound.
   */
  default CompoundDBAnnotation ionize(IonType adduct) throws CannotDetermineMassException {
    final CompoundDBAnnotation clone = clone();
    final double mz = clone.calcMzForAdduct(adduct);
    clone.put(PrecursorMZType.class, mz);
    clone.put(IonTypeType.class, adduct);
    return clone;
  }

  default double calcMzForAdduct(final IonType adduct) throws CannotDetermineMassException {
    return calcMzForAdduct(this, adduct);
  }

  <T> T get(@NotNull DataType<T> key);

  <T> T get(Class<? extends DataType<T>> key);

  <T> T put(@NotNull DataType<T> key, T value);

  /**
   * Stores the given value to this annotation if the value is not equal to null.
   *
   * @param key   The key.
   * @param value The value.
   * @return The previously mapped value. Also returns the currently mapped value if the parameter
   * was null.
   */
  default <T> T putIfNotNull(@NotNull DataType<T> key, @Nullable T value) {
    if (value != null) {
      return put(key, value);
    }
    return get(key);
  }

  <T> T put(@NotNull Class<? extends DataType<T>> key, T value);

  /**
   * Stores the given value to this annotation if the value is not equal to null.
   *
   * @param key   The key.
   * @param value The value.
   * @return The previously mapped value. Also returns the currently mapped value if the parameter
   * was null.
   */
  default <T> T putIfNotNull(@NotNull Class<? extends DataType<T>> key, @Nullable T value) {
    if (value != null) {
      return put(key, value);
    }
    return get(key);
  }

  Set<DataType<?>> getTypes();

  void saveToXML(@NotNull XMLStreamWriter writer, ModularFeatureList flist,
      ModularFeatureListRow row) throws XMLStreamException;

  @Nullable
  default DatabaseMatchInfo getDatabaseMatchInfo() {
    return get(DatabaseMatchInfoType.class);
  }

  @Nullable
  default String getDatabaseUrl() {
    final DatabaseMatchInfo databaseMatchInfo = getDatabaseMatchInfo();
    return databaseMatchInfo == null ? null : databaseMatchInfo.url();
  }

  @Override
  @Nullable
  default Double getPrecursorMZ() {
    return get(PrecursorMZType.class);
  }

  @Override
  @Nullable
  default String getSmiles() {
    return get(SmilesStructureType.class);
  }

  @Override
  @Nullable
  default String getCompoundName() {
    return get(CompoundNameType.class);
  }

  @Override
  @Nullable
  default String getFormula() {
    return get(FormulaType.class);
  }

  @Override
  @Nullable
  default IonType getAdductType() {
    return get(IonTypeType.class);
  }

  @Override
  @Nullable
  default Float getMobility() {
    return get(MobilityType.class);
  }

  @Override
  @Nullable
  default Float getCCS() {
    return get(CCSType.class);
  }

  @Override
  @Nullable
  default Float getRT() {
    return get(RTType.class);
  }

  @Override
  @Nullable
  default Float getScore() {
    return get(CompoundAnnotationScoreType.class);
  }

  @Override
  @Nullable
  default String getDatabase() {
    return get(DatabaseNameType.class);
  }

  boolean matches(FeatureListRow row, @Nullable MZTolerance mzTolerance,
      @Nullable RTTolerance rtTolerance, @Nullable MobilityTolerance mobilityTolerance,
      @Nullable Double percentCCSTolerance);

  /**
   * @param row                 tested row
   * @param mzTolerance         matching tolerance
   * @param rtTolerance         matching tolerance
   * @param mobilityTolerance   matching tolerance
   * @param percentCCSTolerance matching tolerance
   * @return
   */
  @Nullable
  default Float calculateScore(@NotNull FeatureListRow row, @Nullable MZTolerance mzTolerance,
      @Nullable RTTolerance rtTolerance, @Nullable MobilityTolerance mobilityTolerance,
      @Nullable Double percentCCSTolerance) {
    if (!matches(row, mzTolerance, rtTolerance, mobilityTolerance, percentCCSTolerance)) {
      return null;
    }
    // setup ranges around the annotation and test for row average values
    Double mz = getPrecursorMZ();
    final Float rt = getRT();
    final Float mobility = getMobility();
    final Float ccs = getCCS();
    var mzRange = mzTolerance != null && mz != null ? mzTolerance.getToleranceRange(mz) : null;
    var rtRange = rtTolerance != null && rt != null ? rtTolerance.getToleranceRange(rt) : null;
    var mobilityRange =
        mobilityTolerance != null && mobility != null ? mobilityTolerance.getToleranceRange(
            mobility) : null;
    var ccsRange = rtTolerance != null && rt != null ? rtTolerance.getToleranceRange(rt) : null;

    Range<Float> ccsTol = null;
    if (percentCCSTolerance != null && ccs != null && row.getAverageCCS() != null) {
      float tol = (float) (ccs * percentCCSTolerance);
      ccsTol = Range.closed(ccs - tol, ccs + tol);
    }
    return (float) FeatureListUtils.getAlignmentScore(row.getAverageMZ(), row.getAverageRT(),
        row.getAverageMobility(), row.getAverageCCS(), mzRange, rtRange, mobilityRange, ccsTol, 1,
        1, 1, 1);
  }

  /**
   * @return Returns the 2D structure URL.
   */
  default URL get2DStructureURL() {
    final UrlShortName url = get(Structure2dUrlType.class);
    try {
      return url != null ? new URL(url.longUrl()) : null;
    } catch (MalformedURLException e) {
      return null;
    }
  }

  /**
   * @return Returns the 3D structure URL.
   */
  default URL get3DStructureURL() {
    final UrlShortName url = get(Structure3dUrlType.class);
    try {
      return url != null ? new URL(url.longUrl()) : null;
    } catch (MalformedURLException e) {
      return null;
    }
  }

  /**
   * Returns the isotope pattern score or null if the score was not calculated.
   *
   * @return isotope pattern score.
   */
  default Float getIsotopePatternScore() {
    return get(IsotopePatternScoreType.class);
  }

  /**
   * Returns the isotope pattern (predicted) of this compound.
   *
   * @return the isotope pattern
   */
  default IsotopePattern getIsotopePattern() {
    return get(IsotopePatternType.class);
  }

  Map<DataType<?>, Object> getReadOnlyMap();

  CompoundDBAnnotation clone();
}

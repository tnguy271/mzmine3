/*
 * Copyright 2006 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */
package net.sf.mzmine.methods.filtering.mean;

import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import net.sf.mzmine.io.RawDataFile;

import net.sf.mzmine.util.Logger;

import net.sf.mzmine.methods.MethodParameters;

/**
 * This class represents parameter for the mean filter method
 */
public class MeanFilterParameters implements MethodParameters {

	/**
     * These Strings are used to access parameter values in an XML element
     */
	private static final String tagName = "MeanFilterParameters";
	private static final String oneSidedWindowLengthAttributeName = "OneSidedWindowLength";

	/**
	 * One-sided window length. Value is in MZ. True window size is two times this (plus-minus)
	 */

	public double oneSidedWindowLength = (double)0.1;

	private RawDataFile[] rawDataFiles;


    /**
     * @return parameters in human readable form
     */
    public String toString() {
		return new String("One-sided window length = " + oneSidedWindowLength + "Da");
	}

    /**
     *
     * @return parameters represented by XML element
     */
    public Element addToXML(Document doc) {

		Element e = doc.createElement(tagName);
		e.setAttribute(oneSidedWindowLengthAttributeName, String.valueOf(oneSidedWindowLength));
		return e;

	}


    /**
     * Reads parameters from XML
     * @param doc XML document supposed to contain parameters for the method (may not contain them, though)
     */
    public void readFromXML(Element element) {

		String attrValue;
		attrValue = element.getAttribute(oneSidedWindowLengthAttributeName);
		try { oneSidedWindowLength = Double.parseDouble(attrValue); } catch (NumberFormatException nfe) {}
	}

	/**
	 * Returns the XML tag name
	 */
	public String getTagName() {
		return tagName;
	}

	/**
	 *
	 */
	public RawDataFile[] getRawDataFiles() {
		return rawDataFiles;
	}

	/**
	 *
	 */
	public void setRawDataFiles(RawDataFile[] rawDataFiles) {
		this.rawDataFiles = rawDataFiles;
	}


}
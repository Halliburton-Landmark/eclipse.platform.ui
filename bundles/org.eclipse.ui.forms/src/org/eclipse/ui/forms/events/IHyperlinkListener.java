/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.forms.events;
/**
 * Classes that implement this interface will be notified when hyperlinks are
 * entered, exited and activated.
 *
 * @see org.eclipse.ui.forms.widgets.Hyperlink
 * @see org.eclipse.ui.forms.widgets.ImageHyperlink
 * @see org.eclipse.ui.forms.widgets.FormText
 * @since 3.0
 */
public interface IHyperlinkListener {
	/**
	 * Sent when hyperlink is entered either by mouse entering the link client
	 * area, or keyboard focus switching to the hyperlink.
	 *
	 * @param e
	 *            an event containing information about the hyperlink
	 */
	void linkEntered(HyperlinkEvent e);
	/**
	 * Sent when hyperlink is exited either by mouse exiting the link client
	 * area, or keyboard focus switching from the hyperlink.
	 *
	 * @param e
	 *            an event containing information about the hyperlink
	 */
	void linkExited(HyperlinkEvent e);
	/**
	 * Sent when hyperlink is activated either by mouse click inside the link
	 * client area, or by pressing 'Enter' key while hyperlink has keyboard
	 * focus.
	 *
	 * @param e
	 *            an event containing information about the hyperlink
	 */
	void linkActivated(HyperlinkEvent e);
}

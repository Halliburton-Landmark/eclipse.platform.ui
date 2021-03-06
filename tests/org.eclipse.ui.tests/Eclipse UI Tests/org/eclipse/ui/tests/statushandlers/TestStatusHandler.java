/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
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
 ******************************************************************************/

package org.eclipse.ui.tests.statushandlers;

import org.eclipse.ui.internal.WorkbenchErrorHandlerProxy;
import org.eclipse.ui.statushandlers.AbstractStatusHandler;
import org.eclipse.ui.statushandlers.StatusAdapter;

/**
 * The handler should be used during tests. It allows for checking the status
 * and style used during last handling.
 *
 * @since 3.3
 */
public class TestStatusHandler extends AbstractStatusHandler {

	private static StatusAdapter lastHandledStatusAdapter;

	private static int lastHandledStyle;

	private static AbstractStatusHandler workbenchHandler;

	@Override
	public void handle(StatusAdapter statusAdapter, int style) {
		lastHandledStatusAdapter = statusAdapter;
		lastHandledStyle = style;

		if (workbenchHandler == null) {
			workbenchHandler = new WorkbenchErrorHandlerProxy();
		}

		// Forward to the workbench handler
		workbenchHandler.handle(statusAdapter, style);
	}

	/**
	 * Returns the status used during last handling
	 *
	 * @return the status
	 */
	public static StatusAdapter getLastHandledStatusAdapter() {
		return lastHandledStatusAdapter;
	}

	/**
	 * Returns the style used during last handling
	 *
	 * @return the style
	 */
	public static int getLastHandledStyle() {
		return lastHandledStyle;
	}
}

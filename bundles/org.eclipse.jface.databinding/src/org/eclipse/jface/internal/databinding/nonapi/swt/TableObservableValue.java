/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jface.internal.databinding.nonapi.swt;

import org.eclipse.jface.internal.databinding.api.observable.value.AbstractObservableValue;
import org.eclipse.jface.internal.databinding.api.observable.value.ValueDiff;
import org.eclipse.jface.internal.databinding.api.swt.SWTProperties;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Table;

/**
 * @since 1.0
 * 
 */
public class TableObservableValue extends AbstractObservableValue {

	private final Table table;

	private boolean updating = false;
	
	private int currentSelection;

	/**
	 * @param table
	 * @param attribute
	 */
	public TableObservableValue(Table table, String attribute) {
		this.table = table;
		currentSelection = table.getSelectionIndex();
		if (attribute.equals(SWTProperties.SELECTION)) {
			currentSelection = table.getSelectionIndex();
			table.addSelectionListener(new SelectionListener() {
				public void widgetSelected(SelectionEvent e) {
					if (!updating) {
						int newSelection = TableObservableValue.this.table.getSelectionIndex();
						fireValueChange(new ValueDiff(new Integer(currentSelection), new Integer(newSelection)));
						currentSelection = newSelection;
					}
				}

				public void widgetDefaultSelected(SelectionEvent e) {
					widgetSelected(e);
				}
			});
		} else {
			throw new IllegalArgumentException();
		}
	}

	public void setValue(Object value) {
		try {
			updating = true;
			int intValue = ((Integer) value).intValue();
			table.setSelection(intValue);
			currentSelection = intValue;
		} finally {
			updating = false;
		}
	}

	public Object doGetValue() {
		return new Integer(table.getSelectionIndex());
	}

	public Object getValueType() {
		return Integer.class;
	}

}

/*******************************************************************************
 * Copyright (c) 2008 Matthew Hall and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthew Hall - initial API and implementation (bug 194734)
 *     Matthew Hall - bug 195222
 ******************************************************************************/

package org.eclipse.core.internal.databinding.property;

import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.property.list.IListProperty;
import org.eclipse.core.databinding.property.list.ListProperty;
import org.eclipse.core.databinding.property.value.IValueProperty;

/**
 * @since 3.3
 * 
 */
public class ListPropertyDetailValuesList extends ListProperty {
	private final IListProperty masterProperty;
	private final IValueProperty detailProperty;

	/**
	 * @param masterProperty
	 * @param detailProperty
	 */
	public ListPropertyDetailValuesList(IListProperty masterProperty,
			IValueProperty detailProperty) {
		this.masterProperty = masterProperty;
		this.detailProperty = detailProperty;
	}

	public Object getElementType() {
		return detailProperty.getValueType();
	}

	public IObservableList observe(Realm realm, Object source) {
		IObservableList master = masterProperty.observe(realm, source);
		return detailProperty.observeDetail(master);
	}

	public IObservableList observeDetail(IObservableValue master) {
		IObservableList masterList = masterProperty.observeDetail(master);
		return detailProperty.observeDetail(masterList);
	}

	public String toString() {
		return masterProperty + " => " + detailProperty; //$NON-NLS-1$
	}
}

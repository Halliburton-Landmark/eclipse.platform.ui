/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.jface.internal.databinding.api.observable.mapping;

import org.eclipse.jface.internal.databinding.api.observable.IObservable;

/**
 * @since 1.0
 * 
 */
public interface IObservableMapping extends IObservable, IMapping {

	/**
	 * @param listener
	 */
	public void addMappingChangeListener(IMappingChangeListener listener);

	/**
	 * @param listener
	 */
	public void removeMappingChangeListener(IMappingChangeListener listener);
}

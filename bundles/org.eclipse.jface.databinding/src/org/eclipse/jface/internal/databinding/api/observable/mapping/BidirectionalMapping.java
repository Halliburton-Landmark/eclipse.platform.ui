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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.internal.databinding.api.observable.set.IObservableSet;
import org.eclipse.jface.internal.databinding.api.observable.set.ISetChangeListener;
import org.eclipse.jface.internal.databinding.api.observable.set.ISetDiff;
import org.eclipse.jface.internal.databinding.api.observable.set.WritableSet;

/**
 * @since 1.0
 * 
 */
public class BidirectionalMapping extends AbstractObservableMapping implements
		IBidirectionalMapping, IObservableMappingWithDomain {

	private final IObservableMapping wrappedMapping;

	private IObservableSet range;

	private Map valueToElements = new HashMap();

	private ISetChangeListener domainListener = new ISetChangeListener() {
		public void handleSetChange(IObservableSet source, ISetDiff diff) {
			Set rangeAdditions = new HashSet();
			for (Iterator it = diff.getAdditions().iterator(); it.hasNext();) {
				Object added = it.next();
				Object mappingValue = wrappedMapping.getMappingValue(added);
				rangeAdditions.add(mappingValue);
				addMapping(mappingValue, added);
			}
			range.addAll(rangeAdditions);
			for (Iterator it = diff.getRemovals().iterator(); it.hasNext();) {
				Object removed = it.next();
				removeMapping(wrappedMapping.getMappingValue(removed), removed);
			}
			range.retainAll(valueToElements.keySet());
		}
	};
	
	private IMappingChangeListener mappingChangeListener = new IMappingChangeListener(){
		public void handleMappingValueChange(IObservableMapping source, IMappingDiff diff) {
			Set affectedElements = diff.getElements();
			for (Iterator it = affectedElements.iterator(); it.hasNext();) {
				Object element = it.next();
				Object oldFunctionValue = diff.getOldMappingValue(element);
				Object newFunctionValue = diff.getNewMappingValue(element);
				removeMapping(oldFunctionValue, element);
				addMapping(newFunctionValue, element);
			}
			Set tempRange = valueToElements.keySet();
			range.addAll(tempRange);
			range.retainAll(tempRange);
			fireMappingValueChange(diff);
		}
	};

	private IObservableSet domain;

	/**
	 * @param functionWithDomain
	 */
	public BidirectionalMapping(IObservableMappingWithDomain functionWithDomain) {
		this(functionWithDomain, functionWithDomain.getDomain());
	}
	
	/**
	 * @param wrappedMapping
	 * @param domain 
	 */
	public BidirectionalMapping(IObservableMapping wrappedFunction, IObservableSet domain) {
		this.wrappedMapping = wrappedFunction;
		this.domain = domain;
		Set tempRange = new HashSet();
		for (Iterator it = domain.iterator(); it.hasNext();) {
			Object element = it.next();
			Object functionValue = wrappedFunction.getMappingValue(element);
			addMapping(functionValue, element);
			tempRange.add(functionValue);
		}
		this.range = new WritableSet(tempRange);
		domain.addSetChangeListener(domainListener);
	}

	/**
	 * @param functionValue
	 * @param element
	 * @param b
	 */
	private void addMapping(Object functionValue, Object element) {
		Object elementOrSet = valueToElements.get(functionValue);
		if (elementOrSet == null) {
			valueToElements.put(functionValue, element);
			return;
		}
		if (!(elementOrSet instanceof Set)) {
			elementOrSet = new HashSet(Collections.singleton(elementOrSet));
			valueToElements.put(functionValue, elementOrSet);
		}
		Set set = (Set) elementOrSet;
		set.add(element);
	}

	/**
	 * @param functionValue
	 * @param element
	 * @param b
	 */
	private void removeMapping(Object functionValue, Object element) {
		Object elementOrSet = valueToElements.get(functionValue);
		if (elementOrSet instanceof Set) {
			Set set = (Set) elementOrSet;
			set.remove(element);
			if(set.size()==0) {
				valueToElements.remove(functionValue);
			}
		} else {
			valueToElements.remove(functionValue);
		}
	}

	protected Object doGetMappingValue(Object element) {
		return wrappedMapping.getMappingValue(element);
	}
	
	public void setMappingValue(Object element, Object value) {
		wrappedMapping.setMappingValue(element, value);
	}

	public IObservableSet getRange() {
		return range;
	}

	public Set getDomainElementsForValue(Object value) {
		return null;
	}
	
	public void dispose() {
		wrappedMapping.removeMappingChangeListener(mappingChangeListener);
		domain.removeSetChangeListener(domainListener);
	}

	public IObservableSet getDomain() {
		return domain;
	}

}

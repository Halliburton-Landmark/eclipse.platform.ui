/*******************************************************************************
 * Copyright (c) 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.ui.internal.services;

import java.util.Hashtable;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.e4.core.services.context.IContextFunction;
import org.eclipse.e4.ui.services.EContextService;
import org.eclipse.e4.ui.services.EHandlerService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.EventAdmin;
import org.osgi.util.tracker.ServiceTracker;

public class Activator implements BundleActivator {
	
	public final static String PLUGIN_ID = "org.eclipse.e4.ui.services";
	
	private static Activator singleton;
	
	private ServiceRegistration contextServiceReg; 
	private ServiceRegistration handlerServiceReg;
	
	private ServiceTracker eventAdminTracker;
	private BundleContext bundleContext;

	/*
	 * Returns the singleton for this Activator. Callers should be aware that
	 * this will return null if the bundle is not active.
	 */
	public static Activator getDefault() {
		return singleton;
	}

	public void start(BundleContext context) throws Exception {
		bundleContext = context;
		singleton = this;

		//Register functions that will be used as factories for the handler and context services.
		//We must use this advanced technique because these service implementations need access
		//to the service they are registered with. More typically context services are registered directly as OSGi services.
		//Also note these services could be registered lazily using declarative services if needed
		Hashtable<String, String> props = new Hashtable<String, String>(4);
		props.put(IContextFunction.SERVICE_CONTEXT_KEY, EContextService.class.getName());
		contextServiceReg = context.registerService(IContextFunction.class.getName(), new ContextContextFunction(), props);
		props.put(IContextFunction.SERVICE_CONTEXT_KEY, EHandlerService.class.getName());
		handlerServiceReg = context.registerService(IContextFunction.class.getName(), new HandlerContextFunction(), props);
	}
	
	/*
	 * Return the debug options service, if available.
	 */
	public EventAdmin getEventAdmin() {
		if (eventAdminTracker == null) {
			eventAdminTracker = new ServiceTracker(bundleContext, EventAdmin.class.getName(), null);
			eventAdminTracker.open();
		}
		return (EventAdmin) eventAdminTracker.getService();
	}

	public void stop(BundleContext context) throws Exception {
		if (contextServiceReg != null) {
			contextServiceReg.unregister();
			contextServiceReg = null;
		}
		if (handlerServiceReg != null) {
			handlerServiceReg.unregister();
			handlerServiceReg = null;
		}
		
		if (eventAdminTracker != null) {
			eventAdminTracker.close();
			eventAdminTracker = null;
		}
		bundleContext = null;
		singleton = null;
	}
	
	public BundleContext getBundleContext() {
		return bundleContext;
	}
	
	public void logError(String msg) {
		if (bundleContext == null) { // fallback if nothing is available
			if (System.err != null)
				System.err.println(msg);
			return;
		}
		ILog log = Platform.getLog(bundleContext.getBundle());
		log.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, msg));
	}

}

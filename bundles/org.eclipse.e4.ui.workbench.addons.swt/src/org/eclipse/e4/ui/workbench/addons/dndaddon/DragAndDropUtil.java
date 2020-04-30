/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
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
package org.eclipse.e4.ui.workbench.addons.dndaddon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspective;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.jface.util.Geometry;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

class DragAndDropUtil {
	public static final String IGNORE_AS_DROP_TARGET = "ignore_as_drop_target"; //$NON-NLS-1$
	private static List<MWindow> appWindowZOrder = new ArrayList<>();
	private static EModelService modelService;

	@Execute
	void initialize(IEventBroker eventBroker, EModelService ems) {
		modelService = ems;
		eventBroker.subscribe(UIEvents.ElementContainer.TOPIC_SELECTEDELEMENT, e -> {
			Object changedElement = e.getProperty(UIEvents.EventTags.ELEMENT);
			if (changedElement instanceof MApplication) {
				MWindow window = (MWindow) e.getProperty(UIEvents.EventTags.NEW_VALUE);
				appWindowZOrder.remove(window);
				appWindowZOrder.add(0, window);
			}
		});
		eventBroker.subscribe(UIEvents.ElementContainer.TOPIC_CHILDREN, e -> {
			Object changedElement = e.getProperty(UIEvents.EventTags.ELEMENT);
			if (changedElement instanceof MApplication && UIEvents.isREMOVE(e)) {
				UIEvents.asIterable(e, UIEvents.EventTags.OLD_VALUE).forEach(window -> appWindowZOrder.remove(window));
			}
		});
	}

	/**
	 * Shorthand method. Returns the bounding rectangle for the given control,
	 * in display coordinates.
	 *
	 * @param boundsControl
	 *            the control whose bounds are to be computed
	 * @return the bounds of the given control in display coordinates
	 */
	public static Rectangle getDisplayBounds(Control boundsControl) {
		Control parent = boundsControl.getParent();
		if (parent == null || boundsControl instanceof Shell) {
			return boundsControl.getBounds();
		}

		return Geometry.toDisplay(parent, boundsControl.getBounds());
	}

	/**
	 * Finds and returns the most specific SWT control at the given location.
	 * (Note: this does a DFS on the SWT widget hierarchy, which can be slow).
	 * Any invisible control or control tagged with IGNORE_AS_DROP_TARGET will
	 * be ignored by this method.
	 *
	 * @param displayToSearch
	 *            the display to search for potential controls
	 * @param locationToFind
	 *            the position, in display coordinates, to be located
	 * @return the most specific SWT control at the given location
	 */
	public static Control findControl(Display displayToSearch, Point locationToFind) {
		Set<Object> visited = new HashSet<>();
		return appWindowZOrder.stream().map(w -> {
			Control found = null;
			Shell shell = w.getContext().getLocal(Shell.class);
			visited.add(shell);
			MPerspective activePerspective = modelService.getActivePerspective(w);
			if (activePerspective != null) {
				found = activePerspective.getWindows().stream().filter(MWindow::isToBeRendered).map(dw -> {
					visited.add(dw.getWidget());
					return findControl(dw.getWidget(), locationToFind);
				}).filter(Objects::nonNull).findFirst().orElse(null);
			}
			if (found == null) {
				found = w.getWindows().stream().filter(MWindow::isToBeRendered).map(dw -> {
					visited.add(dw.getWidget());
					return findControl(dw.getWidget(), locationToFind);
				}).filter(Objects::nonNull).findFirst().orElse(null);
			}
			return found != null ? found : findControl(shell, locationToFind);
		}).filter(Objects::nonNull).findFirst().orElseGet(() -> {
			return Arrays.stream(displayToSearch.getShells()).filter(o -> !visited.contains(o))
					.map(s -> findControl(s, locationToFind)).filter(Objects::nonNull).findAny().orElse(null);
		});
	}

	private static Control findControl(Object toSearch, Point locationToFind) {
		return toSearch instanceof Shell ? findControl((Shell) toSearch, locationToFind) : null;
	}

	private static Control findControl(Shell toSearch, Point locationToFind) {
		return toSearch != null && toSearch.getData(IGNORE_AS_DROP_TARGET) == null && !toSearch.isDisposed()
				&& toSearch.isVisible() && getDisplayBounds(toSearch).contains(locationToFind)
						? findControl(toSearch.getChildren(), locationToFind)
						: null;
	}
	/**
	 * Searches the given list of controls for a control containing the given
	 * point. If the array contains any composites, those composites will be
	 * recursively searched to find the most specific child that contains the
	 * point. Any invisible control or control tagged with IGNORE_AS_DROP_TARGET
	 * will be ignored by this method.
	 *
	 * @param toSearch
	 *            an array of controls to be searched for potential matches
	 * @param locationToFind
	 *            a point (in display coordinates)
	 * @return the most specific Control that overlaps the given point, or null
	 *         if none
	 */
	private static Control findControl(Control[] toSearch, Point locationToFind) {
		for (int idx = toSearch.length - 1; idx >= 0; idx--) {
			Control next = toSearch[idx];

			if (next == null || next.getData(IGNORE_AS_DROP_TARGET) != null) {
				continue;
			}
			if (!next.isDisposed() && next.isVisible()) {
				Rectangle bounds = getDisplayBounds(next);

				if (bounds.contains(locationToFind)) {
					if (next instanceof Composite) {
						Control result = findControl((Composite) next, locationToFind);

						if (result != null) {
							return result;
						}
					}

					return next;
				}
			}
		}
		return null;
	}

	/**
	 * Finds the control at the given location. Any invisible control or control
	 * tagged with IGNORE_AS_DROP_TARGET will be ignored by this method.
	 *
	 * @param toSearch
	 *            the composite to be searched for potential matches.
	 * @param locationToFind
	 *            location (in display coordinates)
	 * @return the control at the given location
	 */
	private static Control findControl(Composite toSearch, Point locationToFind) {
		Control[] children = toSearch.getChildren();

		return findControl(children, locationToFind);
	}
}

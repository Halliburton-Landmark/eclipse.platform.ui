/*******************************************************************************
 * Copyright (c) 2010, 2018 IBM Corporation and others.
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
 *     Patrik Suzzi <psuzzi@gmail.com> - Bug 497348
 ******************************************************************************/

package org.eclipse.e4.ui.workbench.addons.dndaddon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.e4.ui.internal.workbench.swt.AbstractPartRenderer;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartStack;
import org.eclipse.e4.ui.model.application.ui.basic.MStackElement;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;

/**
 * This agent manage drag and drop when dragging a Tab in Eclipse Part Stacks.
 */
public class StackDropAgent extends DropAgent {
	private Rectangle tabArea;
	private MPartStack dropStack;
	private CTabFolder dropCTF;

	/**
	 * @param manager
	 */
	public StackDropAgent(DnDManager manager) {
		super(manager);
	}

	@Override
	public boolean canDrop(MUIElement dragElement, DnDInfo info) {
		// We only except stack elements and whole stacks
		if (!(dragElement instanceof MStackElement) && !(dragElement instanceof MPartStack)) {
			return false;
		}

		// We have to be over a stack ourselves
		if (!(info.curElement instanceof MPartStack)) {
			return false;
		}

		MPartStack stack = (MPartStack) info.curElement;

		if (stack.getTags().contains(IPresentationEngine.STANDALONE)) {
			return false;
		}

		// We only work for CTabFolders
		if (!(stack.getWidget() instanceof CTabFolder)) {
			return false;
		}

		// We can't drop stacks onto itself
		if (stack == dragElement) {
			return false;
		}

		// You can only drag MParts from window to window
		// NOTE: Disabled again due to too many issues, see bug 445305 for details
		// The if check is commented out in the Eclispe code due to bug 445305 but reverted in our code.
		// We have workaround the issue in DSG so it is safe to enable it.
		// see com.lgc.dsp.ui.swtbridge.util.PostStart
		 if (!(dragElement instanceof MPart)) {
			EModelService ms = dndManager.getModelService();
			MWindow dragElementWin = ms.getTopLevelWindowFor(dragElement);
			MWindow dropWin = ms.getTopLevelWindowFor(stack);
			if (dragElementWin != dropWin) {
				return false;
			}
		 }

		// only allow dropping into the the area
		Rectangle areaRect = getTabAreaRect((CTabFolder) stack.getWidget());
		boolean inArea = areaRect.contains(info.cursorPos);
		if (inArea) {
			tabArea = areaRect;
			dropStack = (MPartStack) info.curElement;
			dropCTF = (CTabFolder) dropStack.getWidget();
		}
		return inArea;
	}

	private Rectangle getTabAreaRect(CTabFolder theCTF) {
		Rectangle ctfBounds = theCTF.getBounds();
		ctfBounds.height = theCTF.getTabHeight();

		Rectangle displayBounds = Display.getCurrent().map(theCTF.getParent(), null, ctfBounds);
		return displayBounds;
	}

	/**
	 * Static helper to get visible items without using member variables in this
	 * stateful Agent.
	 *
	 * @param dropCTF
	 * @return
	 */
	private static List<CTabItem> getVisibleItems(CTabFolder dropCTF) {
		return Stream.of(dropCTF.getItems())
			.filter(i -> i.isShowing())
			.collect(Collectors.toList());
	}

	/**
	 * Static helper to compute the visual rectangles to drop, without using
	 * member variables in this stateful Agent.
	 *
	 * @param dropCTF
	 * @param visibleItems
	 * @param tabArea
	 * @return
	 */
	private static ArrayList<Rectangle> getItemRects(CTabFolder dropCTF, List<CTabItem> visibleItems,
			Rectangle tabArea) {
		// for dropping, we consider visible tab only
		ArrayList<Rectangle> itemRects = new ArrayList<>();
		CTabItem item;
		if (visibleItems.size() > 0) {
			// First rect is from left to the center of the item
			item = visibleItems.get(0);
			Rectangle itemRect = item.getBounds();
			int centerX = itemRect.x + (itemRect.width / 2);
			itemRect.width /= 2;
			int curX = itemRect.x + itemRect.width;
			Rectangle insertRect = dropCTF.getDisplay().map(dropCTF, null, itemRect);
			itemRects.add(insertRect);

			// Process the other items
			for (int i = 1; i < visibleItems.size(); i++) {
				item = visibleItems.get(i);
				itemRect = item.getBounds();
				centerX = itemRect.x + (itemRect.width / 2);
				itemRect.width = centerX - curX;
				itemRect.x = curX;
				curX = centerX;
				insertRect = dropCTF.getDisplay().map(dropCTF, null, itemRect);
				itemRects.add(insertRect);
			}
			// Finally, add rectangle from the center of the last element
			// to the end
			itemRect.x = curX;
			itemRect.width = dropCTF.getBounds().width - curX;
			insertRect = dropCTF.getDisplay().map(dropCTF, null, itemRect);
			itemRects.add(insertRect);
		} else {
			// Empty stack, whole area is index == 0
			itemRects.add(tabArea);
		}
		//
		return itemRects;
	}

	private ArrayList<Rectangle> computeInsertRects() {
		List<CTabItem> visibleItems = getVisibleItems(dropCTF);
		return getItemRects(dropCTF, visibleItems, tabArea);
	}

	private int getDropIndex(DnDInfo info) {
		ArrayList<Rectangle> itemRects = computeInsertRects();

		for (Rectangle itemRect : itemRects) {
			if (itemRect.contains(info.cursorPos)) {
				return itemRects.indexOf(itemRect);
			}
		}
		return -1;
	}

	@Override
	public void dragLeave(MUIElement dragElement, DnDInfo info) {
		dndManager.clearOverlay();

		if (dndManager.getFeedbackStyle() == DnDManager.HOSTED) {
			if (dragElement.getParent() != null) {
				dndManager.hostElement(dragElement, 16, 10);
			}
		} else {
			dndManager.setHostBounds(null);
		}

		tabArea = null;

		super.dragLeave(dragElement, info);
	}

	/**
	 * Tracks movements of mouse on the Stack where the user is dropping the element
	 */
	@Override
	public boolean track(MUIElement dragElement, DnDInfo info) {
		if (!tabArea.contains(info.cursorPos) || dropStack == null || !dropStack.isToBeRendered()) {
			return false;
		}

		int dropIndex = getDropIndex(info);
		if (dropIndex == -1) {
			return true;
		}

		dndManager.setCursor(Display.getCurrent().getSystemCursor(SWT.CURSOR_HAND));

		if (dropStack.getChildren().indexOf(dragElement) == dropIndex){
			return true;
		}
		if (dndManager.getFeedbackStyle() == DnDManager.HOSTED) {
			dock(dragElement, dropIndex);
			Display.getCurrent().update();
			showFrame(dragElement);
		} else {
			List<CTabItem> visibleItems = getVisibleItems(dropCTF);
			if (dropIndex < visibleItems.size()) {
				Rectangle itemBounds = visibleItems.get(dropIndex).getBounds();
				itemBounds.width = 2;
				itemBounds = Display.getCurrent().map(dropCTF, null, itemBounds);
				dndManager.frameRect(itemBounds);
			} else if (visibleItems.size() > 0) {
				Rectangle itemBounds = visibleItems.get(dropIndex - 1).getBounds();
				itemBounds.x = itemBounds.x + itemBounds.width;
				itemBounds.width = 2;
				itemBounds = Display.getCurrent().map(dropCTF, null, itemBounds);
				dndManager.frameRect(itemBounds);
			} else {
				Rectangle fr = new Rectangle(tabArea.x, tabArea.y, tabArea.width, tabArea.height);
				fr.width = 2;
				dndManager.frameRect(fr);
			}
			if (dndManager.getFeedbackStyle() == DnDManager.GHOSTED) {
				Rectangle ca = dropCTF.getClientArea();
				ca = Display.getCurrent().map(dropCTF, null, ca);
				dndManager.setHostBounds(ca);
			}
		}

		return true;
	}

	/**
	 * @param dragElement
	 * @param dropIndex
	 */
	private boolean dock(MUIElement dragElement, int dropIndex) {

		List<CTabItem> vItems = getVisibleItems(dropCTF);
		boolean hiddenTabs = (vItems.size() < dropCTF.getChildren().length);

		// Adjust the index if necessary
		List<MStackElement> dropChildren = dropStack.getChildren();
		int elementIndex = dropChildren.indexOf(dragElement);


		// Check if we have a placeholder with same id: we must remove it before
		// dropping (to avoid bug 410164).
		// Note 1: for bug 537816 we only want to apply this logic to the views, which
		// have different id's if multiple instances exists in the same perspective,
		// so an element with the same id is the "same" view in the perspective.
		// Note 2: editors seem to always have same id, independently of their actual
		// content. This is at least surprising, so we can't distinguish different
		// editors by looking on elementId only.
		// Note 3: currently if we drag/drop parts, it looks like for editor parts we
		// always drop PartImpl instances, for views we drop PartStackImpl or
		// PlaceholderImpl instances. So one could use this for the check below too.
		MStackElement viewWithSameId = null;
		if (elementIndex == -1 && !dragElement.getTags().contains("Editor")) { //$NON-NLS-1$
			for (MStackElement stackElement : dropChildren) {
				String id = stackElement.getElementId();
				if (id != null && id.equals(dragElement.getElementId())) {
					viewWithSameId = stackElement;
					break;
				}
			}
		}

		if (elementIndex != -1 && !(dragElement instanceof MPartStack)) {
			// Get the index of this CTF entry
			Control dragCtrl = (Control) dragElement.getWidget();
			for (CTabItem cti : dropCTF.getItems()) {
				if (dragCtrl == cti.getControl()) {
					int itemIndex = dropCTF.indexOf(cti);
					if (dropIndex > 0 && itemIndex < dropIndex) {
						dropIndex--;
					}
				}
			}
		}

		// 'dropIndex' is now the index of the CTabItem to put ourselves before
		// we need to adjust this to be a model index

		if (hiddenTabs) {
			// some tabs are hidden
			int nVisibleItems = vItems.size();
			if (dropIndex < nVisibleItems) {
				CTabItem item = vItems.get(dropIndex);
				MUIElement itemModel = (MUIElement) item.getData(AbstractPartRenderer.OWNING_ME);

				// if we're going before ourselves its a NO-OP
				if (itemModel == dragElement) {
					return false;
				}
				dropIndex = itemModel.getParent().getChildren().indexOf(itemModel);
				// if the item is dropped at the last position, there is
				// no existing item to put ourselves before
				// so we'll just go to the end.
			} else if (dropIndex == nVisibleItems) {
				dropIndex = dropChildren.size();
			}


		} else {
			// all tabs are visible
			int ctfItemCount = dropCTF.getItemCount();
			if (dropIndex < ctfItemCount) {
				CTabItem item = dropCTF.getItem(dropIndex);
				MUIElement itemModel = (MUIElement) item.getData(AbstractPartRenderer.OWNING_ME);

				// if we're going before ourselves its a NO-OP
				if (itemModel == dragElement) {
					return false;
				}

				dropIndex = itemModel.getParent().getChildren().indexOf(itemModel);
				// if the item is dropped at the last position, there is
				// no existing item to put ourselves before
				// so we'll just go to the end.
			} else if (dropIndex == ctfItemCount) {
				dropIndex = dropChildren.size();
			}
		}
		EModelService ms = dndManager.getModelService();
		MWindow dragElementWin = ms.getTopLevelWindowFor(dragElement);
		MWindow dropWin = ms.getTopLevelWindowFor(dropStack);
		boolean switchedWindows = dragElementWin != dropWin;
		if (switchedWindows) {
			clearEventQueue();
		}
		if (dragElement instanceof MStackElement) {
			MElementContainer<MUIElement> parent = dragElement.getParent();
			if (parent != null) {
				List<MUIElement> children = parent.getChildren();
				EPartService partService = dragElementWin.getContext().get(EPartService.class);
				MUIElement sibling = children.stream().filter(c -> c != dragElement && c.getTags().contains("Editor")) //$NON-NLS-1$
						.findAny().orElseGet(() -> children.stream()
								.filter(c -> c != dragElement && c.isToBeRendered() && c.isVisible()).findAny()
								.orElse(null));
				if (switchedWindows) {
					Collection<MPart> sourceWindowParts = partService.getParts();
					sourceWindowParts.remove(dragElement);
					if (sibling == null) {
						if (!activateAnyEditor(partService, sourceWindowParts)) { // then activate a view
							partService.activate(sourceWindowParts.stream()
									.filter(p -> partService.isPartVisible(p)).findAny().orElse(null));
						}
					} else if (sibling.getTags().contains("Editor")) { //$NON-NLS-1$
						partService.activate((MPart) sibling);
					} else { // avoid activating the view by activating any editor in the window
						if (activateAnyEditor(partService, sourceWindowParts)) {
							showPart(ms, parent, sibling);
						} else {
							partService.activate((MPart) sibling);
						}
					}
				} else if (sibling != null) {
					showPart(ms, parent, sibling);
				}
				children.remove(dragElement);
				if (switchedWindows) {
					dropWin.getParent().setSelectedElement(dropWin);
				}
			}
			if (dropIndex >= 0 && dropIndex < dropChildren.size()) {
				dropChildren.add(dropIndex, (MStackElement) dragElement);
			} else {
				dropChildren.add((MStackElement) dragElement);
			}

			// Bug 410164: remove placeholder element with same id from the drop stack to
			// avoid duplicated elements in same stack
			if (viewWithSameId != null) {
				dropChildren.remove(viewWithSameId);
			}

			// (Re)active the element being dropped
			dropStack.setSelectedElement((MStackElement) dragElement);
		} else {
			MPartStack stack = (MPartStack) dragElement;
			MStackElement curSel = stack.getSelectedElement();
			List<MStackElement> kids = stack.getChildren();

			// First move over all *non-selected* elements
			int selIndex = kids.indexOf(curSel);
			boolean curSelProcessed = false;
			while (kids.size() > 1) {
				// Offset the 'get' to account for skipping 'curSel'
				MStackElement kid = curSelProcessed ? kids.get(kids.size() - 2) : kids.get(kids.size() - 1);
				if (kid == curSel) {
					curSelProcessed = true;
					continue;
				}

				kids.remove(kid);
				if (dropIndex >= 0 && dropIndex < dropChildren.size()) {
					dropChildren.add(dropIndex, kid);
				} else {
					dropChildren.add(kid);
				}
			}

			// Finally, move over the selected element
			kids.remove(curSel);
			dropIndex = dropIndex + selIndex;
			if (dropIndex >= 0 && dropIndex < dropChildren.size()) {
				dropChildren.add(dropIndex, curSel);
			} else {
				dropChildren.add(curSel);
			}

			// (Re)active the element being dropped
			dropStack.setSelectedElement(curSel);
		}
		EPartService partService = dropWin.getContext().get(EPartService.class);
		if (switchedWindows) {
			clearEventQueue();
			// XXX sometimes drop window is not the active window
			// and dragElement is not the active part.
			dropWin.getParent().setSelectedElement(dropWin);
			if (partService.getActivePart() != dragElement) {
				partService.activate((MPart) dragElement);
			}
		}
		return false;
	}

	private void showPart(EModelService ms, MElementContainer<MUIElement> parent, MUIElement sibling) {
		Display display = dropCTF.getDisplay();
		// sometimes focus is set in Control.setVisible and a part is activated.
		// possibly dependent on the current focus control.
		// when focus control is in the editor Composite,
		// then Control.setVisible occurs.
		// when focus control is the CTabFolder,
		// no focus and subsequent activation occurs.
		// add this filter to reject all activations.
		Listener suppressEvents = (e -> {
			e.type = SWT.None;
			System.err.println("clobber type to suppress activation in " + e.widget); //$NON-NLS-1$
		});
		display.addFilter(SWT.FocusIn, suppressEvents);
		display.addFilter(SWT.Activate, suppressEvents);
		try {
			// set selected element to null to avoid activating the sibling part
			parent.setSelectedElement(null);
			ms.bringToTop(sibling);
		} finally {
			display.removeFilter(SWT.FocusIn, suppressEvents);
			display.removeFilter(SWT.Activate, suppressEvents);
		}
	}

	/**
	 * find an editor to activate in the window, preferably:
	 * <ol>
	 * <li>any selected editor in a visible stack
	 * <li>any editor in a visible stack
	 * <li>any selected editor
	 * <li>any editor
	 * </ol>
	 *
	 * @param sourceWindowParts
	 *
	 * @param dragElement
	 */
	private boolean activateAnyEditor(EPartService partService, Collection<MPart> sourceWindowParts) {
		List<MPart> editors = sourceWindowParts.stream().filter(p -> p.getTags().contains("Editor")). //$NON-NLS-1$
				collect(Collectors.toList());
		if (!editors.isEmpty()) {
			List<MPart> selectedEditors = editors.stream().filter(p -> isSelected(p)).collect(Collectors.toList());
			partService.activate(selectedEditors.stream().filter(p -> partService.isPartVisible(p)).findAny()
					.orElseGet(() -> editors.stream().filter(p -> partService.isPartVisible(p)).findAny()
							.orElseGet(() -> selectedEditors.isEmpty() ? editors.get(0) : selectedEditors.get(0))));
		}
		return !editors.isEmpty();
	}

	private boolean isSelected(MStackElement p) {
		return p.getParent().getSelectedElement() == p;
	}

	private void clearEventQueue() {
		Display display = dropCTF.getDisplay();
		while (display.readAndDispatch()) {
			// clear events in dropWindow
		}
	}

	/**
	 * @param dragElement
	 */
	private void showFrame(MUIElement dragElement) {
		CTabFolder ctf = (CTabFolder) dropStack.getWidget();
		CTabItem[] items = ctf.getItems();
		CTabItem item = null;
		for (CTabItem tabItem : items) {
			if (tabItem.getData(AbstractPartRenderer.OWNING_ME) == dragElement) {
				item = tabItem;
				break;
			}
		}

		Rectangle bounds = item.getBounds();
		bounds = Display.getCurrent().map(dropCTF, null, bounds);
		Rectangle outerBounds = new Rectangle(bounds.x - 3, bounds.y - 3, bounds.width + 6,
				bounds.height + 6);

		dndManager.frameRect(outerBounds);
	}

	@Override
	public boolean drop(MUIElement dragElement, DnDInfo info) {
		if (dndManager.getFeedbackStyle() != DnDManager.HOSTED) {
			int dropIndex = getDropIndex(info);
			if (dropIndex != -1) {
				MUIElement toActivate = dragElement instanceof MPartStack ? ((MPartStack) dragElement)
						.getSelectedElement() : dragElement;
				if (dock(dragElement, dropIndex))
					reactivatePart(toActivate);
			}
		}
		return true;
	}
}

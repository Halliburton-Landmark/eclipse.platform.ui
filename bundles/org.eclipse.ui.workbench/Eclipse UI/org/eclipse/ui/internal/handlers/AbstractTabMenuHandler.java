package org.eclipse.ui.internal.handlers;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.e4.core.commands.ExpressionContext;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.advanced.MPlaceholder;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.e4.ui.workbench.modeling.ISaveHandler;

abstract class AbstractTabMenuHandler extends AbstractHandler {

	MPart getTabMenuPart(Object evaluationContext) {
		if (evaluationContext instanceof ExpressionContext) {
			return ((ExpressionContext)evaluationContext).eclipseContext.get(MPart.class);
		}
		return null;
	}

	boolean hasCloseableSiblingParts(MPart mpart) {
		return !getCloseableSiblingParts(mpart).isEmpty();
	}

	void closeSiblingParts(MPart part, boolean skipThisPart) {
		MElementContainer<MUIElement> container = getParent(part);
		if (container == null)
			return;

		List<MPart> others = getCloseableSiblingParts(part);

		// add the current part last so that we unrender obscured items first
		if (!skipThisPart && part.isToBeRendered() && isClosable(part)) {
			others.add(part);
		}

		// add the selected element of the stack at the end, else we may end up
		// selecting another part when we hide it since it is the selected
		// element
		MUIElement selectedElement = container.getSelectedElement();
		if (others.remove(selectedElement)) {
			others.add((MPart) selectedElement);
		} else if (selectedElement instanceof MPlaceholder) {
			selectedElement = ((MPlaceholder) selectedElement).getRef();
			if (others.remove(selectedElement)) {
				others.add((MPart) selectedElement);
			}
		}

		EPartService partService = getContextForParent(part)
				.get(EPartService.class);
		// try using the ISaveHandler first... This gives better control of
		// dialogs...
		ISaveHandler saveHandler = getContextForParent(part)
				.get(ISaveHandler.class);
		if (saveHandler != null) {
			final List<MPart> toPrompt = new ArrayList<>(others);
			toPrompt.retainAll(partService.getDirtyParts());

			boolean cancel = false;
			if (toPrompt.size() > 1) {
				cancel = !saveHandler.saveParts(toPrompt, true);
			} else if (toPrompt.size() == 1) {
				cancel = !saveHandler.save(toPrompt.get(0), true);
			}
			if (cancel) {
				return;
			}

			for (MPart other : others) {
				partService.hidePart(other);
			}
			return;
		}

		// No ISaveHandler, fall back to just using the part service...
		for (MPart otherPart : others) {
			if (partService.savePart(otherPart, true))
				partService.hidePart(otherPart);
		}
	}

	private static MElementContainer<MUIElement> getParent(MPart part) {
		MElementContainer<MUIElement> parent = part.getParent();
		if (parent == null) {
			MPlaceholder placeholder = part.getCurSharedRef();
			return placeholder == null ? null : placeholder.getParent();
		}
		return parent;
	}

	private static List<MPart> getCloseableSiblingParts(MPart part) {
		// broken out from closeSiblingParts so it can be used to determine how
		// many closeable siblings are available
		MElementContainer<MUIElement> container = getParent(part);
		List<MPart> closeableSiblings = new ArrayList<>();
		if (container == null)
			return closeableSiblings;

		List<MUIElement> children = container.getChildren();
		for (MUIElement child : children) {
			// If the element isn't showing skip it
			if (!child.isToBeRendered())
				continue;

			MPart otherPart = null;
			if (child instanceof MPart)
				otherPart = (MPart) child;
			else if (child instanceof MPlaceholder) {
				MUIElement otherItem = ((MPlaceholder) child).getRef();
				if (otherItem instanceof MPart)
					otherPart = (MPart) otherItem;
			}
			if (otherPart == null)
				continue;

			if (part.equals(otherPart))
				continue; // skip selected item
			if (otherPart.isToBeRendered() && isClosable(otherPart))
				closeableSiblings.add(otherPart);
		}
		return closeableSiblings;
	}

	private static boolean isClosable(MPart part) {
		// if it's a shared part check its current ref
		if (part.getCurSharedRef() != null) {
			return !(part.getCurSharedRef().getTags()
					.contains(IPresentationEngine.NO_CLOSE));
		}
		return part.isCloseable();

	}

	private static IEclipseContext getContextForParent(MPart element) {
		EModelService modelService = element.getContext()
				.get(EModelService.class);
		return modelService.getContainingContext(element);
	}

}

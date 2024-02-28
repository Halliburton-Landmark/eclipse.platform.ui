package org.eclipse.ui.internal.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.modeling.EPartService;

public class CloseTabHandler extends AbstractTabMenuHandler {
	
	@Override
	public void setEnabled(Object evaluationContext) {
		MPart mpart = getTabMenuPart(evaluationContext);
		if (mpart != null) {
			setBaseEnabled(mpart.isCloseable());
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		MPart mpart = getTabMenuPart(event.getApplicationContext());
		EPartService partService = mpart.getContext().get(EPartService.class);
		if (partService.savePart(mpart, true)) {
			partService.hidePart(mpart);
		}
		return null;
	}
}

package org.eclipse.ui.internal.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;

public class CloseAllTabsHandler extends AbstractTabMenuHandler {
	
	@Override
	public void setEnabled(Object evaluationContext) {
		MPart mpart = getTabMenuPart(evaluationContext);
		if (mpart != null) {
			setBaseEnabled(hasCloseableSiblingParts(mpart));
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		MPart mpart = getTabMenuPart(event.getApplicationContext());
		if (mpart != null) {
			closeSiblingParts(mpart, false);
		}
		return null;
	}
}

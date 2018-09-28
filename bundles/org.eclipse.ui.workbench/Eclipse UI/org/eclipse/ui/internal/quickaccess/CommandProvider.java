/*******************************************************************************
 * Copyright (c) 2006, 2015 IBM Corporation and others.
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
 *     Lars Vogel <Lars.Vogel@gmail.com> - Bug 440810
 *     Patrik Suzzi <psuzzi@gmail.com> - Bug 476045
 *******************************************************************************/

package org.eclipse.ui.internal.quickaccess;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.e4.core.commands.EHandlerService;
import org.eclipse.e4.core.commands.ExpressionContext;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.activities.IIdentifier;
import org.eclipse.ui.activities.IWorkbenchActivitySupport;
import org.eclipse.ui.commands.ICommandImageService;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.internal.IWorkbenchGraphicConstants;
import org.eclipse.ui.internal.WorkbenchImages;

/**
 * @since 3.3
 *
 */
public class CommandProvider extends QuickAccessProvider {

	private IEvaluationContext currentSnapshot;

	void setSnapshot(IEvaluationContext c) {
		reset();
		currentSnapshot = c;
	}

	private final Map<String, CommandElement> idToCommand;
	private IHandlerService handlerService;
	private ICommandService commandService;
	private EHandlerService ehandlerService;
	private ICommandImageService commandImageService;

	private boolean allCommandsRetrieved;

	public CommandProvider() {
		idToCommand = Collections.synchronizedMap(new HashMap<>());
	}

	@Override
	public String getId() {
		return "org.eclipse.ui.commands"; //$NON-NLS-1$
	}

	@Override
	public QuickAccessElement getElementForId(String id) {
		retrieveCommand(id);
		return idToCommand.get(id);
	}

	@Override
	public QuickAccessElement[] getElements() {
		if (!allCommandsRetrieved) {
			ICommandService commandService = getCommandService();
			Collection<String> commandIds = commandService.getDefinedCommandIds();
			for (String commandId : commandIds) {
				retrieveCommand(commandId);
			}
			allCommandsRetrieved = true;
		}
		synchronized (idToCommand) {
			return idToCommand.values().stream().toArray(QuickAccessElement[]::new);
		}
	}

	private void retrieveCommand(final String currentCommandId) {
		boolean commandRetrieved = idToCommand.containsKey(currentCommandId);
		if (!commandRetrieved) {
			ICommandService commandService = getCommandService();
			EHandlerService ehandlerService = getEHandlerService();
			final Collection commandIds = commandService.getDefinedCommandIds();
			final Iterator commandIdItr = commandIds.iterator();
			while (commandIdItr.hasNext()) {
				final String commandId = (String) commandIdItr.next();
				final Command command = commandService
						.getCommand(commandId);
				ParameterizedCommand pcmd = new ParameterizedCommand(command, null);
				if (command != null && ehandlerService.canExecute(pcmd) && !isFilteredOut(command.getId())) {
					try {
						Collection combinations = ParameterizedCommand
								.generateCombinations(command);
						for (Iterator it = combinations.iterator(); it
								.hasNext();) {
							ParameterizedCommand pc = (ParameterizedCommand) it.next();
							String id = pc.serialize();
							if (!isFilteredOut(id)) {
								synchronized (idToCommand) {
									idToCommand.put(id,
									new CommandElement(pc, id, this));
								}
							}
						}
					} catch (final NotDefinedException e) {
						// It is safe to just ignore undefined commands.
					}
				}
			}
		}
	}

	/**
	 * Eclipse activity framework only allows to filter out visual elements
	 * (implementing IPluginContribution), and is not able to filter out
	 * commands from Quick Access. We add support for filtering out commands
	 * declared in plugin.xml with the pattern: #command/&lt;commandId&gt;
	 *
	 * @param command
	 *            command to check
	 * @return true is a command should be hidden
	 */
	private boolean isFilteredOut(String commandId) {
		IWorkbenchActivitySupport workbenchActivitySupport = PlatformUI.getWorkbench().getActivitySupport();
		IIdentifier identifier = workbenchActivitySupport.getActivityManager().getIdentifier("#command/" + commandId); //$NON-NLS-1$
		return !identifier.isEnabled();
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return WorkbenchImages
				.getImageDescriptor(IWorkbenchGraphicConstants.IMG_OBJ_NODE);
	}

	@Override
	public String getName() {
		return QuickAccessMessages.QuickAccess_Commands;
	}

	EHandlerService getEHandlerService() {
		if (ehandlerService == null) {
			if (currentSnapshot instanceof ExpressionContext) {
				IEclipseContext ctx = ((ExpressionContext) currentSnapshot).eclipseContext;
				ehandlerService = ctx.get(EHandlerService.class);
			} else {
				ehandlerService = PlatformUI.getWorkbench().getService(
						EHandlerService.class);
			}
		}
		return ehandlerService;
	}

	ICommandService getCommandService() {
		if (commandService == null) {
			if (currentSnapshot instanceof ExpressionContext) {
				IEclipseContext ctx = ((ExpressionContext) currentSnapshot).eclipseContext;
				commandService = ctx.get(ICommandService.class);
			} else {
				commandService = PlatformUI.getWorkbench().getService(
						ICommandService.class);
			}
		}
		return commandService;
	}

	IHandlerService getHandlerService() {
		if (handlerService == null) {
			if (currentSnapshot instanceof ExpressionContext) {
				IEclipseContext ctx = ((ExpressionContext) currentSnapshot).eclipseContext;
				handlerService = ctx.get(IHandlerService.class);
			} else {
				handlerService = PlatformUI.getWorkbench().getService(
						IHandlerService.class);
			}
		}
		return handlerService;
	}

	public ICommandImageService getCommandImageService() {
		if (commandImageService == null) {
			if (currentSnapshot instanceof ExpressionContext) {
				IEclipseContext ctx = ((ExpressionContext) currentSnapshot).eclipseContext;
				commandImageService = ctx.get(ICommandImageService.class);
			} else {
				commandImageService = PlatformUI.getWorkbench().getService(ICommandImageService.class);
			}
		}
		return commandImageService;
	}

	IEvaluationContext getContextSnapshot() {
		return currentSnapshot;
	}

	@Override
	protected void doReset() {
		allCommandsRetrieved = false;
		synchronized (idToCommand) {
			idToCommand.clear();
		}
		if (currentSnapshot instanceof ExpressionContext) {
			((ExpressionContext) currentSnapshot).eclipseContext.dispose();
		}
		currentSnapshot = null;
	}
}

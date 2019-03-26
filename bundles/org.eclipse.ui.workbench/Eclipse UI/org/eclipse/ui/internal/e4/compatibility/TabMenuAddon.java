package org.eclipse.ui.internal.e4.compatibility;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.contexts.RunAndTrack;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.di.extensions.EventTopic;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.e4.ui.model.application.ui.MElementContainer;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.menu.MPopupMenu;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.e4.ui.workbench.UIEvents.EventTags;
import org.eclipse.e4.ui.workbench.renderers.swt.MenuManagerRenderer;
import org.eclipse.jface.action.IMenuListener2;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.internal.Workbench;
import org.osgi.service.event.Event;
/**
* Adds editor id to editor tab menu tags.
* The MPart element id of compatibility editors is not the IEditor site id.
* It has a fixed value of CompatibilityEditor.MODEL_ELEMENT_ID "org.eclipse.e4.ui.compatibility.editor"
* unlike MPart of compatibility views which has its element id set to the IView site id.
*
* This presents a problem in that StackRenderer cannot add the IEditor site id to the Tab Menu
* MPopup model tags.
*
*/
@SuppressWarnings("restriction")
public class TabMenuAddon {
	static private final String menuPrefix = "popup:"; //$NON-NLS-1$
	static private final String menuSuffix = ".tab"; //$NON-NLS-1$
    /**
     * add the editor tab popup id to the editor tab popup.
     * @param event
     */
    @Inject
    @Optional
    // @EventFilter("&(EventType=ADD)(objectclass:ChangeElement:=MPart)(objectclass:NewValue:=MPopupMenu)"
    private void subscribeTopicEditorTabMenu(@EventTopic(UIEvents.Part.TOPIC_MENUS) Event event) {
        Object element = event.getProperty(UIEvents.EventTags.ELEMENT);
//        Object data = event.getProperty(EventUtils.DATA);
        // if only EventTopic annotation accepted values to filter against:
        // e.g. eventType="add" elementType=className etc.
        if (element instanceof MPart && UIEvents.isADD(event)) {
            MPart part = (MPart)element;
			if (part.getTags().contains("Editor")) { //$NON-NLS-1$
                for (Object addedElement : UIEvents.asIterable(event, UIEvents.EventTags.NEW_VALUE)) {
                    if (addedElement instanceof MPopupMenu) {
                        final MPopupMenu popupMenu = (MPopupMenu) addedElement;
                        if (popupMenu.getElementId().endsWith(menuSuffix)
                            && isPartTabMenu(part, popupMenu)) {
                            addEditorIdTabMenuTag(part, popupMenu);
                        }
                    }
                }
            }
        }
    }
/**
 * Add the editor id to the popup menu tags.
 * @param editor
 * @param popupMenu
 */
    static private boolean addEditorIdTabMenuTag(final MPart editor, final MPopupMenu popupMenu) {
        String editorId = null;
        IWorkbenchPartSite site = editor.getContext().getLocal(IWorkbenchPartSite.class);
        if (site != null) {
            editorId = site.getId();
/*
        } else { // XXX Operating on this inside knowledge is brittle. Consider omitting it.
 * The editor id is added to editor part tags by WorkbenchPage immediately after the editor
 * MPartDescriptor tags are added.
 *
 * Alternatively, invoking addEditorIdTabMenuTag in the monitorTabPopupSite RAT below.
 * In this case we do not include any editor specific contributions to the popup until the editor
 * is rendered.
            MPartDescriptor descriptor = editor.getContext().get(EModelService.class).
                    getPartDescriptor(editor.getElementId());
            int index = descriptor == null ? 0 : descriptor.getTags().size();
            List<String> tags = editor.getTags();
            editorId = tags.size() <= index ? null : tags.get(index);
 */
        }
        if (editorId != null) {
            String menuId = menuPrefix + editorId + menuSuffix;
            if (!popupMenu.getTags().contains(menuId)) {
                popupMenu.getTags().add(menuId);
                return true;
            }
        }
        return false;
    }
    /**
     * add menu source provider support to all tab popups. adds ISources.ACTIVE_MENU* values to workbench
     * @param event
     */
    @Inject
    @Optional
    private void subscribeTopicPartTabMenuWidget(@EventTopic(UIEvents.UIElement.TOPIC_WIDGET) Event event) {
        final MUIElement changedElement = (MUIElement) event.getProperty(EventTags.ELEMENT);
        if (event.getProperty(EventTags.NEW_VALUE) != null
            && changedElement instanceof MPopupMenu
            && changedElement.getElementId().endsWith(menuSuffix)) {
            final MPopupMenu popupMenu = (MPopupMenu) changedElement;
            final MPart part = popupMenu.getContext().get(MPart.class);
            if (isPartTabMenu(part, popupMenu)) {
                final IEclipseContext partContext = part.getContext();
				if (part.getTags().contains("Editor")) { //$NON-NLS-1$
                    final Widget menu = (Widget) event.getProperty(EventTags.NEW_VALUE);
                    final Object mgr = menu.getData();
                    final IWorkbench wb = partContext.get(IWorkbench.class);
                    if (mgr instanceof IMenuManager && wb instanceof Workbench) {
                        IMenuManager menuManager = (IMenuManager)mgr;
                        final MenuSourceProviderSupport support =
                                new MenuSourceProviderSupport(popupMenu, partContext, (Workbench)wb);
                        menuManager.addMenuListener(support);
						menu.setData("MenuSourceProviderSupport", support); //$NON-NLS-1$
                        menu.addDisposeListener(e -> menuManager.removeMenuListener(support));
                    }
                }
                if (partContext.getLocal(IWorkbenchPartSite.class) == null) {
                    monitorTabPopupSite(popupMenu, part);
                } else if (addEditorIdTabMenuTag(part, popupMenu)) {
                    regenerateTabMenu(popupMenu, null);
                }
            }
        }
    }
    static private void monitorTabPopupSite(final MPopupMenu popupMenu, final MPart editor) {
        IEclipseContext partContext = editor.getContext();
        partContext.runAndTrack(new RunAndTrack() {
            @Override
            public boolean changed(IEclipseContext ctx) {
                IWorkbenchPartSite site = partContext.get(IWorkbenchPartSite.class);
                if (site != null) runExternalCode(() -> {
                    addEditorIdTabMenuTag(editor, popupMenu);
                    regenerateTabMenu(popupMenu, partContext.get(UISynchronize.class));
                });
                return site == null;
            }
        });
    }
    static boolean isPartTabMenu(MPart part, MPopupMenu popupMenu) {
        return part != null && popupMenu.getElementId().equals((part.getElementId()+menuSuffix));
    }
    // Regenerate the popup menu when:
    // 1) a part is created since a new site IHandlerService is created in
    //    PartSite.initializeDefaultServices.  The present handlers reference another IHandlerService.
    // 2) the editor id tag is added to MPopup in which case we must add the editor specific
    //    contributions to the popup menu.
    @SuppressWarnings("unchecked")
    static private void regenerateTabMenu(final MPopupMenu popupMenu, final UISynchronize synchronizer) {
        final MenuManagerRenderer renderer = ((MenuManagerRenderer)popupMenu.getRenderer());
        final MenuManager menuManager = renderer == null ? null : renderer.getManager(popupMenu);
        if (renderer != null && menuManager != null) {
            renderer.cleanUp(popupMenu);
            menuManager.removeAll();
            renderer.reconcileManagerToModel(menuManager, popupMenu);
            if (synchronizer != null) {
                synchronizer.asyncExec(() -> {
                    renderer.processContributions(popupMenu, popupMenu.getElementId(), false, true);
                    renderer.processContents((MElementContainer<MUIElement>)((Object)popupMenu));
                });
            } else {
                renderer.processContributions(popupMenu, popupMenu.getElementId(), false, true);
                renderer.processContents((MElementContainer<MUIElement>)((Object)popupMenu));
            }
        }
    }
    static class MenuSourceProviderSupport implements IMenuListener2 {
        final private Workbench workbench;
        final private IEclipseContext context;
        final private Set<String> menuIds;
        final private UISynchronize synchronizer;

        MenuSourceProviderSupport(final MPopupMenu popupMenu, final IEclipseContext partContext, final Workbench wb) {
            workbench = wb;
            context = partContext;
            synchronizer = context.get(UISynchronize.class);
            menuIds = popupMenu.getTags().stream().filter(pid -> pid.startsWith(menuPrefix)).
                    map(pid -> pid.substring(menuPrefix.length())).collect(Collectors.toSet());
        }
        @Override
        public void menuAboutToHide(IMenuManager manager) {
            synchronizer.asyncExec(() -> workbench.removeShowingMenus(menuIds, null, null));
        }
        @Override
        // support for deprecated org.eclipse.ui.popupMenus extension point omitted intentionally
        public void menuAboutToShow(IMenuManager mgr) {
            workbench.addShowingMenus(menuIds, getPartSelection(), getEditorSelection());
        }
        private ISelection getEditorSelection() {
            IEditorPart part = context.getLocal(IEditorPart.class);
            IEditorInput input = part == null ? null : part.getEditorInput();
            return input == null ? null : new StructuredSelection(new Object[] {input});
        }
        private ISelection getPartSelection() {
			Object selection = context.getLocal("org.eclipse.ui.output.postSelection"); //$NON-NLS-1$
            if (selection == null)
				selection = context.getLocal("org.eclipse.ui.output.selection"); //$NON-NLS-1$
            return selection instanceof ISelection ? (ISelection)selection : null;
        }
    }
}
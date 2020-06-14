package org.eclipse.e4.ui.css.swt.properties.custom;

import org.eclipse.e4.ui.css.core.dom.properties.ICSSPropertyHandler;
import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.swt.properties.AbstractCSSPropertySWTHandler;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolderRenderer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Control;
import org.w3c.dom.css.CSSValue;

public class CSSPropertyWrappedTabColor extends AbstractCSSPropertySWTHandler {
    public static final ICSSPropertyHandler INSTANCE = new CSSPropertyWrappedTabColor();

    @Override
    protected void applyCSSProperty(Control control, String property, CSSValue value, String pseudo, CSSEngine engine)
            throws Exception {
        if (!(control instanceof CTabFolder)) {
            return;
        }
        if (value.getCssValueType() == CSSValue.CSS_PRIMITIVE_VALUE) {
            Color newColor = (Color) engine.convert(value, Color.class, control.getDisplay());
            CTabFolderRenderer renderer = ((CTabFolder) control).getRenderer();
            if (renderer instanceof CTabRendering) {
                ((CTabRendering) renderer).setWrappedTabFolderColor(newColor);
            }
        }
    }

    @Override
    protected String retrieveCSSProperty(Control control, String property, String pseudo, CSSEngine engine)
            throws Exception {
        return null;
    }

}

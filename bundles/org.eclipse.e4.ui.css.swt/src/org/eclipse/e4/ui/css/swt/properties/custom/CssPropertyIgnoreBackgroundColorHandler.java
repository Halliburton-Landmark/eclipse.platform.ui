package org.eclipse.e4.ui.css.swt.properties.custom;

import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.swt.properties.css2.CSSPropertyBackgroundSWTHandler;
import org.w3c.dom.css.CSSValue;

/**
 * This class handles the specific CSS property value: background-color=ignore which does not apply any color.
 *
 * Ignoring property is useful when specific controls should be excluded from a broader CSS rule.
 *
 */
public class CssPropertyIgnoreBackgroundColorHandler extends CSSPropertyBackgroundSWTHandler {

    @Override
    public void applyCSSPropertyBackgroundColor(Object element, CSSValue value, String pseudo, CSSEngine engine)
            throws Exception {
        if (value.getCssText().equals("ignore")) {
            return;
        }
        super.applyCSSPropertyBackgroundColor(element, value, pseudo, engine);
    }

}

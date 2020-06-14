package org.eclipse.e4.ui.css.swt.properties.custom;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.swt.helpers.SWTElementHelpers;
import org.eclipse.e4.ui.css.swt.properties.css2.CSSPropertyFontSWTHandler;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.w3c.dom.css.CSSValue;
import org.w3c.dom.css.CSSValueList;

/**
 * Handler for CSS property "hal-font-family". It work exactly like "font-family", but also supports list of fonts:
 * {hal-font-family : Arial, Tahoma, "Courier New";}. Eclipse "font-family" only supports a primitive value:
 * {font-family: Arial;}
 */
@SuppressWarnings("restriction")
public class CssPropertyFontFamilyHandler extends CSSPropertyFontSWTHandler {

    private final Set<String> fontFamilies;

    public CssPropertyFontFamilyHandler() {
        FontData[] fonts = Display.getDefault().getFontList(null, true);
        fontFamilies = Arrays.stream(fonts).map(fd -> fd.getName().toLowerCase()).collect(Collectors.toSet());
    }

    @Override
    public boolean applyCSSProperty(Object element, String property, CSSValue value, String pseudo, CSSEngine engine)
            throws Exception {
        // Defect 262354. Workaround for Help/Workflow Guide views. Do not apply style for ImageHyperlink control.
        if (SWTElementHelpers.getWidget(element) instanceof ImageHyperlink) {
            return false;
        }

        if (property.equals(CSSProperties.HAL_FONT_FAMILY)) {
            return applyHalFontFamily(element, value, pseudo, engine);
        }
        return super.applyCSSProperty(element, property, value, pseudo, engine);
    }

    private boolean applyHalFontFamily(Object element, CSSValue value, String pseudo, CSSEngine engine)
            throws Exception {
        if (value.getCssValueType() == CSSValue.CSS_PRIMITIVE_VALUE) {
            return super.applyCSSProperty(element, CSSProperties.FONT_FAMILY, value, pseudo, engine);
        } else if (value.getCssValueType() == CSSValue.CSS_VALUE_LIST) {
            CSSValueList valueList = (CSSValueList) value;
            for (int i = 0; i < valueList.getLength(); i++) {
                CSSValue item = valueList.item(i);
                if (item.getCssValueType() == CSSValue.CSS_PRIMITIVE_VALUE) {
                    String familyName = item.getCssText();
                    if (fontFamilies.contains(familyName.toLowerCase())) {
                        return super.applyCSSProperty(element, CSSProperties.FONT_FAMILY, item, pseudo, engine);
                    }
                }
            }
        }
        return false;
    }
}

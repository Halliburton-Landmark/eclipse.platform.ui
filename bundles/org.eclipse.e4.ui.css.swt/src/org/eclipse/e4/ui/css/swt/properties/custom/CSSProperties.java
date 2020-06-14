package org.eclipse.e4.ui.css.swt.properties.custom;

/**
 * Helper class that contains templates for DS CSS file.
 *
 * @author Maksym Bodaniuk
 *
 */
public interface CSSProperties {

    /**
     * Key value for setting and getting the CSS class name of a widget.
     */
    public static final String CSS_CLASSNAME = "org.eclipse.e4.ui.css.CssClassName";

    /**
     * Key value for setting and getting the CSS ID of a widget.
     */
    public static final String CSS_ID = "org.eclipse.e4.ui.css.id";

    /**
     * CSS property name for image
     */
    public static final String IMAGE = "image";

    /**
     * CSS property name for hover image
     */
    public static final String HOVER_IMAGE = "hover-image";

    /**
     * CSS property name for hover image
     */
    public static final String DISABLED_IMAGE = "disabled-image";

    /**
     * CSS property name for depth color
     */
    public static final String DEPTH_COLOR = "depth-color";

    /**
     * CSS property name for font family (with support for list of values)
     */
    public static final String HAL_FONT_FAMILY = "hal-font-family";

    /**
     * CSS property name for font family
     */
    public static final String FONT_FAMILY = "font-family";

    /**
     * CSS property name for border color
     */
    public static final String BORDER_COLOR = "border-color";
}

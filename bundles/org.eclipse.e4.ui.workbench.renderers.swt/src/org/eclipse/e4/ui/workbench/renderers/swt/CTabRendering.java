/*******************************************************************************
 * Copyright (c) 2010, 2020 IBM Corporation and others.
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
 *     Fabio Zadrozny - Bug 465711
 *     Simon Scholz <simon.scholz@vogella.com> - Bug 497586
 *     Simon Scholz <simon.scholz@vogella.com> - Bug 506540
 *     Mike Marchand <mmarchand@cranksoftware.com> - Bug 538740
 *******************************************************************************/
package org.eclipse.e4.ui.workbench.renderers.swt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import javax.inject.Inject;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.ui.internal.css.swt.ICTabRendering;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolderRenderer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Pattern;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.ToolBar;

@SuppressWarnings("restriction")
public class CTabRendering extends CTabFolderRenderer implements ICTabRendering, IPreferenceChangeListener {
	private static final String CONTAINS_TOOLBAR = "CTabRendering.containsToolbar"; //$NON-NLS-1$

	/**
	 * A named preference for setting CTabFolder's to be rendered with rounded
	 * corners
	 * <p>
	 * The default value for this preference is: <code>false</code> (render
	 * CTabFolder's with square corners)
	 * </p>
	 */
	public static final String USE_ROUND_TABS = "USE_ROUND_TABS"; //$NON-NLS-1$

	/**
	 * Default value for "use round tabs" preference
	 */
	public static final boolean USE_ROUND_TABS_DEFAULT = false;

	// Constants for circle drawing
	final static int LEFT_TOP = 0;
	final static int LEFT_BOTTOM = 1;
	final static int RIGHT_TOP = 2;
	final static int RIGHT_BOTTOM = 3;

	static final int SQUARE_CORNER = 0;

	// drop shadow constants
	final static int SIDE_DROP_WIDTH = 3;
	final static int BOTTOM_DROP_WIDTH = 4;

	// keylines
	static final int OUTER_KEYLINE_WIDTH = 1;
	static final int INNER_KEYLINE_WIDTH = 0;
	static final int TOP_KEYLINE_WIDTH = 0;

	// The tab has an outline, it contributes to the trim. See Bug 562183.
	static final int TAB_OUTLINE_WIDTH = 1;

	// Item Constants
	static final int ITEM_TOP_MARGIN = 2;
	static final int ITEM_BOTTOM_MARGIN = 6;
	static final int ITEM_LEFT_MARGIN = 4;
	static final int ITEM_RIGHT_MARGIN = 4;

	static final String E4_TOOLBAR_ACTIVE_IMAGE = "org.eclipse.e4.renderer.toolbar_background_active_image"; //$NON-NLS-1$
	static final String E4_TOOLBAR_INACTIVE_IMAGE = "org.eclipse.e4.renderer.toolbar_background_inactive_image"; //$NON-NLS-1$

	Rectangle rectShape;
	int[] shape;

	Image toolbarActiveImage, toolbarInactiveImage;

	int cornerSize = 10;

	Color outerKeylineColor, innerKeylineColor;
	boolean active;

	Color[] selectedTabFillColors;
	int[] selectedTabFillPercents;

	Color[] unselectedTabsColors;
	int[] unselectedTabsPercents;

	Color tabOutlineColor;

	Color unselectedTabOutlineColor;

	int paddingLeft = 0, paddingRight = 0, paddingTop = 0, paddingBottom = 0;

	private final CTabFolderRendererWrapper rendererWrapper;
	private final CTabFolderWrapper parentWrapper;

	private Color unselectedHoverColor;
	private Color selectedTabHighlightColor;
	private boolean drawTabHighlightOnTop = true;


	private boolean drawCustomTabContentBackground;

	private Color selectedHoverBorderColor;

	private Color unselectedHoverBorderColor;

	private Color wrappedTabFolderColor;

	private boolean fillToolbarArea = true;

	@Inject
	public CTabRendering(CTabFolder parent) {
		super(parent);
		parentWrapper = new CTabFolderWrapper(parent);
		rendererWrapper = new CTabFolderRendererWrapper(this);

		IEclipsePreferences preferences = getSwtRendererPreferences();
		preferences.addPreferenceChangeListener(this);
		parent.addDisposeListener(e -> preferences.removePreferenceChangeListener(this));

		cornerRadiusPreferenceChanged();
	}

	@Override
	public void setUnselectedHoverColor(Color newColor) {
		this.unselectedHoverColor = newColor;
	}

	@Override
	public void setUnselectedHoverBorderColor(Color newColor) {
		this.unselectedHoverBorderColor = newColor;
	}

	@Override
	public void setSelectedHoverBorderColor(Color newColor) {
		this.selectedHoverBorderColor = newColor;
	}

	@Override
	public void setWrappedTabFolderColor(Color newColor) {
		this.wrappedTabFolderColor = newColor;
	}

	@Override
	public void setFillToolbarArea(boolean fillToolbarArea) {
		this.fillToolbarArea = fillToolbarArea;
	}

	@Override
	public void setUnselectedHotTabsColorBackground(Color color) {
		this.unselectedHoverColor = color;
	}

	@Override
	protected Rectangle computeTrim(int part, int state, int x, int y, int width, int height) {
		boolean onBottom = parent.getTabPosition() == SWT.BOTTOM;
		int borderTop = onBottom ? INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH : TOP_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH;
		int borderBottom = onBottom ? TOP_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH
				: INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH;
		int marginWidth = parent.marginWidth;
		int marginHeight = parent.marginHeight;
		int sideDropWidth = 0;

		// Trim is not affected by the corner size.
		switch (part) {
		case PART_BODY:
			if (state == SWT.FILL) {
				x = -1 - paddingLeft;
				int tabHeight = parent.getTabHeight();
				y = onBottom ? y - paddingTop - marginHeight - borderTop - TAB_OUTLINE_WIDTH
						: y - paddingTop - marginHeight - tabHeight - borderTop - TAB_OUTLINE_WIDTH;
				width = 2 + paddingLeft + paddingRight;
				height += paddingTop + paddingBottom + TAB_OUTLINE_WIDTH;
				height += tabHeight + borderBottom + borderTop;
			} else {
				x = x - marginWidth - OUTER_KEYLINE_WIDTH - INNER_KEYLINE_WIDTH - sideDropWidth;
				width = width + 2 * OUTER_KEYLINE_WIDTH + 2 * INNER_KEYLINE_WIDTH + 2 * marginWidth + 2 * sideDropWidth;
				int tabHeight = parent.getTabHeight();
				if (parent.getMinimized()) {
					y = onBottom ? y - borderTop - 5 : y - tabHeight - borderTop - 5;
					height = borderTop + borderBottom + tabHeight;
				} else {
					y = onBottom ? y - marginHeight - borderTop
							: y - marginHeight - tabHeight - borderTop - TAB_OUTLINE_WIDTH;
					height = height + borderBottom + borderTop + 2 * marginHeight + tabHeight + TAB_OUTLINE_WIDTH;
				}
			}
			break;
		case PART_HEADER:
			x = x - (INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH) - sideDropWidth;
			width = width + 2 * (INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH + sideDropWidth);
			break;
		case PART_BORDER:
			x = x - INNER_KEYLINE_WIDTH - OUTER_KEYLINE_WIDTH - sideDropWidth - ITEM_LEFT_MARGIN;
			width = width + 2 * (INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH + sideDropWidth) + ITEM_RIGHT_MARGIN;
			height = height + borderTop + borderBottom;
			y = y - borderTop;
			break;
		default:
			if (0 <= part && part < parent.getItemCount()) {
				x = x - ITEM_LEFT_MARGIN;// - (CORNER_SIZE/2);
				width = width + ITEM_LEFT_MARGIN + ITEM_RIGHT_MARGIN + 1;
				y = y - ITEM_TOP_MARGIN;
				height = height + ITEM_TOP_MARGIN + ITEM_BOTTOM_MARGIN;
			}
			break;
		}
		return new Rectangle(x, y, width, height);
	}

	@Override
	protected Point computeSize(int part, int state, GC gc, int wHint, int hHint) {
		wHint += paddingLeft + paddingRight;
		hHint += paddingTop + paddingBottom;
		if (0 <= part && part < parent.getItemCount()) {
			gc.setAdvanced(true);
			return super.computeSize(part, state, gc, wHint, hHint);
		}
		return super.computeSize(part, state, gc, wHint, hHint);
	}

	@Override
	protected void draw(int part, int state, Rectangle bounds, GC gc) {

		switch (part) {
		case PART_BACKGROUND:
			if (this.drawCustomTabContentBackground) {
				this.drawCustomBackground(gc, bounds, state);
			} else {
				super.draw(part, state, bounds, gc);
			}
			return;
		case PART_BODY:
			this.drawTabBody(gc, bounds, state);
			return;
		case PART_HEADER:
			this.drawTabHeader(gc, bounds, state);
			return;
		default:
			if (0 <= part && part < parent.getItemCount()) {
				// Sometimes the clipping is incorrect, see Bug 428697 and Bug 563345
				// Resetting it before draw the tabs prevents draw issues.
				gc.setClipping((Rectangle) null);
				gc.setAdvanced(true);
				if (bounds.width == 0 || bounds.height == 0) {
					return;
				}
				if ((state & SWT.SELECTED) != 0) {
					drawSelectedTab(part, gc, bounds, state);
					state &= ~SWT.BACKGROUND;
					super.draw(part, state, bounds, gc);
				} else {
					drawUnselectedTab(part, gc, bounds, state);
					if ((state & SWT.HOT) == 0 && !active) {
						gc.setAlpha(0x7f);
						state &= ~SWT.BACKGROUND;
						super.draw(part, state, bounds, gc);
						gc.setAlpha(0xff);
					} else {
						state &= ~SWT.BACKGROUND;
						super.draw(part, state, bounds, gc);
					}
				}
				return;
			}
		}
		super.draw(part, state, bounds, gc);
	}

	void drawTabHeader(GC gc, Rectangle bounds, int state) {
		boolean onBottom = parent.getTabPosition() == SWT.BOTTOM;
		// TODO: this needs to be added to computeTrim for HEADER
		int header = 1;
		Rectangle trim = computeTrim(PART_HEADER, state, 0, 0, 0, 0);
		trim.width = bounds.width - trim.width;

		// XXX: The magic numbers need to be cleaned up. See
		// https://bugs.eclipse.org/425777 for details.
		trim.height = (parent.getTabHeight() + (onBottom ? 7 : 4)) - trim.height;

		trim.x = -trim.x;
		trim.y = onBottom ? bounds.height - parent.getTabHeight() - 1 - header : -trim.y;
		draw(PART_BACKGROUND, SWT.NONE, trim, gc);

		if (outerKeylineColor == null)
			outerKeylineColor = gc.getDevice().getSystemColor(SWT.COLOR_BLACK);
		gc.setForeground(outerKeylineColor);

		if (cornerSize == SQUARE_CORNER) {
			gc.drawRectangle(rectShape);
		} else {
			gc.drawPolyline(shape);
		}
	}

	void drawTabBody(GC gc, Rectangle bounds, int state) {
		int marginWidth = parent.marginWidth;
		int marginHeight = parent.marginHeight;
		int delta = INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH + 2 * marginWidth;
		int width = bounds.width - delta;
		int height = Math.max(
				parent.getTabHeight() + INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH,
				bounds.height - INNER_KEYLINE_WIDTH - OUTER_KEYLINE_WIDTH - 2 * marginHeight);

		// Remember for use in header drawing
		if (cornerSize == SQUARE_CORNER) {
			Rectangle rect = new Rectangle(bounds.x, bounds.y, width, height);
			gc.fillRectangle(rect);
			rectShape = rect;
		} else {
			int[] points = new int[1024];
			int index = 0;
			int radius = cornerSize / 2;

			int circX = bounds.x + delta / 2 + radius;
			int circY = bounds.y + radius;

			// Body
			int[] ltt = drawCircle(circX, circY, radius, LEFT_TOP);
			System.arraycopy(ltt, 0, points, index, ltt.length);
			index += ltt.length;

			int[] lbb = drawCircle(circX, circY + height - (radius * 2), radius, LEFT_BOTTOM);
			System.arraycopy(lbb, 0, points, index, lbb.length);
			index += lbb.length;

			int[] rb = drawCircle(circX + width - (radius * 2), circY + height - (radius * 2), radius,
					RIGHT_BOTTOM);
			System.arraycopy(rb, 0, points, index, rb.length);
			index += rb.length;

			int[] rt = drawCircle(circX + width - (radius * 2), circY, radius, RIGHT_TOP);
			System.arraycopy(rt, 0, points, index, rt.length);
			index += rt.length;
			points[index++] = circX;
			points[index++] = circY - radius;

			int[] tempPoints = new int[index];
			System.arraycopy(points, 0, tempPoints, 0, index);
			gc.fillPolygon(tempPoints);

			// Remember for use in header drawing
			shape = tempPoints;
		}

	}

	private int[] computeSquareTabOutline(boolean onBottom, int startX, int endX, int bottomY,
			Rectangle bounds, Point parentSize) {
		int index = 0;
		int outlineY = onBottom ? bottomY + bounds.height : bounds.y;
		int[] points = new int[20];

		int margin = (Objects.equals(outerKeylineColor, tabOutlineColor)
						|| Objects.equals(outerKeylineColor, parent.getBackground())
						? 0
						: 1);
		points[index++] = margin;
		points[index++] = bottomY;
		points[index++] = startX;
		points[index++] = bottomY;

		points[index++] = startX;
		points[index++] = outlineY;

		points[index++] = endX;
		points[index++] = outlineY;

		points[index++] = endX;
		points[index++] = bottomY;

		if (active) {
			points[index++] = parentSize.x - 1 - margin;
			points[index++] = bottomY;
		}

		points[index++] = parentSize.x - 1 - margin;
		points[index++] = parentSize.y - 1;

		points[index++] = points[0];
		points[index++] = parentSize.y - 1;

		points[index++] = points[0];
		points[index++] = points[1];

		int[] tmpPoints = new int[index];
		System.arraycopy(points, 0, tmpPoints, 0, index);

		return tmpPoints;
	}


	private int[] computeRoundTabOutline(int itemIndex, boolean onBottom, int bottomY, Rectangle bounds,
			Point parentSize) {
		int header = 0;
		int width = bounds.width;
		int[] points = new int[1024];
		int index = 0;
		int radius = cornerSize / 2;
		int circX = bounds.x + radius;
		int selectionX1, selectionY1, selectionX2, selectionY2;
		int circY = onBottom ? bounds.y + bounds.height - 1 - header - radius : bounds.y + radius;
		if (itemIndex == 0 && bounds.x == -computeTrim(CTabFolderRenderer.PART_HEADER, SWT.NONE, 0, 0, 0, 0).x) {
			circX -= 1;
			points[index++] = circX - radius + 1;
			points[index++] = bottomY;

			points[index++] = selectionX1 = circX + 1 - radius;
			points[index++] = bottomY;
		} else {
			if (active) {
				points[index++] = INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH;
				points[index++] = bottomY;
			}
			points[index++] = bounds.x;
			points[index++] = bottomY;
		}

		int startX = -1, endX = -1;
		if (!onBottom) {
			int[] ltt = drawCircle(circX + 1, circY, radius, LEFT_TOP);
			startX = ltt[6];
			for (int i = 0; i < ltt.length / 2; i += 2) {
				int tmp = ltt[i];
				ltt[i] = ltt[ltt.length - i - 2];
				ltt[ltt.length - i - 2] = tmp;
				tmp = ltt[i + 1];
				ltt[i + 1] = ltt[ltt.length - i - 1];
				ltt[ltt.length - i - 1] = tmp;
			}
			System.arraycopy(ltt, 0, points, index, ltt.length);
			index += ltt.length;

			int[] rt = drawCircle(circX + width - (radius * 2), circY, radius, RIGHT_TOP);
			endX = rt[rt.length - 4];
			for (int i = 0; i < rt.length / 2; i += 2) {
				int tmp = rt[i];
				rt[i] = rt[rt.length - i - 2];
				rt[rt.length - i - 2] = tmp;
				tmp = rt[i + 1];
				rt[i + 1] = rt[rt.length - i - 1];
				rt[rt.length - i - 1] = tmp;
			}
			System.arraycopy(rt, 0, points, index, rt.length);
			index += rt.length;

			points[index++] = selectionX2 = bounds.width + circX - radius;
			points[index++] = selectionY2 = bounds.y + bounds.height;
		} else {
			int[] ltt = drawCircle(circX + 1, circY, radius, LEFT_BOTTOM);
			startX = ltt[6];
			System.arraycopy(ltt, 0, points, index, ltt.length);
			index += ltt.length;

			int[] rt = drawCircle(circX + width - (radius * 2), circY, radius, RIGHT_BOTTOM);
			endX = rt[rt.length - 4];
			System.arraycopy(rt, 0, points, index, rt.length);
			index += rt.length;

			points[index++] = selectionX2 = bounds.width + circX - radius;
			points[index++] = selectionY2 = bottomY;
		}

		if (active) {
			points[index++] = parentSize.x + INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH;
			points[index++] = bottomY;
		}

		int[] tmpPoints = new int[index];
		System.arraycopy(points, 0, tmpPoints, 0, index);
		return tmpPoints;
	}

	void drawSelectedTab(int itemIndex, GC gc, Rectangle bounds, int state) {
		if (parent.getSingle() && parent.getItem(itemIndex).isShowing())
			return;

		boolean onBottom = parent.getTabPosition() == SWT.BOTTOM;
		int header = 0;
		int bottomY = onBottom ? bounds.y - header : bounds.y + bounds.height;
		int selectionX1, selectionY1, selectionX2, selectionY2;
		int startX, endX;
		int[] tabOutlinePoints = null;
		Point parentSize = parent.getSize();

		gc.setClipping(0, onBottom ? bounds.y - header : bounds.y,
				parentSize.x + INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH,
				bounds.y + bounds.height);// bounds.height

		Pattern backgroundPattern = null;
		if (selectedTabFillColors == null) {
			setSelectedTabFill(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
		}
		if (selectedTabFillColors.length == 1) {
			gc.setBackground(selectedTabFillColors[0]);
			gc.setForeground(selectedTabFillColors[0]);
		} else if (selectedTabFillColors.length == 2) {
			// for now we support the 2-colors gradient for selected tab
			if (!onBottom) {
				backgroundPattern = new Pattern(gc.getDevice(), 0, 0, 0, bounds.height + 1, selectedTabFillColors[0],
						selectedTabFillColors[1]);
			} else {
				backgroundPattern = new Pattern(gc.getDevice(), 0, 0, 0, bounds.height + 1, selectedTabFillColors[1],
						selectedTabFillColors[0]);
			}

			gc.setBackgroundPattern(backgroundPattern);
			gc.setForeground(selectedTabFillColors[1]);
		}

		startX = bounds.x - 1;
		endX = bounds.x + bounds.width;
		selectionX1 = startX + 1;
		selectionY1 = bottomY;
		selectionX2 = endX - 1;
		selectionY2 = bottomY;

		boolean superimposeKeylineOutline = Objects.equals(outerKeylineColor, tabOutlineColor);
		Rectangle outlineBoundsForOutline = new Rectangle( //
				superimposeKeylineOutline ? bounds.x - OUTER_KEYLINE_WIDTH : bounds.x,
				!onBottom && superimposeKeylineOutline ? bounds.y - OUTER_KEYLINE_WIDTH : bounds.y,
				superimposeKeylineOutline ? bounds.width + OUTER_KEYLINE_WIDTH : bounds.width, //
				bounds.height);
		if (cornerSize == SQUARE_CORNER) {
			tabOutlinePoints = computeSquareTabOutline(onBottom, startX, endX, bottomY, outlineBoundsForOutline,
					parentSize);
			outlineBoundsForOutline.height += TAB_OUTLINE_WIDTH; // increase area to fill by outline thickness
			gc.fillRectangle(outlineBoundsForOutline);
		} else {
			tabOutlinePoints = computeRoundTabOutline(itemIndex, onBottom, bottomY, outlineBoundsForOutline,
					parentSize);
			gc.fillPolygon(tabOutlinePoints);
		}

		gc.drawLine(selectionX1, selectionY1, selectionX2, selectionY2);
		if (tabOutlineColor == null) {
			tabOutlineColor = gc.getDevice().getSystemColor(SWT.COLOR_BLACK);
		}
		gc.setForeground(tabOutlineColor);

		Color gradientLineTop = null;
		Pattern foregroundPattern = null;
		if (!active && !onBottom) {
			gradientLineTop = getGradientLineTop(gc);
			foregroundPattern = new Pattern(gc.getDevice(), 0, 0, 0, bounds.height + 1, gradientLineTop,
					gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
			gc.setForegroundPattern(foregroundPattern);
		}
		if ((state & SWT.HOT) != 0) {
			if (selectedHoverBorderColor == null) {
				gradientLineTop = getGradientLineTop(gc);
				foregroundPattern = new Pattern(gc.getDevice(), 0, 0, 0, bounds.height + 1, gradientLineTop,
						gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
				gc.setForegroundPattern(foregroundPattern);
			} else {
				gc.setForeground(selectedHoverBorderColor);
			}
		}

		gc.setClipping((Rectangle) null);

		if (active) {
			if (outerKeylineColor == null)
				outerKeylineColor = gc.getDevice().getSystemColor(SWT.COLOR_RED);
			gc.setForeground(outerKeylineColor);
			if (cornerSize == SQUARE_CORNER) {
				gc.drawRectangle(rectShape);
			} else {
				gc.drawPolyline(shape);
			}
		} else if (!onBottom) {
			gc.drawLine(startX, 0, endX, 0);
		}

		if (selectedTabHighlightColor != null) {
			gc.setBackground(selectedTabHighlightColor);
			boolean highlightOnTop = drawTabHighlightOnTop;
			if (onBottom) {
				highlightOnTop = !highlightOnTop;
			}
			int verticalOffset = highlightOnTop ? 0 : bounds.height - 2;
			int horizontalOffset = itemIndex == 0 || cornerSize == SQUARE_CORNER ? 0 : 1;
			int widthAdjustment = cornerSize == SQUARE_CORNER ? 0 : 1;
			gc.fillRectangle(bounds.x + horizontalOffset, bounds.y + verticalOffset, bounds.width - widthAdjustment, 3);
		}

		if (backgroundPattern != null) {
			backgroundPattern.dispose();
		}
		if (foregroundPattern != null) {
			foregroundPattern.dispose();
		}

		gc.setForeground(tabOutlineColor);
		if (TAB_OUTLINE_WIDTH > 0) {
			gc.drawPolyline(tabOutlinePoints);
		}
	}

	void drawUnselectedTab(int itemIndex, GC gc, Rectangle bounds, int state) {
		int header = 0;
		int width = bounds.width;
		boolean onBottom = parent.getTabPosition() == SWT.BOTTOM;
		int[] points = new int[1024];
		int[] inactive = new int[8];
		int index = 0, inactive_index = 0;
		int radius = cornerSize / 2;
		int circX = bounds.x + radius;
		int circY = onBottom ? bounds.y + bounds.height - 1 - header - radius : bounds.y + 1 + radius;
		int bottomY = onBottom ? bounds.y - header : bounds.y + bounds.height;

		int leftIndex = circX;
		if (itemIndex == 0) {
			points[index++] = leftIndex - radius + 1;
			points[index++] = bottomY;
		} else {
			points[index++] = bounds.x + 1;
			points[index++] = bottomY;
		}

		if (!active) {
			System.arraycopy(points, 0, inactive, 0, index);
			inactive_index += 2;
		}

		int rightIndex = circX - 1;
		if (!onBottom) {
			int[] ltt = drawCircle(leftIndex + 1, circY, radius, LEFT_TOP);
			for (int i = 0; i < ltt.length / 2; i += 2) {
				int tmp = ltt[i];
				ltt[i] = ltt[ltt.length - i - 2];
				ltt[ltt.length - i - 2] = tmp;
				tmp = ltt[i + 1];
				ltt[i + 1] = ltt[ltt.length - i - 1];
				ltt[ltt.length - i - 1] = tmp;
			}
			System.arraycopy(ltt, 0, points, index, ltt.length);
			index += ltt.length;

			if (!active) {
				System.arraycopy(ltt, 0, inactive, inactive_index, 2);
				inactive_index += 2;
			}

			int[] rt = drawCircle(rightIndex + width - (radius * 2), circY, radius, RIGHT_TOP);
			for (int i = 0; i < rt.length / 2; i += 2) {
				int tmp = rt[i];
				rt[i] = rt[rt.length - i - 2];
				rt[rt.length - i - 2] = tmp;
				tmp = rt[i + 1];
				rt[i + 1] = rt[rt.length - i - 1];
				rt[rt.length - i - 1] = tmp;
			}
			System.arraycopy(rt, 0, points, index, rt.length);
			index += rt.length;
			if (!active) {
				System.arraycopy(rt, rt.length - 4, inactive, inactive_index, 2);
				inactive[inactive_index] -= 1;
				inactive_index += 2;
			}
		} else {
			int[] ltt = drawCircle(leftIndex + 1, circY, radius, LEFT_BOTTOM);
			System.arraycopy(ltt, 0, points, index, ltt.length);
			index += ltt.length;

			if (!active) {
				System.arraycopy(ltt, 0, inactive, inactive_index, 2);
				inactive_index += 2;
			}

			int[] rt = drawCircle(rightIndex + width - (radius * 2), circY, radius, RIGHT_BOTTOM);
			System.arraycopy(rt, 0, points, index, rt.length);
			index += rt.length;
			if (!active) {
				System.arraycopy(rt, rt.length - 4, inactive, inactive_index, 2);
				inactive[inactive_index] -= 1;
				inactive_index += 2;
			}

		}

		points[index++] = bounds.width + rightIndex - radius;
		points[index++] = bottomY;

		if (!active) {
			System.arraycopy(points, index - 2, inactive, inactive_index, 2);
			inactive[inactive_index] -= 1;
			inactive_index += 2;
		}
		gc.setClipping(points[0], onBottom ? bounds.y - header : bounds.y,
				parent.getSize().x - (0 + INNER_KEYLINE_WIDTH + OUTER_KEYLINE_WIDTH), bounds.y + bounds.height);
		Color color = unselectedHoverColor;
		if (color == null) {
			// Fallback: if color was not set, use white for highlighting
			// hot tab. }
			color = gc.getDevice().getSystemColor(SWT.COLOR_WHITE);
		}
		gc.setBackground(color);
		int[] tmpPoints = new int[index];
		System.arraycopy(points, 0, tmpPoints, 0, index);
		Color tempBorder = new Color(gc.getDevice(), 182, 188, 204);
		if ((state & SWT.HOT) != 0) {
			gc.fillPolygon(tmpPoints);
			gc.setForeground(unselectedHoverBorderColor == null ? tempBorder : unselectedHoverBorderColor);
			gc.drawPolyline(tmpPoints);
		} else {
			gc.setForeground(unselectedTabOutlineColor == null ? tempBorder : unselectedTabOutlineColor);
			if (active) {
				gc.drawPolyline(tmpPoints);
			} else {
				gc.drawLine(inactive[0], inactive[1], inactive[2], inactive[3]);
				gc.drawLine(inactive[4], inactive[5], inactive[6], inactive[7]);
			}
		}
		tempBorder.dispose();

		Rectangle rect = null;
		gc.setClipping(rect);

		// gc.setForeground(outerKeyline);
		// gc.drawPolyline(shape);
	}

	static int[] drawCircle(int xC, int yC, int r, int circlePart) {
		int x = 0, y = r, u = 1, v = 2 * r - 1, e = 0;
		int[] points = new int[1024];
		int[] pointsMirror = new int[1024];
		int loop = 0;
		int loopMirror = 0;
		while (x < y) {
			if (circlePart == RIGHT_BOTTOM) {
				points[loop++] = xC + x;
				points[loop++] = yC + y;
			}
			if (circlePart == RIGHT_TOP) {
				points[loop++] = xC + y;
				points[loop++] = yC - x;
			}
			if (circlePart == LEFT_TOP) {
				points[loop++] = xC - x;
				points[loop++] = yC - y;
			}
			if (circlePart == LEFT_BOTTOM) {
				points[loop++] = xC - y;
				points[loop++] = yC + x;
			}
			x++;
			e += u;
			u += 2;
			if (v < 2 * e) {
				y--;
				e -= v;
				v -= 2;
			}
			if (x > y) {
				break;
			}
			if (circlePart == RIGHT_BOTTOM) {
				pointsMirror[loopMirror++] = xC + y;
				pointsMirror[loopMirror++] = yC + x;
			}
			if (circlePart == RIGHT_TOP) {
				pointsMirror[loopMirror++] = xC + x;
				pointsMirror[loopMirror++] = yC - y;
			}
			if (circlePart == LEFT_TOP) {
				pointsMirror[loopMirror++] = xC - y;
				pointsMirror[loopMirror++] = yC - x;
			}
			if (circlePart == LEFT_BOTTOM) {
				pointsMirror[loopMirror++] = xC - x;
				pointsMirror[loopMirror++] = yC + y;
			}
			// grow?
			if ((loop + 1) > points.length) {
				int length = points.length * 2;
				int[] newPointTable = new int[length];
				int[] newPointTableMirror = new int[length];
				System.arraycopy(points, 0, newPointTable, 0, points.length);
				points = newPointTable;
				System.arraycopy(pointsMirror, 0, newPointTableMirror, 0, pointsMirror.length);
				pointsMirror = newPointTableMirror;
			}
		}
		int[] finalArray = new int[loop + loopMirror];
		System.arraycopy(points, 0, finalArray, 0, loop);
		for (int i = loopMirror - 1, j = loop; i > 0; i = i - 2, j = j + 2) {
			int tempY = pointsMirror[i];
			int tempX = pointsMirror[i - 1];
			finalArray[j] = tempX;
			finalArray[j + 1] = tempY;
		}
		return finalArray;
	}

	static RGB blend(RGB c1, RGB c2, int ratio) {
		int r = blend(c1.red, c2.red, ratio);
		int g = blend(c1.green, c2.green, ratio);
		int b = blend(c1.blue, c2.blue, ratio);
		return new RGB(r, g, b);
	}

	static int blend(int v1, int v2, int ratio) {
		int b = (ratio * v1 + (100 - ratio) * v2) / 100;
		return Math.min(255, b);
	}


	public ImageData blur(Image src, int radius, int sigma) {
		float[] kernel = create1DKernel(radius, sigma);

		ImageData imgPixels = src.getImageData();
		int width = imgPixels.width;
		int height = imgPixels.height;

		int[] inPixels = new int[width * height];
		int[] outPixels = new int[width * height];
		int offset = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				RGB rgb = imgPixels.palette.getRGB(imgPixels.getPixel(x, y));
				if (rgb.red == 255 && rgb.green == 255 && rgb.blue == 255) {
					inPixels[offset] = (rgb.red << 16) | (rgb.green << 8) | rgb.blue;
				} else {
					inPixels[offset] = (imgPixels.getAlpha(x, y) << 24) | (rgb.red << 16) | (rgb.green << 8) | rgb.blue;
				}
				offset++;
			}
		}

		convolve(kernel, inPixels, outPixels, width, height, true);
		convolve(kernel, outPixels, inPixels, height, width, true);

		ImageData dst = new ImageData(imgPixels.width, imgPixels.height, 24, new PaletteData(0xff0000, 0xff00, 0xff));

		dst.setPixels(0, 0, inPixels.length, inPixels, 0);
		offset = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (inPixels[offset] == -1) {
					dst.setAlpha(x, y, 0);
				} else {
					int a = (inPixels[offset] >> 24) & 0xff;
					// if (a < 150) a = 0;
					dst.setAlpha(x, y, a);
				}
				offset++;
			}
		}
		return dst;
	}

	private void convolve(float[] kernel, int[] inPixels, int[] outPixels, int width, int height, boolean alpha) {
		int kernelWidth = kernel.length;
		int kernelMid = kernelWidth / 2;
		for (int y = 0; y < height; y++) {
			int index = y;
			int currentLine = y * width;
			for (int x = 0; x < width; x++) {
				// do point
				float a = 0, r = 0, g = 0, b = 0;
				for (int k = -kernelMid; k <= kernelMid; k++) {
					float val = kernel[k + kernelMid];
					int xcoord = x + k;
					if (xcoord < 0) {
						xcoord = 0;
					}
					if (xcoord >= width) {
						xcoord = width - 1;
					}
					int pixel = inPixels[currentLine + xcoord];
					// float alp = ((pixel >> 24) & 0xff);
					a += val * ((pixel >> 24) & 0xff);
					r += val * (((pixel >> 16) & 0xff));
					g += val * (((pixel >> 8) & 0xff));
					b += val * (((pixel) & 0xff));
				}
				int ia = alpha ? clamp((int) (a + 0.5)) : 0xff;
				int ir = clamp((int) (r + 0.5));
				int ig = clamp((int) (g + 0.5));
				int ib = clamp((int) (b + 0.5));
				outPixels[index] = (ia << 24) | (ir << 16) | (ig << 8) | ib;
				index += height;
			}
		}

	}

	private int clamp(int value) {
		if (value > 255) {
			return 255;
		}
		if (value < 0) {
			return 0;
		}
		return value;
	}

	private float[] create1DKernel(int radius, int sigma) {
		// guideline: 3*sigma should be the radius
		int size = radius * 2 + 1;
		float[] kernel = new float[size];
		int radiusSquare = radius * radius;
		float sigmaSquare = 2 * sigma * sigma;
		float piSigma = 2 * (float) Math.PI * sigma;
		float sqrtSigmaPi2 = (float) Math.sqrt(piSigma);
		int start = size / 2;
		int index = 0;
		float total = 0;
		for (int i = -start; i <= start; i++) {
			float d = i * i;
			if (d > radiusSquare) {
				kernel[index] = 0;
			} else {
				kernel[index] = (float) Math.exp(-(d) / sigmaSquare) / sqrtSigmaPi2;
			}
			total += kernel[index];
			index++;
		}
		for (int i = 0; i < size; i++) {
			kernel[i] /= total;
		}
		return kernel;
	}

	public Rectangle getPadding() {
		return new Rectangle(paddingTop, paddingRight, paddingBottom, paddingLeft);
	}

	public void setPadding(int paddingLeft, int paddingRight, int paddingTop, int paddingBottom) {
		this.paddingLeft = paddingLeft;
		this.paddingRight = paddingRight;
		this.paddingTop = paddingTop;
		this.paddingBottom = paddingBottom;
		parent.redraw();
	}

	@Override
	public void setCornerRadius(int radius) {
		cornerSize = (radius < 6) ? 0 : radius;
		parent.redraw();
	}

	@Override
	public void setOuterKeyline(Color color) {
		this.outerKeylineColor = color;
		// TODO: HACK! Should be set based on pseudo-state.
		if (color != null) {
			setActive(!(color.getRed() == 255 && color.getGreen() == 255 && color.getBlue() == 255));
		}
		parent.redraw();
	}

	@Override
	public void setSelectedTabHighlight(Color color) {
		this.selectedTabHighlightColor = color;
		parent.redraw();
	}

	@Override
	public void setSelectedTabFill(Color color) {
		setSelectedTabFill(new Color[] { color }, new int[] { 100 });
	}

	@Override
	public void setSelectedTabFill(Color[] colors, int[] percents) {
		selectedTabFillColors = colors;
		selectedTabFillPercents = percents;
		parent.redraw();
	}

	@Override
	public void setUnselectedTabsColor(Color color) {
		setUnselectedTabsColor(new Color[] { color }, new int[] { 100 });
	}

	@Override
	public void setUnselectedTabsColor(Color[] colors, int[] percents) {
		unselectedTabsColors = colors;
		unselectedTabsPercents = percents;
		parent.redraw();
	}

	@Override
	public void setTabOutline(Color color) {
		this.tabOutlineColor = color;
		parent.redraw();
	}

	@Override
	public void setUnselectedTabOutline(Color color) {
		this.unselectedTabOutlineColor = color;
		parent.redraw();
	}

	@Override
	public void setInnerKeyline(Color color) {
		this.innerKeylineColor = color;
		parent.redraw();
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * Sets whether to use a custom tab background (reusing tab colors and
	 * gradients), or default one from plain CTabFolder (using widget background
	 * color).
	 *
	 * @param drawCustomTabContentBackground
	 */
	@Override
	public void setDrawCustomTabContentBackground(boolean drawCustomTabContentBackground) {
		this.drawCustomTabContentBackground = drawCustomTabContentBackground;
	}

	/**
	 * Draws tab content background, deriving the colors from the tab colors.
	 *
	 * @param gc
	 * @param bounds
	 * @param state
	 */
	private void drawCustomBackground(GC gc, Rectangle bounds, int state) {
		boolean selected = (state & SWT.SELECTED) != 0;
		boolean vertical = selected ? parentWrapper.isSelectionGradientVertical() : parentWrapper.isGradientVertical();
		Rectangle partHeaderBounds = computeTrim(PART_HEADER, state, bounds.x, bounds.y, bounds.width, bounds.height);

		drawUnselectedTabBackground(gc, partHeaderBounds, state, vertical, parent.getBackground());
		drawTabBackground(gc, partHeaderBounds, state, vertical, parent.getBackground());
		drawChildrenBackground(partHeaderBounds);
	}

	private void drawUnselectedTabBackground(GC gc, Rectangle partHeaderBounds, int state, boolean vertical,
			Color defaultBackground) {
		if (unselectedTabsColors == null) {
			boolean selected = (state & SWT.SELECTED) != 0;
			unselectedTabsColors = selected ? parentWrapper.getSelectionGradientColors()
					: parentWrapper.getGradientColors();
			unselectedTabsPercents = selected ? parentWrapper.getSelectionGradientPercents()
					: parentWrapper.getGradientPercents();
		}
		if (unselectedTabsColors == null) {
			unselectedTabsColors = new Color[] { gc.getDevice().getSystemColor(SWT.COLOR_WHITE) };
			unselectedTabsPercents = new int[] { 100 };
		}

		rendererWrapper.drawBackground(gc, partHeaderBounds.x, partHeaderBounds.y - 1, partHeaderBounds.width,
				partHeaderBounds.height,
				defaultBackground, unselectedTabsColors, unselectedTabsPercents, vertical);
	}

	private void drawTabBackground(GC gc, Rectangle partHeaderBounds, int state, boolean vertical,
			Color defaultBackground) {
		boolean selected = (state & SWT.SELECTED) != 0;
		Color[] colors = selectedTabFillColors;
		int[] percents = selectedTabFillPercents;
		if (!selected) {
			colors = unselectedTabsColors;
			percents = unselectedTabsPercents;
		}
		if (colors == null) {
			colors = selected ? parentWrapper.getSelectionGradientColors() : parentWrapper.getGradientColors();
			percents = selected ? parentWrapper.getSelectionGradientPercents() : parentWrapper.getGradientPercents();
		}
		if (colors == null) {
			colors = new Color[] { gc.getDevice().getSystemColor(SWT.COLOR_WHITE) };
			percents = new int[] { 100 };
		}
		if (colors != null) {
			if (colors.length == 2) {
				colors = new Color[] { colors[0], colors[1] };
			} else {
				colors = new Color[] { colors[0], colors[0] };
			}
		}

		rendererWrapper.drawBackground(gc, partHeaderBounds.x, partHeaderBounds.height, partHeaderBounds.width,
				parent.getBounds().height, defaultBackground, colors, percents, vertical);
		Control topRight = parent.getTopRight();
		if (topRight != null) {
			if (topRight.getBounds().y > 5) {
				topRight.setBackground(wrappedTabFolderColor == null ? colors[1] : wrappedTabFolderColor);

				if (fillToolbarArea) {
					colors = new Color[] { wrappedTabFolderColor == null ? colors[1] : wrappedTabFolderColor };
					percents = new int[] { 100 };
					rendererWrapper.drawBackground(gc, partHeaderBounds.x, partHeaderBounds.height,
							partHeaderBounds.width, topRight.getBounds().height, defaultBackground, colors, percents,
							vertical);
				}
			} else {
				topRight.setBackground(null);
			}
		}
	}

	// Workaround for the bug 433276. Remove it when the bug gets fixed
	private void drawChildrenBackground(Rectangle partHeaderBounds) {
		// for (Control control : parent.getChildren()) {
		// if (!hasBackgroundOverriddenByCSS(control)
		// && containsToolbar(control)) {
		// drawChildBackground((Composite) control, partHeaderBounds);
		// }
		// }
	}

	private boolean containsToolbar(Control control) {
		if (control.getData(CONTAINS_TOOLBAR) != null) {
			return true;
		}

		if (control instanceof Composite) {
			for (Control child : ((Composite) control).getChildren()) {
				if (child instanceof ToolBar) {
					control.setData(CONTAINS_TOOLBAR, true);
					return true;
				}
			}
		}
		return false;
	}

	private Color getGradientLineTop(GC gc) {
		RGB blendColor = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW).getRGB();
		RGB topGradient = blend(blendColor, tabOutlineColor.getRGB(), 40);
		Color gradientLineTop = new Color(gc.getDevice(), topGradient);
		return gradientLineTop;
	}

	private static class CTabFolderRendererWrapper extends ReflectionSupport<CTabFolderRenderer> {
		private Method drawBackgroundMethod;

		public CTabFolderRendererWrapper(CTabFolderRenderer instance) {
			super(instance);
		}

		public void drawBackground(GC gc, int x, int y, int width, int height, Color defaultBackground, Color[] colors,
				int[] percents, boolean vertical) {
			if (drawBackgroundMethod == null) {
				drawBackgroundMethod = getMethod("drawBackground", //$NON-NLS-1$
						new Class<?>[] { GC.class, int[].class, int.class, int.class, int.class, int.class, Color.class,
								Image.class, Color[].class, int[].class, boolean.class });
			}
			executeMethod(drawBackgroundMethod, new Object[] { gc, null, x, y, width, height, defaultBackground, null,
					colors, percents, vertical });
		}
	}

	private static class CTabFolderWrapper extends ReflectionSupport<CTabFolder> {
		private Field selectionGradientVerticalField;

		private Field gradientVerticalField;

		private Field selectionGradientColorsField;

		private Field selectionGradientPercentsField;

		private Field gradientColorsField;

		private Field gradientPercentsField;

		public CTabFolderWrapper(CTabFolder instance) {
			super(instance);
		}

		public boolean isSelectionGradientVertical() {
			if (selectionGradientVerticalField == null) {
				selectionGradientVerticalField = getField("selectionGradientVertical"); //$NON-NLS-1$
			}
			Boolean result = (Boolean) getFieldValue(selectionGradientVerticalField);
			return result != null ? result : true;
		}

		public boolean isGradientVertical() {
			if (gradientVerticalField == null) {
				gradientVerticalField = getField("gradientVertical"); //$NON-NLS-1$
			}
			Boolean result = (Boolean) getFieldValue(gradientVerticalField);
			return result != null ? result : true;
		}

		public Color[] getSelectionGradientColors() {
			if (selectionGradientColorsField == null) {
				selectionGradientColorsField = getField("selectionGradientColorsField"); //$NON-NLS-1$
			}
			return (Color[]) getFieldValue(selectionGradientColorsField);
		}

		public int[] getSelectionGradientPercents() {
			if (selectionGradientPercentsField == null) {
				selectionGradientPercentsField = getField("selectionGradientPercents"); //$NON-NLS-1$
			}
			return (int[]) getFieldValue(selectionGradientPercentsField);
		}

		public Color[] getGradientColors() {
			if (gradientColorsField == null) {
				gradientColorsField = getField("gradientColors"); //$NON-NLS-1$
			}
			return (Color[]) getFieldValue(gradientColorsField);
		}

		public int[] getGradientPercents() {
			if (gradientPercentsField == null) {
				gradientPercentsField = getField("gradientPercents"); //$NON-NLS-1$
			}
			return (int[]) getFieldValue(gradientPercentsField);
		}
	}

	private static class ReflectionSupport<T> {
		private final T instance;

		public ReflectionSupport(T instance) {
			this.instance = instance;
		}

		protected Object getFieldValue(Field field) {
			Object value = null;
			if (field != null) {
				boolean accessible = field.isAccessible();
				try {
					field.setAccessible(true);
					value = field.get(instance);
				} catch (Exception exc) {
					// do nothing
				} finally {
					field.setAccessible(accessible);
				}
			}
			return value;
		}

		protected Field getField(String name) {
			Class<?> cls = instance.getClass();
			while (!cls.equals(Object.class)) {
				try {
					return cls.getDeclaredField(name);
				} catch (Exception exc) {
					cls = cls.getSuperclass();
				}
			}
			return null;
		}

		protected Object executeMethod(Method method, Object... params) {
			Object value = null;
			if (method != null) {
				boolean accessible = method.isAccessible();
				try {
					method.setAccessible(true);
					value = method.invoke(instance, params);
				} catch (Exception exc) {
					// do nothing
				} finally {
					method.setAccessible(accessible);
				}
			}
			return value;
		}

		protected Method getMethod(String name, Class<?>... params) {
			Class<?> cls = instance.getClass();
			while (!cls.equals(Object.class)) {
				try {
					return cls.getDeclaredMethod(name, params);
				} catch (Exception exc) {
					cls = cls.getSuperclass();
				}
			}
			return null;
		}
	}

	@Override
	public void setSelectedTabHighlightTop(boolean drawTabHiglightOnTop) {
		this.drawTabHighlightOnTop = drawTabHiglightOnTop;
		parent.redraw();
	}

	private void cornerRadiusPreferenceChanged() {
		IEclipsePreferences preferences = getSwtRendererPreferences();
		boolean useRound = preferences.getBoolean(USE_ROUND_TABS, USE_ROUND_TABS_DEFAULT);
		setCornerRadius(useRound ? 16 : 0);
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent event) {
		if (!USE_ROUND_TABS.equals(event.getKey())) {
			return;
		}
		cornerRadiusPreferenceChanged();
	}

	private IEclipsePreferences getSwtRendererPreferences() {
		return InstanceScope.INSTANCE.getNode("org.eclipse.e4.ui.workbench.renderers.swt"); //$NON-NLS-1$
	}
}

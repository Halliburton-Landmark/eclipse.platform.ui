package org.eclipse.jface.resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.image.SVGHelper;

/**
 * @since 3.4
 *
 */
public class SvgImageDescriptor extends ImageDescriptor {

    private URL url;
	private int renderWidth;
	private int renderHeight;

	@SuppressWarnings("boxing")
	private static final int SIZE_PROPERTY = Integer.getInteger("svg.image.size.default", 16); //$NON-NLS-1$

	private static final String SVG_EXT = ".svg"; //$NON-NLS-1$
	private static final String PNG_EXT = ".png"; //$NON-NLS-1$
	private static final String[] IMG_SFXS = new String[] { "_16", "_24", "_32", "_8", "_10", "_48" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

	// Debug code, will be removed in future
	private static final boolean USE_OLD_SVG_IMAGES = Boolean.getBoolean("svg.old_32.enable"); //$NON-NLS-1$
	private static final boolean SHOW_MISSING_SVG_IMAGES = Boolean.getBoolean("svg.show.missing"); //$NON-NLS-1$

	/**
	 * Default size of image descriptor. Can be configurable using system property
	 * {@code svg.image.size.default}
	 */
	public static final Point DEFAULT_SIZE = new Point(SIZE_PROPERTY, SIZE_PROPERTY);

	/**
	 * @param url
	 * @param renderSize
	 */
    public SvgImageDescriptor(URL url, int renderSize) {
		this(url, renderSize, renderSize);
	}

	/**
	 * @param url
	 * @param size
	 */
	public SvgImageDescriptor(URL url, Point size) {
		this(url, size.x, size.y);
	}

	/**
	 * @param url
	 * @param width
	 * @param height
	 */
	public SvgImageDescriptor(URL url, int width, int height) {
        this.url = url;
		this.renderWidth = width;
		this.renderHeight = height;
    }

    @Override
    public ImageData getImageData(int zoom) {
		int scale = zoom / 100;
		try {
			byte[] imageBytes = SVGHelper.loadSvg(url, renderWidth * scale, renderHeight * scale);
			return new ImageData(new ByteArrayInputStream(imageBytes));
		} catch (Exception e) {
			Logger.getLogger(SvgImageDescriptor.class.getName()).severe("SVG EXCEPTION: " + url.getFile() + " " + e); //$NON-NLS-1$ //$NON-NLS-2$
			return new ImageData(DEFAULT_SIZE.x, DEFAULT_SIZE.y, 24, new PaletteData());
		}
    }

    @Override
    public Image createImage(boolean returnMissingImageOnError, Device device) {
		return new Image(device, (ImageDataProvider) this::getImageData);
    }

	/**
	 * @return URL of image
	 */
	public URL getURL() {
		return url;
	}

	/**
	 * Creates and returns a new SVG image descriptor from a URL.
	 *
	 * @param url The URL of the image file.
	 * @return a new image descriptor
	 */
	static ImageDescriptor getImageDescriptor(URL url) {
		if (!url.getPath().contains("lgc")) { //$NON-NLS-1$
			return null;
		}

		Point size = SvgImageDescriptor.DEFAULT_SIZE;
		String urlSpec = url.toString();

		String query = url.getQuery();
		if (query != null) {
			size = getCustomSizeFromQuery(query);
			urlSpec = urlSpec.replace("?" + query, ""); //$NON-NLS-1$ //$NON-NLS-2$
		}

		if (urlSpec.endsWith(PNG_EXT)) {
			String svgUrlSpec = urlSpec.replace(PNG_EXT, SVG_EXT);

			// find size suffix and remove it
			for (String sfx : IMG_SFXS) {
				if (svgUrlSpec.contains(sfx)) {
					svgUrlSpec = svgUrlSpec.replace(sfx, ""); //$NON-NLS-1$
					break;
				}
			}

			try {
				new URL(svgUrlSpec).openConnection().connect();
				urlSpec = svgUrlSpec;
			} catch (IOException e) {
				if (USE_OLD_SVG_IMAGES) {
					String oldSvgUrlSpec = getOldSvgUrl(svgUrlSpec);
					urlSpec = oldSvgUrlSpec != null ? oldSvgUrlSpec : urlSpec;
				}
			}
		}

		if (urlSpec.endsWith(SVG_EXT)) {
			try {
				return new SvgImageDescriptor(new URL(urlSpec), size);
			} catch (MalformedURLException e) {
				return getMissingImageDescriptor();
			}
		}

		if (SHOW_MISSING_SVG_IMAGES && !urlSpec.contains("/CommonButton/")) { //$NON-NLS-1$
			return getMissingImageDescriptor();
		}

		return null;
	}

	/**
	 * Get size parameters from query. SVG image can be loaded in desired size using
	 * query, example:
	 * <p>
	 * {@code platform:/path/toSvg/image.svg?width=100&height=40}<br>
	 * {@code platform:/path/toSvg/image.svg?size=48}
	 * <p>
	 * If dimensions are missing then default
	 * {@link SvgImageDescriptor#DEFAULT_SIZE} will be used
	 *
	 * @param urlQuery {@link URL#getQuery()}
	 * @return Point that contains size values
	 */
	private static Point getCustomSizeFromQuery(String urlQuery) {
		final String sizeSeq = "size="; //$NON-NLS-1$
		if (urlQuery.contains(sizeSeq)) {
			int parsedSize = Integer.parseInt(urlQuery.replace(sizeSeq, "")); //$NON-NLS-1$
			return new Point(parsedSize, parsedSize);
		}

		Point size = new Point(DEFAULT_SIZE.x, DEFAULT_SIZE.y);
		final String widthSeq = "width="; //$NON-NLS-1$
		final String heightSeq = "height="; //$NON-NLS-1$
		for (String parameter : urlQuery.split("&")) { //$NON-NLS-1$
			if (parameter.contains(widthSeq)) {
				size.x = Integer.parseInt(parameter.replace(widthSeq, "")); //$NON-NLS-1$
			} else if (parameter.contains(heightSeq)) {
				size.y = Integer.parseInt(parameter.replace(heightSeq, "")); //$NON-NLS-1$
			}
		}
		return size;
	}

	// the method will be removed once we will have all SVG assets
	// old SVG resources have suffix _32
	private static String getOldSvgUrl(String svgUrlSpec) {
		try {
			int dotIndex = svgUrlSpec.lastIndexOf(SVG_EXT);
			if (dotIndex != -1) {
				String oldSvgName = svgUrlSpec.substring(0, dotIndex) + "_32.svg"; //$NON-NLS-1$
				new URL(oldSvgName).openConnection().connect();

				return oldSvgName;
			}
		} catch (IOException e1) {
		}
		return null;
	}
}

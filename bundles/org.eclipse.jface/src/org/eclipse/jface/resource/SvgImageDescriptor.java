package org.eclipse.jface.resource;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.logging.Logger;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.internal.image.SVGFileFormat;
import org.eclipse.swt.internal.image.SVGHelper;

/**
 * @since 3.4
 *
 */
public class SvgImageDescriptor extends ImageDescriptor {

    private URL url;
	private int renderWidth;
	private int renderHeight;

	/** Default size of image descriptor */
	public static final Point DEFAULT_SIZE = new Point(16, 16);

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
			Logger.getLogger(SVGFileFormat.class.getName()).severe("SVG EXCEPTION: " + url.getFile() + " " + e); //$NON-NLS-1$ //$NON-NLS-2$
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

}

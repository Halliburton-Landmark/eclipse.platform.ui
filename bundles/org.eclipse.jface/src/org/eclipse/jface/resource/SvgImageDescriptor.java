package org.eclipse.jface.resource;

import java.net.URL;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.internal.image.SVGFileFormat;

/**
 * @since 3.4
 *
 */
public class SvgImageDescriptor extends ImageDescriptor {

    private URL url;
	private int renderWidth;
	private int renderHeight;

	/**
	 * @param url
	 * @param renderSize
	 */
    public SvgImageDescriptor(URL url, int renderSize) {
		this(url, renderSize, renderSize);
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
		return SVGFileFormat.loadImageData(url, renderWidth * scale, renderHeight * scale);
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

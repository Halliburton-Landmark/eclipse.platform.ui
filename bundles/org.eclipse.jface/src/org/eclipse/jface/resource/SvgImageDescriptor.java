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
    private int renderSize;

	/**
	 * @param url
	 * @param renderSize
	 */
    public SvgImageDescriptor(URL url, int renderSize) {
        this.url = url;
        this.renderSize = renderSize;
    }

    @Override
    public ImageData getImageData(int zoom) {
        return SVGFileFormat.loadImageData(url, renderSize * zoom / 100);
    }

    @Override
    public Image createImage(boolean returnMissingImageOnError, Device device) {
		return new Image(device, (ImageDataProvider) this::getImageData);
    }

}

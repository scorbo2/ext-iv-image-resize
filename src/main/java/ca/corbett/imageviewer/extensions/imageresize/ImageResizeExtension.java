package ca.corbett.imageviewer.extensions.imageresize;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.ReservedKeyStrokeWorkaround;

import java.util.ArrayList;
import java.util.List;

/**
 * An ImageViewer extension that provides a dialog that helps you to resize either a single
 * image, or a directory of images, based on given parameters. Useful for scaling down
 * overly large images easily.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class ImageResizeExtension extends ImageViewerExtension {

    private static final String keystrokeProp = AppConfig.KEYSTROKE_PREFIX + "Image Resize.keystroke";
    private final AppExtensionInfo extInfo;

    public ImageResizeExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(),"/ca/corbett/imageviewer/extensions/imageresize/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("ImageResizeExtension: can't parse extInfo.json!");
        }
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    public void loadJarResources() {
    }

    @Override
    protected List<AbstractProperty> createConfigProperties() {
        List<AbstractProperty> props = new ArrayList<>();

        props.add(new KeyStrokeProperty(keystrokeProp,
                                        "Resize image:",
                                        KeyStrokeManager.parseKeyStroke("Ctrl+S"),
                                        ImageResizeAction.getInstance())
                      .setAllowBlank(true)
                      .addFormFieldGenerationListener(new ReservedKeyStrokeWorkaround()));

        return props;
    }

    @Override
    public List<EnhancedAction> getMenuActions(String topLevelMenu, MainWindow.BrowseMode browseMode) {
        // We don't allow image resize in image set mode, because an image set
        // can contain images from across multiple directories, which breaks
        // our recursion options.
        if (browseMode == MainWindow.BrowseMode.IMAGE_SET) {
            return null;
        }

        if ("Edit".equals(topLevelMenu)) {
            return List.of(ImageResizeAction.getInstance());
        }
        return null;
    }

    @Override
    public List<EnhancedAction> getPopupMenuActions(MainWindow.BrowseMode browseMode) {
        // We don't allow image resize in image set mode, because an image set
        // can contain images from across multiple directories, which breaks
        // our recursion options.
        if (browseMode == MainWindow.BrowseMode.IMAGE_SET) {
            return null;
        }

        return List.of(ImageResizeAction.getInstance());
    }
}

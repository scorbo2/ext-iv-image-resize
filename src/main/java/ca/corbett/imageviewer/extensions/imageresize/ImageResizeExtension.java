package ca.corbett.imageviewer.extensions.imageresize;

import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * An ImageViewer extension that provides a dialog that helps you to resize either a single
 * image, or a directory of images, based on given parameters. Useful for scaling down
 * overly large images easily.
 *
 * @author scorbo2
 */
public class ImageResizeExtension extends ImageViewerExtension {

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
        return null;
    }

    @Override
    public List<JMenuItem> getMenuItems(String topLevelMenu, MainWindow.BrowseMode browseMode) {
        if (browseMode == MainWindow.BrowseMode.IMAGE_SET) {
            return null; // We COULD allow resizing from within an image set...
        }

        if ("Edit".equals(topLevelMenu)) {
            List<JMenuItem> list = new ArrayList<>();
            list.add(buildMenuItem());
            return list;
        }
        return null;
    }

    @Override
    public List<JMenuItem> getPopupMenuItems(MainWindow.BrowseMode browseMode) {
        if (browseMode == MainWindow.BrowseMode.IMAGE_SET) {
            return null; // We COULD allow resizing from within an image set...
        }

        List<JMenuItem> list = new ArrayList<>();
        list.add(buildMenuItem());
        return list;
    }

    private JMenuItem buildMenuItem() {
        JMenuItem item = new JMenuItem(new ImageResizeAction());
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        return item;
    }

}

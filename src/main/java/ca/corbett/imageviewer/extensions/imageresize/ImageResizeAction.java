package ca.corbett.imageviewer.extensions.imageresize;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Launches the ImageResizeDialog if an image is currently selected in MainWindow.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class ImageResizeAction extends EnhancedAction {

    private static ImageResizeAction instance;
    private static final String NAME = "Resize image...";

    private ImageResizeAction() {
        super(NAME);
    }

    public static ImageResizeAction getInstance() {
        if (instance == null) {
            instance = new ImageResizeAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (MainWindow.getInstance().getBrowseMode() == MainWindow.BrowseMode.IMAGE_SET) {
            MainWindow.getInstance().showMessageDialog(NAME,
                                                       "Resize operation is only supported for file system view.");
            return;
        }
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog(NAME, "Nothing selected.");
            return;
        }

        // Ensure correct file format:
        File file = currentImage.getImageFile();
        if (!ImageResizeExtension.fileExtensionIsSupported(file)) {
            MainWindow.getInstance().showMessageDialog(NAME,
                                                       "Image resizing can currently only be performed on jpeg or png images.");
            return;
        }

        new ImageResizeDialog(file).setVisible(true);
    }
}

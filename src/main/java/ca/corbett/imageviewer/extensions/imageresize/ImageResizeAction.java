package ca.corbett.imageviewer.extensions.imageresize;

import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * Launches the ImageResizeDialog if an image is currently selected in MainWindow.
 *
 * @author scorbo2
 */
public class ImageResizeAction extends AbstractAction {

    public ImageResizeAction() {
        super("Resize image...");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        if (currentImage.isEmpty()) {
            MainWindow.getInstance().showMessageDialog("Resize image", "Nothing selected.");
            return;
        }

        // Ensure correct file format:
        File file = currentImage.getImageFile();
        if (!file.getName().toLowerCase().endsWith("jpg")
                && !file.getName().toLowerCase().endsWith("jpeg")
                && !file.getName().toLowerCase().endsWith("png")) {
            MainWindow.getInstance().showMessageDialog("Resize image",
                                                       "Image resizing can currently only be performed on jpeg or png images.");
            return;
        }

        new ImageResizeDialog(file).setVisible(true);

    }

}

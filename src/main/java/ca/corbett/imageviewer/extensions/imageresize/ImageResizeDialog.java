package ca.corbett.imageviewer.extensions.imageresize;

import ca.corbett.extras.MessageUtil;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.progress.MultiProgressDialog;
import ca.corbett.extras.progress.SimpleProgressAdapter;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.Margins;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.NumberField;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides options for resizing (scaling) a given image, and optionally for applying a
 * bulk resize operation to all images in the given directory.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since ImageViewer 1.2
 */
public class ImageResizeDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(ImageResizeDialog.class.getName());

    private final int MAX_DIMENSION = 9999; // arbitrary

    private final KeyStrokeManager keyStrokeManager;
    private MessageUtil messageUtil;
    private final File srcFile;
    private int imgWidth;
    private int imgHeight;

    private ComboField<String> resizeActionChooser;
    private LabelField extraLabel;
    private ComboField<String> triggerChooser;
    private NumberField triggerValueField;
    private ComboField<String> targetChooser;
    private NumberField targetValueField;
    private CheckBoxField forceCheckbox;

    public ImageResizeDialog(File srcFile) {
        super(MainWindow.getInstance(), "Resize image");
        this.srcFile = srcFile;
        setSize(new Dimension(500, 400));
        setMinimumSize(new Dimension(500, 400));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModal(true);
        initComponents();
        loadImageDetails();
        keyStrokeManager = new KeyStrokeManager(this);
        configureKeyStrokes();
    }

    /**
     * Utility method to scale the given image by the given scale factor and return the difference
     * in file size between the original image and the destination image.
     *
     * @param srcFile     The source file containing the image to be scaled.
     * @param destFile    The destination file to write the scaled image (overwritten if exists).
     * @param scaleFactor Less than 1 to scale down, greater than 1 to scale up.
     * @return The difference in bytes between the size of srcFile and the size of destFile.
     * @throws IOException If image loading or saving goes wrong.
     */
    public static long resizeImage(File srcFile, File destFile, float scaleFactor) throws IOException {
        return resizeImage(ImageUtil.loadImage(srcFile), srcFile, destFile, scaleFactor);
    }

    /**
     * Similar to resizeImage(File, File, float) except that here, if you have already loaded the
     * image from srcFile, you can pass it in to avoid the processing expense of loading it
     * again here.
     *
     * @param img         The image that was loaded from srcFile.
     * @param srcFile     The source file containing the image to be scaled.
     * @param destFile    The destination file to write the scaled image (overwritten if exists).
     * @param scaleFactor Less than 1 to scale down, greater than 1 to scale up.
     * @return The difference in bytes between the size of srcFile and the size of destFile.
     * @throws IOException If image loading or saving goes wrong.
     */
    public static long resizeImage(BufferedImage img, File srcFile, File destFile, float scaleFactor)
            throws IOException {
        logger.log(Level.INFO, "ImageResize: resizing {0} by factor {1}",
                   new Object[]{srcFile.getAbsolutePath(), String.format(scaleFactor + "", "%0$.2f")});
        int oldWidth = img.getWidth();
        int oldHeight = img.getHeight();
        int newWidth = (int)(oldWidth * scaleFactor);
        int newHeight = (int)(oldHeight * scaleFactor);
        int imageType = img.getColorModel().hasAlpha()
            ? BufferedImage.TYPE_INT_ARGB
            : BufferedImage.TYPE_INT_RGB;
        BufferedImage outputImage = new BufferedImage(newWidth, newHeight, imageType);
        Graphics2D graphics = outputImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                  RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                                  RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                                  RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(img, 0, 0, newWidth, newHeight, null);
        graphics.dispose();
        if (destFile.exists()) {
            destFile.delete();
        }
        if (isPng(srcFile)) {
            ImageUtil.savePngImage(outputImage, destFile);
        }
        else if (isJpeg(srcFile)) {
            ImageUtil.saveImage(outputImage, destFile);
        }
        else {
            throw new IOException("Unsupported image format; must be png or jpeg image.");
        }
        img.flush();
        outputImage.flush();
        return srcFile.length() - destFile.length();

    }

    private static boolean isPng(File f) {
        return f.getName().toLowerCase().endsWith("png");
    }

    private static boolean isJpeg(File f) {
        return f.getName().toLowerCase().endsWith("jpg")
                || f.getName().toLowerCase().endsWith("jpeg");
    }

    /**
     * Invoked from the OK handler to do a single-image resize and dispose the dialog.
     * If an error occurs, an error message is displayed and the dialog remains open.
     */
    private void saveResize() {
        if (getMessageUtil().askYesNo("Confirm", "Overwrite original image with this resize?") != MessageUtil.YES) {
            return;
        }

        // Sanity check the new value:
        int newValue = targetValueField.getCurrentValue().intValue();
        if (newValue <= 0 || newValue > MAX_DIMENSION) {
            getMessageUtil().info("Image dimensions must be between 1 and " + MAX_DIMENSION);
            return;
        }

        // Figure out scale factor:
        boolean landscape = imgWidth > imgHeight;
        float scaleFactor = switch (targetChooser.getSelectedIndex()) {
            case 0 -> (float)newValue / (float)imgWidth;
            case 1 -> (float)newValue / (float)imgHeight;
            case 2 -> landscape ? (float)newValue / (float)imgWidth : (float)newValue / (float)imgHeight;
            default -> 1f;
        };

        try {
            // Just overwrite in place, don't care about file size savings on single images:
            resizeImage(srcFile, srcFile, scaleFactor);
        }
        catch (IOException ioe) {
            getMessageUtil().error("Error resizing image: " + ioe.getMessage(), ioe);
            return;
        }

        // Reload the main window and then we're done here:
        MainWindow.getInstance().reloadCurrentImage();
        dispose();
    }

    /**
     * Starts a worker thread to resize all images in the given directory (recursively if specified).
     */
    private void bulkResize(boolean recursive) {
        List<File> fileList = FileSystemUtil.findFiles(srcFile.getParentFile(), recursive)
                                            .stream()
                                            .filter(ImageUtil::isImageFile)
                                            .filter(ImageResizeExtension::fileExtensionIsSupported)
                                            .toList();
        String extraPrompt = recursive ? " recursively" : "";

        // Sanity check resize values:
        int triggerValue = triggerValueField.getCurrentValue().intValue();
        int targetValue = targetValueField.getCurrentValue().intValue();
        if (triggerValue <= 0 || targetValue <= 0
                || triggerValue > MAX_DIMENSION || targetValue > MAX_DIMENSION) {
            getMessageUtil().info("Image dimensions must be between 1 and " + MAX_DIMENSION);
            return;
        }

        if (getMessageUtil().askYesNo("Confirm",
                                      "Perform bulk resize on all "
                                          + fileList.size()
                                          + " images in this directory"
                                          + extraPrompt
                                          + "?\nOriginal images will be overwritten with resized versions.")
            != MessageUtil.YES) {
            return;
        }

        ImageResizeThread worker = new ImageResizeThread(fileList, getResizeTrigger(), triggerValue, getResizeTarget(),
                                                         targetValue, forceCheckbox.isChecked());
        MultiProgressDialog progressDialog = new MultiProgressDialog(this, "Resizing images...");
        progressDialog.setInitialShowDelayMS(250); // Don't show for very quick operations.
        worker.addProgressListener(new ThreadProgressListener(this, worker));
        progressDialog.runWorker(worker, true);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(buildControlPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private FormPanel buildControlPanel() {
        FormPanel formPanel = new FormPanel();

        LabelField label = new LabelField("Proportional resize");
        label.setMargins(new Margins(5, 28, 5, 9, 5));
        label.setFont(LabelField.getDefaultFont().deriveFont(Font.BOLD, 14f));
        formPanel.add(label);

        List<String> options = new ArrayList<>();
        options.add("Selected image");
        options.add("All images in this directory");
        options.add("All images recursively");
        resizeActionChooser = new ComboField<>("Resize:", options, 0, false);
        resizeActionChooser.setMargins(new Margins(5, 5, 0, 5, 5));
        resizeActionChooser.addValueChangedListener(f -> resizeActionChooserChanged());
        formPanel.add(resizeActionChooser);

        extraLabel = new LabelField("Note:", "Only jpg and png images will be resized.)");
        extraLabel.setMargins(new Margins(14, 5, 6, 5, 5));
        formPanel.add(extraLabel);

        options = new ArrayList<>();
        options.add("Image width exceeds...");
        options.add("Image height exceeds...");
        options.add("Either width or height exceeds...");
        triggerChooser = new ComboField<>("Resize if:", options, 2, false);
        triggerChooser.setMargins(new Margins(5, 12, 0, 5, 5));
        triggerChooser.setVisible(false);
        formPanel.add(triggerChooser);

        triggerValueField = new NumberField("Value: ", 1, 1, MAX_DIMENSION, 100);
        triggerValueField.setMargins(new Margins(5, 5, 5, 5, 5));
        triggerValueField.setVisible(false);
        triggerValueField.getFieldComponent().setPreferredSize(new Dimension(100,28));
        formPanel.add(triggerValueField);

        forceCheckbox = new CheckBoxField("Do resize even if the file grows", false);
        forceCheckbox.setMargins(new Margins(5, 5, 5, 5, 5));
        forceCheckbox.setVisible(false);
        formPanel.add(forceCheckbox);

        options = new ArrayList<>();
        options.add("Target width of...");
        options.add("Target height of...");
        options.add("Largest dimension of...");
        targetChooser = new ComboField<>("Resize to:", options, 2, false);
        targetChooser.setMargins(new Margins(5, 5, 5, 5, 5));
        formPanel.add(targetChooser);

        targetValueField = new NumberField("Value: ", 1, 1, MAX_DIMENSION, 100);
        targetValueField.setMargins(new Margins(5, 5, 5, 5, 5));
        targetValueField.getFieldComponent().setPreferredSize(new Dimension(100,28));
        formPanel.add(targetValueField);

        return formPanel;
    }

    /**
     * Fired when the resize action chooser value changes.
     * We update the UI to suit the user's selection: selected image,
     * all images in this directory, or all images recursively.
     */
    private void resizeActionChooserChanged() {
        boolean isCurrentImage = (resizeActionChooser.getSelectedIndex() == 0);
        triggerChooser.setVisible(!isCurrentImage);
        triggerValueField.setVisible(!isCurrentImage);
        forceCheckbox.setVisible(!isCurrentImage);
        if (isCurrentImage) {
            extraLabel.getFieldLabel().setText("Size:");
            extraLabel.setText(
                imgWidth + "x" + imgHeight + ", " + ((imgWidth > imgHeight) ? "landscape" : "portrait"));
        }
        else {
            extraLabel.getFieldLabel().setText("Note:");
            extraLabel.setText("Only jpeg and png images will be resized.");
        }
    }

    private void okHandler() {
        if (resizeActionChooser.getSelectedIndex() == 0) {
            saveResize();
        }
        else {
            bulkResize(resizeActionChooser.getSelectedIndex() == 2);
        }
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        panel.setLayout(new FlowLayout(FlowLayout.RIGHT));

        JButton button = new JButton("OK");
        button.setPreferredSize(new Dimension(90, 23));
        button.addActionListener(e -> okHandler());
        panel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(90, 23));
        button.addActionListener(e -> dispose());
        panel.add(button);

        return panel;
    }

    private ImageResizeThread.ResizeType getResizeTrigger() {
        return switch (triggerChooser.getSelectedIndex()) {
            case 0 -> ImageResizeThread.ResizeType.Width;
            case 1 -> ImageResizeThread.ResizeType.Height;
            case 2 -> ImageResizeThread.ResizeType.Either;
            default -> null;
        };
    }

    private ImageResizeThread.ResizeType getResizeTarget() {
        return switch (targetChooser.getSelectedIndex()) {
            case 0 -> ImageResizeThread.ResizeType.Width;
            case 1 -> ImageResizeThread.ResizeType.Height;
            case 2 -> ImageResizeThread.ResizeType.Either;
            default -> null;
        };
    }

    private void loadImageDetails() {
        ImageInstance image = MainWindow.getInstance().getSelectedImage();
        imgWidth = image.getImageWidth();
        imgHeight = image.getImageHeight();
        extraLabel.getFieldLabel().setText("Size:");
        extraLabel.setText(imgWidth + "x" + imgHeight + ", " + ((imgWidth > imgHeight) ? "landscape" : "portrait"));
        targetValueField.setCurrentValue(imgWidth);
    }

    /**
     * Set up some convenient keystrokes for the dialog.
     * ESC will close the dialog (like Cancel), ENTER will accept (like OK).
     */
    private void configureKeyStrokes() {
        keyStrokeManager.clear();
        keyStrokeManager.registerHandler("esc", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        keyStrokeManager.registerHandler("enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okHandler();
            }
        });
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, Logger.getLogger(ImageResizeDialog.class.getName()));
        }
        return messageUtil;
    }

    /**
     * Listens to our ImageResizeThread for completion events and reports them appropriately.
     * Note: these callbacks fire on the worker thread, not on the Swing EDT!
     * We need to be careful to switch to the EDT when updating the UI.
     */
    private static class ThreadProgressListener extends SimpleProgressAdapter {
        private final ImageResizeDialog owner;
        private final ImageResizeThread thread;

        public ThreadProgressListener(ImageResizeDialog owner, ImageResizeThread thread) {
            this.owner = owner;
            this.thread = thread;
        }

        @Override
        public void progressComplete() {
            String msg = "The resize operation evaluated "
                + thread.getProcessedCount()
                + " images.\n"
                + thread.getResizedCount()
                + " were resized and "
                + thread.getSkippedCount()
                + " were skipped.\n";
            if (thread.getProblemCount() > 0) {
                msg += thread.getProblemCount() + " problems were encountered (see log file).";
            }

            final String m = msg;
            SwingUtilities.invokeLater(() -> {
                owner.dispose();
                MainWindow.getInstance().showMessageDialog("Resize complete", m);
            });
        }

        @Override
        public void progressCanceled() {
            SwingUtilities.invokeLater(() -> {
                MainWindow.getInstance().showMessageDialog("Resize canceled",
                                                           "The resize operation was canceled while in progress.\n"
                                                               + thread.getResizedCount()
                                                               + " images were resized before the cancellation.");
            });
        }
    }
}

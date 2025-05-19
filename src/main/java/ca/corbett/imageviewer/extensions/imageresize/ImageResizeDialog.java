package ca.corbett.imageviewer.extensions.imageresize;

import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.io.FileSystemUtil;
import ca.corbett.extras.MessageUtil;
import ca.corbett.forms.FormPanel;
import ca.corbett.forms.fields.CheckBoxField;
import ca.corbett.forms.fields.ComboField;
import ca.corbett.forms.fields.LabelField;
import ca.corbett.forms.fields.NumberField;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides options for resizing (scaling) a given image, and optionally for applying a
 * bulk resize operation to all images in the given directory.
 *
 * @author scorbo2
 * @since ImageView 1.2
 */
public class ImageResizeDialog extends JDialog implements KeyEventDispatcher {

    private static final Logger logger = Logger.getLogger(ImageResizeDialog.class.getName());

    private final int MAX_DIMENSION = 9999; // arbitrary

    private MessageUtil messageUtil;
    private final File srcFile;
    private int imgWidth;
    private int imgHeight;

    private ComboField resizeActionChooser;
    private LabelField extraLabel;
    private ComboField triggerChooser;
    private NumberField triggerValueField;
    private ComboField targetChooser;
    private NumberField targetValueField;
    private CheckBoxField forceCheckbox;

    public ImageResizeDialog(File srcFile) {
        super(MainWindow.getInstance(), "Resize image");
        this.srcFile = srcFile;
        setSize(new Dimension(500, 390));
        setMinimumSize(new Dimension(500, 390));
        setResizable(false);
        setLocationRelativeTo(MainWindow.getInstance());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        initComponents();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            loadImageDetails();
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
        }
    }

    @Override
    public void dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
        super.dispose();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!isActive()) {
            return false; // don't capture keystrokes if this dialog isn't showing.
        }

        if (e.getID() == KeyEvent.KEY_RELEASED) {
            switch (e.getKeyCode()) {

                case KeyEvent.VK_ESCAPE:
                    dispose();
                    break;

                case KeyEvent.VK_ENTER:
                    okHandler();
                    break;
            }
        }

        return false;
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
        BufferedImage outputImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
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
            Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("png");
            ImageWriter imageWriter = null;
            if (iter.hasNext()) {
                imageWriter = iter.next();
            }
            if (imageWriter == null) {
                throw new IOException("Unable to find PNG writer on this system.");
            }

            ImageUtil.saveImage(outputImage, destFile, imageWriter, null);
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

    private void saveResize() {
        if (JOptionPane.showConfirmDialog(this, "Overwrite original image with this resize?",
                                          "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        // Sanity check the new value:
        int newValue = targetValueField.getCurrentValue().intValue();
        if (newValue <= 0 || newValue > MAX_DIMENSION) {
            getMessageUtil().info("Image dimensions must be between 1 and " + MAX_DIMENSION);
            return;
        }

        boolean landscape = imgWidth > imgHeight;
        float scaleFactor = 1f;
        switch (targetChooser.getSelectedIndex()) {
            case 0:
                scaleFactor = (float)newValue / (float)imgWidth;
                break;
            case 1:
                scaleFactor = (float)newValue / (float)imgHeight;
                break;
            case 2:
                scaleFactor = landscape ? (float)newValue / (float)imgWidth : (float)newValue / (float)imgHeight;
                break;
        }

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

    private void bulkResize(boolean recursive) {
        List<String> extensions = new ArrayList<>();
        extensions.add("jpg");
        extensions.add("jpeg");
        extensions.add("png");
        List<File> fileList = FileSystemUtil.findFiles(srcFile.getParentFile(), recursive, extensions);
        String extraPrompt = recursive ? " recursively" : "";

        // Sanity check resize values:
        int triggerValue = triggerValueField.getCurrentValue().intValue();
        int targetValue = targetValueField.getCurrentValue().intValue();
        if (triggerValue <= 0 || targetValue <= 0
                || triggerValue > MAX_DIMENSION || targetValue > MAX_DIMENSION) {
            getMessageUtil().info("Image dimensions must be between 1 and " + MAX_DIMENSION);
            return;
        }

        if (JOptionPane.showConfirmDialog(this,
                                          "Perform bulk resize on all " + fileList.size() + " images in this directory" + extraPrompt + "?\nOriginal images will be overwritten with resized versions.",
                                          "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        ImageResizeThread worker = new ImageResizeThread(fileList, getResizeTrigger(), triggerValue, getResizeTarget(),
                                                         targetValue, forceCheckbox.isChecked());
        MainWindow.getInstance().disableDirTree();
        new Thread(worker).start();
        dispose();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(buildControlPanel(), BorderLayout.CENTER);
        add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private FormPanel buildControlPanel() {
        FormPanel formPanel = new FormPanel();

        LabelField label = new LabelField("Proportional resize");
        label.setMargins(28, 5, 9, 5, 5);
        label.setFont(label.getFieldLabelFont().deriveFont(Font.BOLD, 14f));
        formPanel.addFormField(label);

        List<String> options = new ArrayList<>();
        options.add("Selected image");
        options.add("All images in this directory");
        options.add("All images recursively");
        resizeActionChooser = new ComboField("Resize:", options, 0, false);
        resizeActionChooser.setMargins(0, 5, 0, 5, 5);
        resizeActionChooser.addValueChangedAction(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean isCurrentImage = (resizeActionChooser.getSelectedIndex() == 0);
                triggerChooser.setVisible(!isCurrentImage);
                triggerValueField.setVisible(!isCurrentImage);
                forceCheckbox.setVisible(!isCurrentImage);
                if (isCurrentImage) {
                    extraLabel.setFieldLabelText("Size:");
                    extraLabel.setText(
                            imgWidth + "x" + imgHeight + ", " + ((imgWidth > imgHeight) ? "landscape" : "portrait"));
                }
                else {
                    extraLabel.setFieldLabelText("Note:");
                    extraLabel.setText("Only jpeg and png images will be resized.");
                }
            }

        });
        formPanel.addFormField(resizeActionChooser);

        extraLabel = new LabelField("Note:", "Only jpg and png images will be resized.)");
        extraLabel.setMargins(14, 5, 6, 5, 5);
        formPanel.addFormField(extraLabel);

        options = new ArrayList<>();
        options.add("Image width exceeds...");
        options.add("Image height exceeds...");
        options.add("Either width or height exceeds...");
        triggerChooser = new ComboField("Resize if:", options, 2, false);
        triggerChooser.setMargins(12, 5, 0, 5, 5);
        triggerChooser.setVisible(false);
        formPanel.addFormField(triggerChooser);

        triggerValueField = new NumberField("Value: ", 1, 1, MAX_DIMENSION, 100);
        triggerValueField.setMargins(5, 5, 5, 5, 5);
        triggerValueField.setVisible(false);
        triggerValueField.getFieldComponent().setPreferredSize(new Dimension(100,28));
        formPanel.addFormField(triggerValueField);

        forceCheckbox = new CheckBoxField("Do resize even if the file grows", false);
        forceCheckbox.setMargins(5, 5, 5, 5, 5);
        forceCheckbox.setVisible(false);
        formPanel.addFormField(forceCheckbox);

        options = new ArrayList<>();
        options.add("Target width of...");
        options.add("Target height of...");
        options.add("Largest dimension of...");
        targetChooser = new ComboField("Resize to:", options, 2, false);
        targetChooser.setMargins(5, 5, 5, 5, 5);
        formPanel.addFormField(targetChooser);

        targetValueField = new NumberField("Value: ", 1, 1, MAX_DIMENSION, 100);
        targetValueField.setMargins(5, 5, 5, 5, 5);
        targetValueField.getFieldComponent().setPreferredSize(new Dimension(100,28));
        formPanel.addFormField(targetValueField);

        formPanel.render();
        return formPanel;
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
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okHandler();
            }

        });
        panel.add(button);

        button = new JButton("Cancel");
        button.setPreferredSize(new Dimension(90, 23));
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }

        });
        panel.add(button);

        return panel;
    }

    private ImageResizeThread.ResizeType getResizeTrigger() {
        switch (triggerChooser.getSelectedIndex()) {
            case 0:
                return ImageResizeThread.ResizeType.Width;
            case 1:
                return ImageResizeThread.ResizeType.Height;
            case 2:
                return ImageResizeThread.ResizeType.Either;
        }
        return null;
    }

    private ImageResizeThread.ResizeType getResizeTarget() {
        switch (targetChooser.getSelectedIndex()) {
            case 0:
                return ImageResizeThread.ResizeType.Width;
            case 1:
                return ImageResizeThread.ResizeType.Height;
            case 2:
                return ImageResizeThread.ResizeType.Either;
        }
        return null;
    }

    private void loadImageDetails() {
        ImageInstance image = MainWindow.getInstance().getSelectedImage();
        imgWidth = image.getImageWidth();
        imgHeight = image.getImageHeight();
        extraLabel.setFieldLabelText("Size:");
        extraLabel.setText(imgWidth + "x" + imgHeight + ", " + ((imgWidth > imgHeight) ? "landscape" : "portrait"));
        targetValueField.setCurrentValue(imgWidth);
    }

    private MessageUtil getMessageUtil() {
        if (messageUtil == null) {
            messageUtil = new MessageUtil(this, Logger.getLogger(ImageResizeDialog.class.getName()));
        }
        return messageUtil;
    }

}

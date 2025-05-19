package ca.corbett.imageviewer.extensions.imageresize;

import ca.corbett.imageviewer.Version;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.logging.Stopwatch;
import org.apache.commons.io.FileUtils;

import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Absorbed and modified from BulkImageResizer, this thread will go through a given list of files
 * and scale them up or down depending on given criteria. In the case of downscaling, the
 * resize will only happen if the resulting image is smaller on disk than the source image
 * (otherwise what's the point).
 *
 * @author scorbo2
 * @since ImageViewer 1.2
 */
public final class ImageResizeThread implements Runnable {

    public enum ResizeType {
        Width, Height, Either
    }

    private static final Logger logger = Logger.getLogger(ImageResizeThread.class.getName());
    private final List<File> fileList;
    private final ResizeType trigger;
    private final int triggerValue;
    private final ResizeType target;
    private final int targetValue;
    private final boolean force;
    private ProgressMonitor monitor;
    private int resizedCount;
    private int skippedCount;
    private int problemCount;
    private boolean wasCanceled;

    public ImageResizeThread(List<File> fileList, ResizeType trigger, int triggerValue, ResizeType target, int targetValue, boolean force) {
        this.fileList = fileList;
        this.trigger = trigger;
        this.triggerValue = triggerValue;
        this.target = target;
        this.targetValue = targetValue;
        this.force = force;
        initialize();
    }

    public int getProcessedCount() {
        return fileList.size();
    }

    public int getResizedCount() {
        return resizedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public int getProblemCount() {
        return problemCount;
    }

    public boolean wasCanceled() {
        return wasCanceled;
    }

    /**
     * Invoked internally to initialize the worker thread.
     */
    private void initialize() {
        int min = 0;
        int max = 100;
        if (fileList != null && !fileList.isEmpty()) {
            max = fileList.size();
        }
        monitor = new ProgressMonitor(MainWindow.getInstance(),
                                      "Resizing...", "Please wait", min, max);
        monitor.setMillisToDecideToPopup(200);
        monitor.setMillisToPopup(200);
    }

    @Override
    public void run() {
        resizedCount = 0;
        skippedCount = 0;
        problemCount = 0;
        wasCanceled = false;
        int i = 1;
        for (File file : fileList) {
            if (monitor.isCanceled()) {
                wasCanceled = true;
                break;
            }
            try {
                monitor.setNote("Resizing " + file.getName());
                BufferedImage image = ImageUtil.loadImage(file);
                monitor.setProgress(i++);
                int oldWidth = image.getWidth();
                int oldHeight = image.getHeight();
                if (qualifiesForResize(oldWidth, oldHeight)) {
                    float scaleFactor = calculateScaleFactor(oldWidth, oldHeight);
                    File destFile = File.createTempFile(Version.APPLICATION_NAME, ".tmp");
                    Stopwatch.start("imageResize");
                    long bytesSaved = ImageResizeDialog.resizeImage(image, file, destFile, scaleFactor);
                    Stopwatch.stop("imageResize");
                    if (bytesSaved < 0 && !force) {
                        skippedCount++;
                        destFile.delete();
                        logger.log(Level.INFO, "Resizing of {0} skipped due to negative savings.",
                                   new Object[]{file.getAbsolutePath()});
                    }
                    else {
                        resizedCount++;
                        file.delete();
                        FileUtils.moveFile(destFile, file);
                        logger.log(Level.INFO,
                                   "Resizing of {0} completed with savings of {1} in {2}.",
                                   new Object[]{file.getAbsolutePath(),
                                           getSizeDescription(bytesSaved),
                                           Stopwatch.reportFormatted("imageResize")});
                    }
                }
                else {
                    skippedCount++;
                    logger.log(Level.INFO, "Resizing of {0} skipped because image not large enough.",
                               new Object[]{file.getAbsolutePath()});
                }

                image.flush();
            }
            catch (IOException ioe) {
                problemCount++;
                logger.log(Level.SEVERE,
                           "resizeImage: Caught exception while resizing " + file.getAbsolutePath() + ": " + ioe.getMessage(),
                           ioe);
            }
        }
        final ImageResizeThread thisThread = this;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                thisThread.resizeCompleteHandler();
            }

        });

        monitor.close();
    }

    private boolean qualifiesForResize(int oldWidth, int oldHeight) {
        boolean qualifiesForResize = false;
        switch (trigger) {
            case Width:
                qualifiesForResize = oldWidth > triggerValue;
                break;
            case Height:
                qualifiesForResize = oldHeight > triggerValue;
                break;
            case Either:
                qualifiesForResize = (oldWidth > triggerValue) || (oldHeight > triggerValue);
                break;
        }
        return qualifiesForResize;
    }

    private float calculateScaleFactor(int oldWidth, int oldHeight) {
        boolean landscape = oldWidth >= oldHeight;
        float scaleFactor = 1f;
        switch (target) {
            case Width:
                scaleFactor = (float)targetValue / (float)oldWidth;
                break;
            case Height:
                scaleFactor = (float)targetValue / (float)oldHeight;
                break;
            case Either:
                scaleFactor = landscape ? (float)targetValue / (float)oldWidth : (float)targetValue / (float)oldHeight;
                break;
        }
        return scaleFactor;
    }

    private String getSizeDescription(long number) {
        long absNumber = Math.abs(number);
        String result;
        if (absNumber < 1024) {
            result = absNumber + " bytes";
        }
        else if (absNumber < (1024 * 1024)) {
            result = (absNumber / 1024) + "KB";
        }
        else {
            result = (absNumber / 1024 / 1024) + "MB";
        }
        if (number < 0) {
            result = "-" + result;
        }
        return result;
    }

    private void resizeCompleteHandler() {
        MainWindow.getInstance().enableDirTree();
        MainWindow.getInstance().reloadCurrentDirectory();

        if (wasCanceled()) {
            MainWindow.getInstance()
                      .showMessageDialog("Resize canceled", "The resize operation was canceled while in progress.\n"
                              + getResizedCount() + " images were resized before the cancellation.");
            return;
        }

        String msg = "The resize operation evaluated " + getProcessedCount() + " images.\n"
                + getResizedCount() + " were resized and " + getSkippedCount() + " were skipped.\n";
        if (getProblemCount() > 0) {
            msg += getProblemCount() + " problems were encountered (see log file).";
        }

        MainWindow.getInstance().showMessageDialog("Resize complete", msg);

    }

}

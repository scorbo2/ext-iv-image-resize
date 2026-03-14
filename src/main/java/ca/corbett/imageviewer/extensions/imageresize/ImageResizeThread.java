package ca.corbett.imageviewer.extensions.imageresize;

import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.logging.Stopwatch;
import ca.corbett.extras.progress.SimpleProgressWorker;
import ca.corbett.imageviewer.Version;
import org.apache.commons.io.FileUtils;

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
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 * @since ImageViewer 1.2
 */
public final class ImageResizeThread extends SimpleProgressWorker {

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

    @Override
    public void run() {
        resizedCount = 0;
        skippedCount = 0;
        problemCount = 0;
        wasCanceled = false;
        int i = 0;
        try {
            fireProgressBegins(fileList.size());
            for (File file : fileList) {
                if (!fireProgressUpdate(i, "Resizing " + file.getName())) {
                    wasCanceled = true;
                    break;
                }
                try {
                    BufferedImage image = ImageUtil.loadImage(file);
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
                            if (!destFile.delete()) {
                                logger.warning("Unable to delete temp file: " + destFile.getAbsolutePath());
                            }
                            logger.log(Level.INFO, "Resizing of {0} skipped due to negative savings.",
                                       new Object[]{file.getAbsolutePath()});
                        }
                        else {
                            resizedCount++;
                            if (!file.delete()) {
                                logger.warning("Unable to delete original file: " + file.getAbsolutePath());
                            }
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

                i++; // next file
            }
        }
        finally {
            // Ensure completion events are fired, otherwise
            // the progress dialog never closes:
            if (wasCanceled) {
                fireProgressCanceled();
            }
            else {
                fireProgressComplete();
            }
        }
    }

    private boolean qualifiesForResize(int oldWidth, int oldHeight) {
        return switch (trigger) {
            case Width -> oldWidth > triggerValue;
            case Height -> oldHeight > triggerValue;
            case Either -> (oldWidth > triggerValue) || (oldHeight > triggerValue);
        };
    }

    private float calculateScaleFactor(int oldWidth, int oldHeight) {
        boolean landscape = oldWidth >= oldHeight;
        return switch (target) {
            case Width -> (float)targetValue / (float)oldWidth;
            case Height -> (float)targetValue / (float)oldHeight;
            case Either -> landscape ? (float)targetValue / (float)oldWidth : (float)targetValue / (float)oldHeight;
        };
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
}

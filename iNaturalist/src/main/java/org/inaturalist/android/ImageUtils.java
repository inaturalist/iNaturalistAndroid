package org.inaturalist.android;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.util.Log;


import com.schokoladenbrown.Smooth;

import org.apache.sanselan.ImageReadException;
import org.apache.sanselan.ImageWriteException;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.common.IImageMetadata;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.jpeg.exifRewrite.ExifRewriter;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.constants.TagInfo;
import org.apache.sanselan.formats.tiff.constants.TiffConstants;
import org.apache.sanselan.formats.tiff.write.TiffOutputDirectory;
import org.apache.sanselan.formats.tiff.write.TiffOutputField;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Various image utility methods
 */
public class ImageUtils {


    // Radius of the Blur. Supported range 0 < radius <= 25
    private static final float BLUR_RADIUS = 25f;
    private static final String TAG = "ImageUtils";

    public static Bitmap blur(Context context, Bitmap image) {
        if (null == image) return null;

        Bitmap outputBitmap = Bitmap.createBitmap(image);
        final RenderScript renderScript = RenderScript.create(context);
        Allocation tmpIn = Allocation.createFromBitmap(renderScript, image);
        Allocation tmpOut = Allocation.createFromBitmap(renderScript, outputBitmap);

        //Intrinsic Gausian blur filter
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
        theIntrinsic.setRadius(BLUR_RADIUS);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);
        return outputBitmap;
    }

    /**
     * Center-crops a bitmap
     * @param bitmap
     * @return
     */
    public static Bitmap centerCropBitmap(Bitmap bitmap) {
        Bitmap output;

        if (bitmap.getWidth() == bitmap.getHeight()) {
            // No resize needed - return a copy as-is
            return bitmap.copy(bitmap.getConfig() == null ? Bitmap.Config.ARGB_8888 : bitmap.getConfig(), true);

        } else if (bitmap.getWidth() > bitmap.getHeight()) {
            output = Bitmap.createBitmap(
                    bitmap,
                    bitmap.getWidth() / 2 - bitmap.getHeight() / 2,
                    0,
                    bitmap.getHeight(),
                    bitmap.getHeight()
            );
        } else {
            output = Bitmap.createBitmap(
                    bitmap,
                    0,
                    bitmap.getHeight() / 2 - bitmap.getWidth() / 2,
                    bitmap.getWidth(),
                    bitmap.getWidth()
            );
        }
        return output;
    }

    /**
     * Creates a circular bitmap (taken from http://curious-blog.blogspot.com/2014/05/create-circle-bitmap-in-android.html)
     * @param bitmap
     * @return
     */
    public static Bitmap getCircleBitmap(Bitmap bitmap) {
        final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);

        final int color = Color.RED;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, output.getWidth(), output.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawOval(rectF, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        return getRoundedCornerBitmap(bitmap, 6);
    }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, float round) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);
        final float roundPx = round;

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }


    // Get max GL dimensions (width/height)
    // Taken from: http://stackoverflow.com/a/26823209/1233767
    private static void getMaxDimensions() {
        // Safe minimum default size
        final int IMAGE_MAX_BITMAP_DIMENSION = 2048;

        if ((sMaxBitmapHeight > 0) && (sMaxBitmapWidth > 0)) {
            // Already calculated max height/width - no need to recalculate
            return;
        }

        // Get EGL Display
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        // Initialise
        int[] version = new int[2];
        egl.eglInitialize(display, version);

        // Query total number of configurations
        int[] totalConfigurations = new int[1];
        egl.eglGetConfigs(display, null, 0, totalConfigurations);

        // Query actual list configurations
        EGLConfig[] configurationsList = new EGLConfig[totalConfigurations[0]];
        egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations);

        int[] textureWidth = new int[1];
        int[] textureHeight = new int[1];
        int maximumTextureWidth = 0, maximumTextureHeight = 0;

        // Iterate through all the configurations to located the maximum texture size
        for (int i = 0; i < totalConfigurations[0]; i++) {
            egl.eglGetConfigAttrib(display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureWidth);
            egl.eglGetConfigAttrib(display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_HEIGHT, textureHeight);

            // Keep track of the maximum texture size
            if (maximumTextureWidth < textureWidth[0])
                maximumTextureWidth = textureWidth[0];
            if (maximumTextureHeight < textureHeight[0])
                maximumTextureHeight = textureHeight[0];
        }

        // Release
        egl.eglTerminate(display);

        // Return largest texture size found, or default
        sMaxBitmapWidth = Math.max(maximumTextureWidth, IMAGE_MAX_BITMAP_DIMENSION);
        sMaxBitmapHeight = Math.max(maximumTextureHeight, IMAGE_MAX_BITMAP_DIMENSION);
    }

    private static int sMaxBitmapHeight = 0, sMaxBitmapWidth = 0;

    // Scales down the bitmap only the bitmap is larger than GL limits
    public static Bitmap scaleDownBitmapIfNeeded(Context context, Bitmap photo) {
        getMaxDimensions();

        if (photo.getHeight() > sMaxBitmapHeight) {
            return scaleDownBitmap(context, photo, sMaxBitmapHeight, true);
        } else if (photo.getWidth() > sMaxBitmapWidth) {
            return scaleDownBitmap(context, photo, sMaxBitmapWidth, false);
        } else {
            // No problem - return as-is
            return photo;
        }
    }

    public static Bitmap scaleDownBitmap(Context context, Bitmap photo, int newDimension, boolean isHeight) {
        final float densityMultiplier = context.getResources().getDisplayMetrics().density;

        int h;
        int w;
        if (isHeight) {
            h = (int) (newDimension * densityMultiplier);
            w = (int) (h * photo.getWidth() / ((double) photo.getHeight()));
        } else {
            w = (int) (newDimension * densityMultiplier);
            h = (int) (w * photo.getHeight() / ((double) photo.getWidth()));
        }

        photo = Bitmap.createScaledBitmap(photo, w, h, true);

        return photo;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize * 4;
    }

    public static Bitmap decodeSampledBitmapFromUri(ContentResolver contentResolver, Uri uri, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try {
            BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        // This decreases in-memory byte-storage per pixel
        options.inPreferredConfig = Bitmap.Config.ALPHA_8;
        try {
            return BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }


    public static int getImageOrientation(String imgFilePath) {
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(imgFilePath);
            int degrees = exifOrientationToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
            return degrees;
        } catch (Exception e) {
            e.printStackTrace();
            // No orientation
            return 0;
        }
    }

    private static int exifOrientationToDegrees(int orientation) {
        switch (orientation) {
        case ExifInterface.ORIENTATION_ROTATE_90:
            return 90;
        case ExifInterface.ORIENTATION_ROTATE_180:
            return 180;
        case ExifInterface.ORIENTATION_ROTATE_270:
            return -90;
        default:
            return 0;
        }

    }

    public static Bitmap rotateAccordingToOrientation(Bitmap bitmapImage, String filename) {
        int orientation = getImageOrientation(filename);

        if (orientation != 0) {
            // Rotate the image
            Matrix matrix = new Matrix();
            matrix.setRotate((float) orientation, bitmapImage.getWidth() / 2, bitmapImage.getHeight() / 2);
            return Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.getWidth(), bitmapImage.getHeight(), matrix, true);
        } else {
            return bitmapImage;
        }
    }


    /**
     * Resizes an image to max size
     * @param path the path to the image filename (optional)
     * @param photoUri the original Uri of the image
     * @return the resized image - or original image if smaller than 2048x2048
     */
    public static String resizeImage(Context context, String path, Uri photoUri, int maxDimensions) {
        InputStream is = null;
        BitmapFactory.Options options = new BitmapFactory.Options();

        try {
            if (path == null) {
                is = context.getContentResolver().openInputStream(photoUri);
            } else {
                is = new FileInputStream(path);
            }

            // Just read the input image dimensions
            options.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeStream(is,null,options);
            int originalHeight = options.outHeight;
            int originalWidth = options.outWidth;
            int newHeight, newWidth;

            // BitmapFactory.decodeStream moves the reading cursor
            is.close();

            if (path == null) {
                is = context.getContentResolver().openInputStream(photoUri);
            } else {
                is = new FileInputStream(path);
            }


            if (Math.max(originalHeight, originalWidth) < maxDimensions) {
                if (path != null) {
                    // Original file is smaller than max - no need to resize
                    return path;
                } else {
                    // Don't resize because image is smaller than max - however, make a local copy of it
                    newHeight = originalHeight;
                    newWidth = originalWidth;
                }
            } else {
                // Resize but make sure we have the same width/height aspect ratio
                if (originalHeight > originalWidth) {
                    newHeight = maxDimensions;
                    newWidth = (int) (maxDimensions * ((float) originalWidth / originalHeight));
                } else {
                    newWidth = maxDimensions;
                    newHeight = (int) (maxDimensions * ((float) originalHeight / originalWidth));
                }
            }

            Log.d(TAG, "Bitmap h:" + options.outHeight + "; w:" + options.outWidth);
            Log.d(TAG, "Resized Bitmap h:" + newHeight + "; w:" + newWidth);

            Bitmap resizedBitmap = BitmapFactory.decodeStream(is);

            if ((newHeight != originalHeight) || (newWidth != originalWidth)) {
                if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    // Resize bitmap using Lanczos algorithm (provides smoother/better results than the
                    // built-in Android resize methods)
                    resizedBitmap = Smooth.rescale(resizedBitmap, newWidth, newHeight, Smooth.AlgoParametrized1.LANCZOS, 1.0);
                } else {
                    // The Smooth rescale library has issues with Older Android versions (causes crashes) - use
                    // built-in Android resizing
                    resizedBitmap = Bitmap.createScaledBitmap(resizedBitmap, newWidth, newHeight, true);
                }
            }

            // Save resized image
            File imageFile = new File(context.getFilesDir(), UUID.randomUUID().toString() + ".jpeg");
            OutputStream os = new FileOutputStream(imageFile);
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.flush();
            os.close();

            Log.d(TAG, String.format("resizeImage: %s => %s", path, imageFile.getAbsolutePath()));

            resizedBitmap.recycle();

            // BitmapFactory.decodeStream moves the reading cursor
            is.close();

            if (path == null) {
                is = context.getContentResolver().openInputStream(photoUri);
            } else {
                is = new FileInputStream(path);
            }

            // Copy all EXIF data from original image into resized image
            copyExifData(is, new File(imageFile.getAbsolutePath()), null);

            is.close();

            return imageFile.getAbsolutePath();

        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return path;
    }


    // EXIF-copying code taken from: https://bricolsoftconsulting.com/copying-exif-metadata-using-sanselan/
    private static boolean copyExifData(InputStream sourceFileStream, File destFile, List<TagInfo> excludedFields) {
        String tempFileName = destFile.getAbsolutePath() + ".tmp";
        File tempFile = null;
        OutputStream tempStream = null;

        try {
            tempFile = new File (tempFileName);

            TiffOutputSet sourceSet = getSanselanOutputSet(sourceFileStream, TiffConstants.DEFAULT_TIFF_BYTE_ORDER);
            if (sourceSet == null) return false;
            TiffOutputSet destSet = getSanselanOutputSet(destFile, sourceSet.byteOrder);
            if (destSet == null) return false;

            // If the EXIF data endianess of the source and destination files
            // differ then fail. This only happens if the source and
            // destination images were created on different devices. It's
            // technically possible to copy this data by changing the byte
            // order of the data, but handling this case is outside the scope
            // of this implementation
            if (sourceSet.byteOrder != destSet.byteOrder) return false;

            destSet.getOrCreateExifDirectory();

            // Go through the source directories
            List<?> sourceDirectories = sourceSet.getDirectories();
            for (int i=0; i<sourceDirectories.size(); i++) {
                TiffOutputDirectory sourceDirectory = (TiffOutputDirectory)sourceDirectories.get(i);
                TiffOutputDirectory destinationDirectory = getOrCreateExifDirectory(destSet, sourceDirectory);

                if (destinationDirectory == null) continue; // failed to create

                // Loop the fields
                List<?> sourceFields = sourceDirectory.getFields();
                for (int j=0; j<sourceFields.size(); j++) {
                    // Get the source field
                    TiffOutputField sourceField = (TiffOutputField) sourceFields.get(j);

                    // Check exclusion list
                    if (excludedFields != null && excludedFields.contains(sourceField.tagInfo)) {
                        destinationDirectory.removeField(sourceField.tagInfo);
                        continue;
                    }

                    // Remove any existing field
                    destinationDirectory.removeField(sourceField.tagInfo);

                    // Add field
                    destinationDirectory.add(sourceField);
                }
            }

            // Save data to destination
            tempStream = new BufferedOutputStream(new FileOutputStream(tempFile));
            new ExifRewriter().updateExifMetadataLossless(destFile, tempStream, destSet);
            tempStream.close();

            // Replace file
            if (destFile.delete()) {
                tempFile.renameTo(destFile);
            }

            return true;

        } catch (ImageReadException exception) {
            exception.printStackTrace();

        } catch (ImageWriteException exception) {
            exception.printStackTrace();

        } catch (IOException exception) {
            exception.printStackTrace();

        } finally {
            if (tempStream != null) {
                try {
                    tempStream.close();
                } catch (IOException e) {
                }
            }

            if (tempFile != null) {
                if (tempFile.exists()) tempFile.delete();
            }
        }

        return false;
    }

    private static TiffOutputDirectory getOrCreateExifDirectory(TiffOutputSet outputSet, TiffOutputDirectory outputDirectory) {
        TiffOutputDirectory result = outputSet.findDirectory(outputDirectory.type);
        if (result != null)
            return result;
        result = new TiffOutputDirectory(outputDirectory.type);
        try {
            outputSet.addDirectory(result);
        } catch (ImageWriteException e) {
            return null;
        }
        return result;
    }


    private static TiffOutputSet getSanselanOutputSet(InputStream stream, int defaultByteOrder)
            throws IOException, ImageReadException, ImageWriteException {
        TiffImageMetadata exif = null;
        TiffOutputSet outputSet = null;

        IImageMetadata metadata = Sanselan.getMetadata(stream, null);
        if (!(metadata instanceof  JpegImageMetadata)) {
            // Not a JPEG -> No EXIF data
            return null;
        }

        JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
        if (jpegMetadata != null) {
            exif = jpegMetadata.getExif();

            if (exif != null) {
                outputSet = exif.getOutputSet();
            }
        }

        // If JPEG file contains no EXIF metadata, create an empty set
        // of EXIF metadata. Otherwise, use existing EXIF metadata to
        // keep all other existing tags
        if (outputSet == null)
            outputSet = new TiffOutputSet(exif==null?defaultByteOrder:exif.contents.header.byteOrder);

        return outputSet;
    }

    private static TiffOutputSet getSanselanOutputSet(File jpegImageFile, int defaultByteOrder)
            throws IOException, ImageReadException, ImageWriteException {
        TiffImageMetadata exif = null;
        TiffOutputSet outputSet = null;

        IImageMetadata metadata = Sanselan.getMetadata(jpegImageFile);
        JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
        if (jpegMetadata != null) {
            exif = jpegMetadata.getExif();

            if (exif != null) {
                outputSet = exif.getOutputSet();
            }
        }

        // If JPEG file contains no EXIF metadata, create an empty set
        // of EXIF metadata. Otherwise, use existing EXIF metadata to
        // keep all other existing tags
        if (outputSet == null)
            outputSet = new TiffOutputSet(exif == null ? defaultByteOrder : exif.contents.header.byteOrder);

        return outputSet;
    }

}

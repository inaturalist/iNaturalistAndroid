package org.inaturalist.android;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

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
import org.tinylog.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import jp.wasabeef.glide.transformations.BlurTransformation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;



/**
 * Various image utility methods
 */
public class ImageUtils {


    // Radius of the Blur. Supported range 0 < radius <= 25
    private static final int BLUR_RADIUS = 25;
    private static final String TAG = "ImageUtils";

    public static void blur(Context context, Bitmap image, ImageView imageView) {
        if (null == image) return;
        if (context == null) return;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isDestroyed() || activity.isFinishing()) {
                return; // Avoid loading if activity is destroyed or finishing
            }
        }

        Glide.with(context)
                .asBitmap()
                .load(image)
                .apply(RequestOptions.bitmapTransform(new BlurTransformation(BLUR_RADIUS))) // Apply blur transformation
                .into(imageView);
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
            Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);
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
            Logger.tag(TAG).error(e);

            try {
                androidx.exifinterface.media.ExifInterface orgExif = new androidx.exifinterface.media.ExifInterface(imgFilePath);
                return orgExif.getRotationDegrees();
            } catch (IOException ex) {
                Logger.tag(TAG).error(e);
            }

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

        return rotateImage(bitmapImage, orientation);
    }

    public static Bitmap rotateImage(Bitmap bitmapImage, int orientation) {
        if (orientation != 0) {
            // Rotate the image
            Matrix matrix = new Matrix();
            matrix.setRotate((float) orientation, bitmapImage.getWidth() / 2, bitmapImage.getHeight() / 2);
            return Bitmap.createBitmap(bitmapImage, 0, 0, bitmapImage.getWidth(), bitmapImage.getHeight(), matrix, true);
        } else {
            return bitmapImage;
        }
    }



    public static String resizeImage(Context context, String path, Uri photoUri, int maxDimensions, boolean isCameraPhoto) {
        // Don't use Lanczos resizing, since under certain conditions it can cause a native JNI crash which we cannot catch
        return resizeImage(context, path, photoUri, maxDimensions, true, isCameraPhoto);
    }

    private static InputStream readInputStream(Context context, String path, Uri photoUri) throws FileNotFoundException {
        if (photoUri != null) {
            try {
                return context.getContentResolver().openInputStream(photoUri);
            } catch (SecurityException exc) {
                // This could happen when if the app that exposes this URI is still active (not in background).
                // Could happen if it's been a while between importing the photo and actually triggering the import itself (e.g.
                // share from Google Photos app to iNat -> first time asking for media permissions -> taking some time to approve this permission ->
                // Google Photos is in the background for a while during this time)
                Logger.tag(TAG).error(exc);
                return null;
            }
        } else if (path != null) {
            return new FileInputStream(new File(path));
        } else {
            Logger.tag(TAG).error("Both path and URI are null");
            return null;
        }
    }

    /**
     * Resizes an image to max size
     * @param path the path to the image filename (optional)
     * @param photoUri the original Uri of the image
     * @param noLanczos if True, will not use Lanczos to resize image (but rather bilinear resampling)
     * @param isCameraPhoto if True, will not rotate photo orientation according to EXIF (photo taken by iNat camera itself)
     * @return the resized image - or original image if smaller than 2048x2048
     */
    public static String resizeImage(Context context, String path, Uri photoUri, int maxDimensions, boolean noLanczos, boolean isCameraPhoto) {
        InputStream is = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        Logger.tag(TAG).info("resizeImage: " + path + " / " + photoUri + " : " + isCameraPhoto);

        try {
            is = readInputStream(context, path, photoUri);

            if (is == null) {
                return null;
            }

            // Just read the input image dimensions
            options.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            int originalHeight = options.outHeight;
            int originalWidth = options.outWidth;
            int newHeight, newWidth;

            // BitmapFactory.decodeStream moves the reading cursor
            is.close();

            int rotationDegrees = 0;


            if (path != null) {
                try {
                    is = readInputStream(context, path, photoUri);
                    if (is != null) {
                        androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(is);
                        rotationDegrees = exif.getRotationDegrees();
                        Logger.tag(TAG).info("resizeImage: degrees: " + rotationDegrees);
                        is.close();
                    } else {
                        Logger.tag(TAG).error("resizeImage: Couldn't read input stream while reading rotation");
                    }
                } catch (Exception exc) {
                    Logger.tag(TAG).error("resizeImage: exception while reading rotation");
                    Logger.tag(TAG).error(exc);
                }
            }

            is = readInputStream(context, path, photoUri);

            if (Math.max(originalHeight, originalWidth) < maxDimensions) {
                // Original file is smaller than max
                // Don't resize because image is smaller than max - however, make a local copy of it
                newHeight = originalHeight;
                newWidth = originalWidth;
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

            Logger.tag(TAG).debug("Bitmap h:" + options.outHeight + "; w:" + options.outWidth);
            Logger.tag(TAG).debug("Resized Bitmap h:" + newHeight + "; w:" + newWidth);

            Bitmap resizedBitmap = BitmapFactory.decodeStream(is);

            if ((resizedBitmap != null) && ((newHeight != originalHeight) || (newWidth != originalWidth))) {
                // The Smooth rescale library has issues with Older Android versions (causes crashes) - use
                // built-in Android resizing + it has not 16kb page alignment, which is mandatory
                // for Android 15+
                resizedBitmap = Bitmap.createScaledBitmap(resizedBitmap, newWidth, newHeight, !noLanczos);
            }

            if (resizedBitmap == null) {
                Logger.tag(TAG).error("resizeImage: resizedBitmap is null");
                return null;
            }

            // Save resized image
            File imageFile = new File(context.getFilesDir(), UUID.randomUUID().toString() + ".jpeg");
            OutputStream os = new FileOutputStream(imageFile);
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
            os.flush();
            os.close();

            Logger.tag(TAG).debug(String.format("resizeImage: %s => %s", path, imageFile.getAbsolutePath()));

            resizedBitmap.recycle();

            // BitmapFactory.decodeStream moves the reading cursor
            is.close();


            is = readInputStream(context, path, photoUri);

            // Copy all EXIF data from original image into resized image
            copyAllExif(context, is, imageFile.getAbsolutePath(), false);

            is.close();

            return imageFile.getAbsolutePath();

        } catch (OutOfMemoryError e) {
            Logger.tag(TAG).error(e);
        } catch (FileNotFoundException e) {
            Logger.tag(TAG).error(e);
        } catch (IOException e) {
            Logger.tag(TAG).error(e);
        } catch (SecurityException e) {
            Logger.tag(TAG).error(e);
        }

        // Failed copying the image
        return null;
    }

    public static void copyAllExif(Context ctx,
                                   InputStream in,
                                   String dstPath,
                                   boolean normalizeOrientation) throws IOException {

        // 1) Build ExifInterface for source (handle Uri)
        androidx.exifinterface.media.ExifInterface src;

        src = new androidx.exifinterface.media.ExifInterface(in); // read-only, fine for source

        // 2) Create a temp copy of the destination image (we’ll write EXIF into the temp)
        File dst = new File(dstPath);
        File parent = dst.getParentFile();
        File tmp = File.createTempFile(".exifcopy_", ".tmp", parent);

        // Make a byte-for-byte copy of the destination image first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.copy(dst.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            // Pre-Android O (API 26) fallback
            FileInputStream inStream = null;
            FileOutputStream outStream = null;
            try {
                inStream = new FileInputStream(dst);
                outStream = new FileOutputStream(tmp);

                byte[] buffer = new byte[8192];
                int length;
                while ((length = inStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, length);
                }
            } finally {
                if (inStream != null) {
                    try {
                        inStream.close();
                    } catch (IOException e) {
                        // Handle close exception
                    }
                }
                if (outStream != null) {
                    try {
                        outStream.close();
                    } catch (IOException e) {
                        // Handle close exception
                    }
                }
            }
        }

        // 3) Open writable ExifInterface on the temp file
        ExifInterface dstExif = new ExifInterface(tmp.getAbsolutePath());

        // 4) Copy every known TAG via reflection (future-proof)
        for (String tag : getAllKnownTags()) {
            String val = src.getAttribute(tag);
            if (val != null) {
                dstExif.setAttribute(tag, val);
            }
        }

        // 5) If the pixel data is already rotated upright, normalize orientation tag
        if (normalizeOrientation) {
            dstExif.setAttribute(ExifInterface.TAG_ORIENTATION,
                    String.valueOf(ExifInterface.ORIENTATION_NORMAL));
        }

        // 6) Persist metadata (single write)
        dstExif.saveAttributes(); // works for JPEG/PNG/WebP

        // 7) Atomic replace
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.move(tmp.toPath(), dst.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                // Fallback if ATOMIC_MOVE not supported
                Files.move(tmp.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            // Pre-Android O (API 26) fallback
            if (!tmp.renameTo(dst)) {
                // If rename fails, copy then delete
                FileInputStream inStream = null;
                FileOutputStream outStream = null;
                try {
                    inStream = new FileInputStream(tmp);
                    outStream = new FileOutputStream(dst);

                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = inStream.read(buffer)) > 0) {
                        outStream.write(buffer, 0, length);
                    }

                    tmp.delete();
                } finally {
                    if (inStream != null) {
                        try {
                            inStream.close();
                        } catch (IOException e) {
                            // Handle close exception
                        }
                    }
                    if (outStream != null) {
                        try {
                            outStream.close();
                        } catch (IOException e) {
                            // Handle close exception
                        }
                    }
                }
            }
        }
    }

    // Collect all public static String fields named TAG_*
    private static Set<String> getAllKnownTags() {
        Set<String> tags = new LinkedHashSet<>();
        for (Field f : ExifInterface.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    && f.getType() == String.class
                    && f.getName().startsWith("TAG_")) {
                try {
                    String tag = (String) f.get(null);
                    if (tag != null && !tag.isEmpty()) tags.add(tag);
                } catch (IllegalAccessException ignored) {}
            }
        }
        return tags;
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
        IImageMetadata metadata = null;

        try {
            metadata = Sanselan.getMetadata(stream, null);
        } catch (Throwable exc) {
            // Couldn't read EXIF metadata
            Logger.tag(TAG).error(exc);
            return null;
        }
        if (!(metadata instanceof JpegImageMetadata)) {
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

    /** Adds a photo to the phone's camera gallery */
    public static String addPhotoToGallery(Context context, String path) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ - cannot directly access DCIM directory
            return addPhotoToGalleryAndroid10(context, path);
        }

        // Copy the file into the camera folder
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis());
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + "/Camera/");
        if (!storageDir.exists()) storageDir.mkdirs();
        String outputPath;
        File image = null;
        try {
            image = File.createTempFile(
                    timeStamp,                   /* prefix */
                    ".jpeg",                     /* suffix */
                    storageDir                   /* directory */
            );
            outputPath = image.getPath();
            FileInputStream inStream = null;
            inStream = new FileInputStream(path);
            FileOutputStream outStream = new FileOutputStream(outputPath);
            FileChannel inChannel = inStream.getChannel();
            FileChannel outChannel = outStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inStream.close();
            outStream.close();
            // Tell the OS to scan the file (will add it to the gallery and create a thumbnail for it)
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(image)));
        } catch (IOException e) {
            Logger.tag(TAG).error("Failed to create gallery photo");
            Logger.tag(TAG).error(e);
            return null;
        } catch (Exception exc) {
            Logger.tag(TAG).error("Failed to write gallery photo");
            if (image != null) {
                // Don't leave around an empty file if we failed to write to it.
                image.delete();
            }
            Logger.tag(TAG).error(exc);
            return null;
        }

        return outputPath;
    }

    /** Adds a photo to the phone's camera gallery (the Android10+ way - without directly accessing DCIM folder -
     * based on https://github.com/yasirkula/UnityNativeGallery/blob/670d9e2b8328f7796dd95d29dd80fadd8935b804/JAR%20Source/NativeGallery.java#L73-L96) */
    public static String addPhotoToGalleryAndroid10(@NonNull final Context context, @NonNull final String path) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis());
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM);
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, true);

        final ContentResolver resolver = context.getContentResolver();

        OutputStream os = null;
        Uri uri = null;

        try {
            final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            uri = resolver.insert(contentUri, contentValues);

            if (uri == null) {
                Logger.tag(TAG).error("addPhotoToGalleryAndroid10: Failed to create new MediaStore record.");
                return null;
            }

            os = resolver.openOutputStream(uri);

            if (os == null) {
                Logger.tag(TAG).error("addPhotoToGalleryAndroid10: Failed to get output stream.");
                resolver.delete(uri, null, null);
                return null;
            }

            InputStream is = resolver.openInputStream(Uri.fromFile(new File(path)));
            FileUtils.copyStreamToStream(is, os);
            is.close();
            os.close();

            contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, false);
            resolver.update(uri, contentValues, null, null);

        } catch (IOException e) {
            Logger.tag(TAG).error("addPhotoToGalleryAndroid10: " + e);
            Logger.tag(TAG).error(e);
            if (uri != null) {
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(uri, null, null);
            }

            return null;
        }

        // Since there is no meaning to path anymore (no direct DCIM access)
        return null;
    }
}

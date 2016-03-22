package org.inaturalist.android;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.Log;


import java.io.FileNotFoundException;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Various image utility methods
 */
public class ImageUtils {

    /**
     * Center-crops a bitmap
     * @param bitmap
     * @return
     */
    public static Bitmap centerCropBitmap(Bitmap bitmap) {
        Bitmap output;

        if (bitmap.getWidth() == bitmap.getHeight()) {
            return bitmap;

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

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            } else {
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }
        }
        return inSampleSize;
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
        try {
            return BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}

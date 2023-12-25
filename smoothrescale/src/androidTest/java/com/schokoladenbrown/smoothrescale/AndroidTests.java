package com.schokoladenbrown.smoothrescale;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.InstrumentationRegistry;

import com.schokoladenbrown.Smooth;

import org.junit.Test;

public class AndroidTests {

    @Test
    public void testRescale() {
        // get Context of the app under test
        Context appContext = InstrumentationRegistry.getTargetContext();
        Bitmap src = BitmapFactory.decodeResource(appContext.getResources(), com.schokoladenbrown.test.R.drawable.black_with_white_border);
        assertNotNull(src);
        // Checks pixel is white
        assertEquals(0xffffffff, src.getPixel(0, 0));
        // Checks pixel is black
        assertEquals(0xff000000, src.getPixel(src.getWidth() / 2, src.getHeight() / 2));

        Bitmap dst = Smooth.rescale(src, 80, 80, Smooth.Algo.BILINEAR);
        assertNotNull(dst);
        assertEquals(80, dst.getWidth());
        assertEquals(80, dst.getHeight());
        assertEquals(0xffffffff, dst.getPixel(0, 0));
        assertEquals(0xff000000, dst.getPixel(dst.getWidth() / 2, dst.getHeight() / 2));
    }
}

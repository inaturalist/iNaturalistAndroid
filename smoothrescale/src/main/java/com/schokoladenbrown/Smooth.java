package com.schokoladenbrown;

import android.graphics.Bitmap;

public class Smooth {

    /* flag values must be in sync with swscale.h */
    public enum Algo {
        FAST_BILINEAR     (1),
        BILINEAR          (2),
        POINT          (0x10),
        AREA           (0x20),
        BICUBLIN       (0x40),
        SINC          (0x100),
        SPLINE        (0x400);

        private final int flag;
        Algo(int flag) { this.flag = flag; }
    }

    public enum AlgoParametrized1 {
        /* swscale.h: For SWS_GAUSS param[0] tunes the exponent and thus cutoff frequency */
        GAUSS          (0x80),
        /* swscale.h: For SWS_LANCZOS param[0] tunes the width of the window function */
        LANCZOS       (0x200);

        private final int flag;
        AlgoParametrized1(int flag) { this.flag = flag; }
    }

    public enum AlgoParametrized2 {
        /* swscale.h: For SWS_BICUBIC param[0] and [1] tune the shape of the basic
                      function, param[0] tunes f(1) and param[1] f´(1) */
        BICUBIC           (4);

        private final int flag;
        AlgoParametrized2(int flag) { this.flag = flag; }
    }


    public static Bitmap rescale(Bitmap src, int dstWidth, int dstHeight, Algo algo) {
        return native_rescale(src, Bitmap.createBitmap(dstWidth, dstHeight, src.getConfig()),
                algo.flag, 0.0, 0.0);
    }

    public static Bitmap rescale(Bitmap src, int dstWidth, int dstHeight,
                                 AlgoParametrized1 algo, double p0) {
        return native_rescale(src, Bitmap.createBitmap(dstWidth, dstHeight, src.getConfig()),
                algo.flag, p0, 0.0);
    }

    public static Bitmap rescale(Bitmap src, int dstWidth, int dstHeight,
                                 AlgoParametrized2 algo, double p0, double p1) {
        return native_rescale(src, Bitmap.createBitmap(dstWidth, dstHeight, src.getConfig()),
                algo.flag, p0, p1);
    }


    static {
        System.loadLibrary("smoothrescale");
    }

    private static native Bitmap native_rescale(Bitmap src, Bitmap dst, int algo, double p0, double p1);
}

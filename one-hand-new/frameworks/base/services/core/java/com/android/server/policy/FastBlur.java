package com.android.server.policy;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.IWindowManager;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManagerGlobal;

public class FastBlur {
    private static String TAG = "FastBlur";
    private static final float SCREENSHOT_RESOLUTION_SCALE = 0.2f;
    public static Bitmap doBlur(Bitmap sentBitmap, int radius, boolean canReuseInBitmap) {

        // Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>

        Bitmap bitmap;
        if (canReuseInBitmap) {
            bitmap = sentBitmap;
        } else {
            bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);
        }

        if (radius < 1) {
            return (null);
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);

        return (bitmap);
    }

    public static Bitmap takeScreenshot(Display display, DisplayMetrics displayMetrics, DisplayInfo info) {
        // We need to orient the screenshot correctly (and the Surface api seems to take screenshots
        // only in the natural orientation of the device :!)
        float[] dims = {displayMetrics.widthPixels, displayMetrics.heightPixels};
        float degrees = getDegreesForRotation(display.getRotation());
        boolean requiresRotation = (degrees > 0);
        Matrix displayMatrix = new Matrix();
        if (requiresRotation) {
            // Get the dimensions of the device in its native orientation
            displayMatrix.reset();
            displayMatrix.preRotate(-degrees);
            displayMatrix.mapPoints(dims);
            dims[0] = Math.abs(dims[0]);
            dims[1] = Math.abs(dims[1]);
        }
        long t1 = System.currentTimeMillis();
        // Take the screenshot
        Bitmap screenBitmap = null;
        // BEGIN leo_liao@asus.com, One-hand control
        int screenshotWidth = (int) (dims[0] * SCREENSHOT_RESOLUTION_SCALE);
        int screenshotHeight = (int) (dims[1] * SCREENSHOT_RESOLUTION_SCALE);
        Rect sourceCrop = null;
        try {
            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            sourceCrop = wm.getDisplayMagnifiedRect(Display.DEFAULT_DISPLAY);
        } catch (Exception e) {
        }
        if (sourceCrop != null) {
            screenBitmap = SurfaceControl.screenshot(
                    sourceCrop, screenshotWidth, screenshotHeight, 0, -1, false, Surface.ROTATION_0);
        } else {
            screenBitmap = SurfaceControl.screenshot(screenshotWidth, screenshotHeight);
        }
        // END leo_liao@asus.com
        if (screenBitmap == null) {
            return null;
        }
        long SurfaceControlScreenshotTime = System.currentTimeMillis() - t1;
        long handleRotationTime = -1;
        long createBitmapTime = -1;
        long translateTime = -1;
        long rotateTime = -1;
        long translateTime2 = -1;
        long drawBitmap = -1;
        long handleRecycle = -1;
        if (requiresRotation) {
            t1 = System.currentTimeMillis();
            Bitmap ss = Bitmap.createBitmap((int)(displayMetrics.widthPixels* SCREENSHOT_RESOLUTION_SCALE),
                    (int)(displayMetrics.heightPixels* SCREENSHOT_RESOLUTION_SCALE), Bitmap.Config.ARGB_8888);
            createBitmapTime = System.currentTimeMillis() - t1;
            Canvas c = new Canvas(ss);
            t1 = System.currentTimeMillis();
            c.translate(ss.getWidth() / 2, ss.getHeight() / 2);
            translateTime = System.currentTimeMillis() - t1;
            t1 = System.currentTimeMillis();
            c.rotate(degrees);
            rotateTime = System.currentTimeMillis() - t1;
            t1 = System.currentTimeMillis();
            c.translate(-dims[0] * SCREENSHOT_RESOLUTION_SCALE / 2, -dims[1] * SCREENSHOT_RESOLUTION_SCALE / 2);
            translateTime2 = System.currentTimeMillis() - t1;
            t1 = System.currentTimeMillis();
            c.drawBitmap(screenBitmap, 0, 0, null);
            drawBitmap = System.currentTimeMillis() - t1;
            t1 = System.currentTimeMillis();
            c.setBitmap(null);
            // Recycle the previous bitmap
            screenBitmap.recycle();
            screenBitmap = ss;
            handleRecycle = System.currentTimeMillis() - t1;
        }

        screenBitmap.setHasAlpha(false);
        screenBitmap.prepareToDraw();
        Log.i(TAG, "screenshot"
                + "("+ screenBitmap.getWidth() + " x " + screenBitmap.getHeight()
                + " SurfaceControlScreenshotTime: " + SurfaceControlScreenshotTime + "ms"
                + " translateTime: " + translateTime + "ms"
                + " rotateTime: " + rotateTime + "ms"
                + " translateTime2: " + translateTime2 + "ms"
                + " drawBitmap: " + drawBitmap + "ms"
                + ", handleRecycle: " + handleRecycle + "ms" + ")");
        return screenBitmap;
    }

    /**
     * @return the current display rotation in degrees
     */
    private static float getDegreesForRotation(int value) {
        switch (value) {
        case Surface.ROTATION_90:
            return 360f - 90f;
        case Surface.ROTATION_180:
            return 360f - 180f;
        case Surface.ROTATION_270:
            return 360f - 270f;
        }
        return 0f;
    }
}

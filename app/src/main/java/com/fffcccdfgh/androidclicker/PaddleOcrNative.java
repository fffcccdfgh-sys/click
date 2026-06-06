package com.fffcccdfgh.androidclicker;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PaddleOcrNative implements AutoCloseable {
    private static final String ASSET_DIR = "paddleocr";
    private static final String DET_MODEL = "ch_ppocr_mobile_v2.0_det_slim_opt.nb";
    private static final String CLS_MODEL = "ch_ppocr_mobile_v2.0_cls_slim_opt.nb";
    private static final String REC_MODEL = "ch_ppocr_mobile_v2.0_rec_slim_opt.nb";
    private static final String CONFIG = "config.txt";
    private static final String LABELS = "ppocr_keys_v1.txt";

    static {
        System.loadLibrary("paddle_ocr_loader");
    }

    private long nativeHandle;

    private PaddleOcrNative(long nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public static PaddleOcrNative create(Context context) throws IOException {
        File modelDir = new File(context.getFilesDir(), "paddleocr");
        copyAssetIfNeeded(context, DET_MODEL, modelDir);
        copyAssetIfNeeded(context, CLS_MODEL, modelDir);
        copyAssetIfNeeded(context, REC_MODEL, modelDir);
        copyAssetIfNeeded(context, CONFIG, modelDir);
        copyAssetIfNeeded(context, LABELS, modelDir);

        long handle = nativeInit(
                context.getApplicationInfo().nativeLibraryDir,
                new File(modelDir, DET_MODEL).getAbsolutePath(),
                new File(modelDir, CLS_MODEL).getAbsolutePath(),
                new File(modelDir, REC_MODEL).getAbsolutePath(),
                new File(modelDir, CONFIG).getAbsolutePath(),
                new File(modelDir, LABELS).getAbsolutePath(),
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                "LITE_POWER_HIGH"
        );
        return new PaddleOcrNative(handle);
    }

    public String recognize(Bitmap bitmap) {
        if (nativeHandle == 0L || bitmap == null) {
            return "";
        }
        Bitmap input = bitmap;
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            input = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        return nativeRecognizeBitmap(nativeHandle, input);
    }

    @Override
    public void close() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle);
            nativeHandle = 0L;
        }
    }

    private static void copyAssetIfNeeded(Context context, String assetName, File targetDir)
            throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to create PaddleOCR dir: " + targetDir);
        }

        File target = new File(targetDir, assetName);
        String assetPath = ASSET_DIR + "/" + assetName;
        long assetSize = assetLength(context, assetPath);
        if (target.exists() && target.length() == assetSize) {
            return;
        }

        try (InputStream input = context.getAssets().open(assetPath);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static long assetLength(Context context, String assetPath) throws IOException {
        try (InputStream input = context.getAssets().open(assetPath)) {
            long total = 0L;
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
            }
            return total;
        }
    }

    private static native long nativeInit(
            String nativeLibraryDir,
            String detModelPath,
            String clsModelPath,
            String recModelPath,
            String configPath,
            String labelPath,
            int cpuThreadNum,
            String cpuPowerMode
    );

    private static native boolean nativeRelease(long handle);

    private static native String nativeRecognizeBitmap(long handle, Bitmap bitmap);
}

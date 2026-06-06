package com.hearttrace.mooddiary.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

/**
 * 图片工具类：圆形裁剪等
 */
public class BitmapUtils {

    /**
     * 将正方形 Bitmap 裁剪为圆形（使用 SRC_IN 模式）：
     * - 透明区域会被挖空，保留圆形内的像素
     * - 配合 FrameLayout 的 oval 描边背景使用，即可形成带白边的圆形头像
     */
    public static Bitmap toCircleBitmap(Bitmap source) {
        if (source == null) return null;

        int size = Math.min(source.getWidth(), source.getHeight());

        // 1. 居中裁剪为正方形
        int x = (source.getWidth() - size) / 2;
        int y = (source.getHeight() - size) / 2;
        Bitmap square = Bitmap.createBitmap(source, x, y, size, size);

        // 2. 绘制圆形
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 先画圆形遮罩
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // 再用 SRC_IN 模式绘制正方形图片（只保留圆形内的像素）
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(square, 0, 0, paint);

        square.recycle();
        return output;
    }
}

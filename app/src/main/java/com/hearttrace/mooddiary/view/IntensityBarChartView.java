package com.hearttrace.mooddiary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hearttrace.mooddiary.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IntensityBarChartView extends View {

    public static class BarPoint {
        public final int day;
        public final float intensity;

        public BarPoint(int day, float intensity) {
            this.day = day;
            this.intensity = intensity;
        }
    }

    private final List<BarPoint> points = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private int barStartColor;
    private int barEndColor;

    public IntensityBarChartView(Context context) {
        super(context);
        init(context);
    }

    public IntensityBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        barStartColor = ContextCompat.getColor(context, R.color.chart_bar_start);
        barEndColor = ContextCompat.getColor(context, R.color.chart_bar_end);
        axisPaint.setColor(0xFFE0E4E8);
        axisPaint.setStrokeWidth(2f);
        labelPaint.setColor(ContextCompat.getColor(context, R.color.stats_subtitle));
        labelPaint.setTextSize(28f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(List<BarPoint> data) {
        points.clear();
        if (data != null) {
            points.addAll(data);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }

        float leftPad = 48f;
        float rightPad = 16f;
        float topPad = 16f;
        float bottomPad = 40f;
        float chartW = w - leftPad - rightPad;
        float chartH = h - topPad - bottomPad;
        float baseY = topPad + chartH;

        canvas.drawLine(leftPad, baseY, w - rightPad, baseY, axisPaint);

        float[] yTicks = {0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f};
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        for (float tick : yTicks) {
            float y = baseY - chartH * tick;
            canvas.drawLine(leftPad - 4, y, leftPad, y, axisPaint);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", tick),
                    leftPad - 8, y + 10, labelPaint);
        }

        if (points.isEmpty()) {
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无数据", w / 2f, h / 2f, labelPaint);
            return;
        }

        int n = points.size();
        float gap = chartW / (n * 2f);
        float barW = gap;

        labelPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < n; i++) {
            BarPoint p = points.get(i);
            float cx = leftPad + gap + i * (barW + gap) * 2;
            float barH = chartH * Math.min(1f, Math.max(0f, p.intensity));
            float left = cx - barW / 2;
            float top = baseY - barH;
            barRect.set(left, top, left + barW, baseY);

            LinearGradient gradient = new LinearGradient(
                    left, baseY, left, top,
                    barStartColor, barEndColor,
                    Shader.TileMode.CLAMP);
            barPaint.setShader(gradient);
            canvas.drawRoundRect(barRect, 8f, 8f, barPaint);
            barPaint.setShader(null);

            canvas.drawText(String.valueOf(p.day), cx, h - 8, labelPaint);
        }
    }
}

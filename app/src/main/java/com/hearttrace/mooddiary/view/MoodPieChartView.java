package com.hearttrace.mooddiary.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MoodPieChartView extends View {

    public static class Slice {
        public final String label;
        public final int count;
        public final int color;

        public Slice(String label, int count, int color) {
            this.label = label;
            this.count = count;
            this.color = color;
        }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public MoodPieChartView(Context context) {
        super(context);
        init();
    }

    public MoodPieChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        arcPaint.setStyle(Paint.Style.FILL);
        holePaint.setStyle(Paint.Style.FILL);
        holePaint.setColor(0xFFFFFFFF);
    }

    public void setData(List<Slice> data) {
        slices.clear();
        if (data != null) {
            for (Slice s : data) {
                if (s.count > 0) {
                    slices.add(s);
                }
            }
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

        float size = Math.min(w, h);
        float pad = size * 0.08f;
        oval.set(pad, pad, w - pad, h - pad);

        int total = 0;
        for (Slice s : slices) {
            total += s.count;
        }

        if (total == 0) {
            arcPaint.setColor(0xFFE8ECF0);
            canvas.drawArc(oval, 0, 360, true, arcPaint);
            float holeR = size * 0.28f;
            canvas.drawCircle(w / 2f, h / 2f, holeR, holePaint);
            return;
        }

        float start = -90f;
        for (Slice s : slices) {
            float sweep = 360f * s.count / total;
            arcPaint.setColor(s.color);
            canvas.drawArc(oval, start, sweep, true, arcPaint);
            start += sweep;
        }

        float holeR = size * 0.28f;
        canvas.drawCircle(w / 2f, h / 2f, holeR, holePaint);
    }
}

package com.spotmydime.ui.onboarding;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DonutChartView extends View {

    public static class Segment {
        public float value;
        public int color;

        public Segment(float value, int color) {
            this.value = value;
            this.color = color;
        }
    }

    private final List<Segment> innerSegments = new ArrayList<>();
    private final List<Segment> outerSegments = new ArrayList<>();
    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float density;

    public DonutChartView(Context context) {
        super(context);
        init();
    }

    public DonutChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DonutChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        density = getResources().getDisplayMetrics().density;

        segmentPaint.setStyle(Paint.Style.FILL);
        segmentPaint.setAntiAlias(true);

        holePaint.setStyle(Paint.Style.FILL);
        holePaint.setColor(0xFFFFFFFF);
        holePaint.setAntiAlias(true);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1f));
        borderPaint.setColor(0xFF1A1A1A);
        borderPaint.setAntiAlias(true);
    }

    public void setData(List<Segment> newSegments) {
        innerSegments.clear();
        outerSegments.clear();
        if (newSegments != null) {
            innerSegments.addAll(newSegments);
        }
        invalidate();
    }

    public void setTwoLayerData(List<Segment> outer, List<Segment> inner) {
        innerSegments.clear();
        outerSegments.clear();
        if (outer != null) outerSegments.addAll(outer);
        if (inner != null) innerSegments.addAll(inner);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = (int) (80 * density + 0.5f);
        int width = resolveSize(defaultSize, widthMeasureSpec);
        int height = resolveSize(defaultSize, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        float outerRadius = Math.min(w, h) / 2f - dp(2);
        float innerRadius = outerRadius * 0.52f;
        float holeRadius = outerRadius * 0.28f;

        float totalInner = 0;
        for (Segment seg : innerSegments) totalInner += seg.value;
        float totalOuter = 0;
        for (Segment seg : outerSegments) totalOuter += seg.value;

        boolean hasInner = totalInner > 0 && !innerSegments.isEmpty();
        boolean hasOuter = totalOuter > 0 && !outerSegments.isEmpty();

        // ── Background ring ──
        segmentPaint.setColor(0xFFF0E8D5);
        canvas.drawCircle(cx, cy, outerRadius, segmentPaint);
        canvas.drawCircle(cx, cy, innerRadius, holePaint);

        // ── Outer ring (income) ──
        if (hasOuter) {
            RectF outerRect = new RectF(cx - outerRadius, cy - outerRadius,
                    cx + outerRadius, cy + outerRadius);
            float currentAngle = -90f;
            for (Segment seg : outerSegments) {
                if (seg.value <= 0) continue;
                float sweep = (seg.value / totalOuter) * 360f;
                segmentPaint.setColor(seg.color);
                canvas.drawArc(outerRect, currentAngle, sweep, true, segmentPaint);
                currentAngle += sweep;
            }
            // Draw inner circle to cut out the center of the outer ring
            canvas.drawCircle(cx, cy, innerRadius, holePaint);
        }

        // ── Inner pie (expense categories) ──
        if (hasInner) {
            RectF innerRect = new RectF(cx - innerRadius, cy - innerRadius,
                    cx + innerRadius, cy + innerRadius);
            float currentAngle = -90f;
            for (Segment seg : innerSegments) {
                if (seg.value <= 0) continue;
                float sweep = (seg.value / totalInner) * 360f;
                segmentPaint.setColor(seg.color);
                canvas.drawArc(innerRect, currentAngle, sweep, true, segmentPaint);
                currentAngle += sweep;
            }
        }

        // ── Center hole ──
        canvas.drawCircle(cx, cy, holeRadius, holePaint);

        // ── Border ──
        canvas.drawCircle(cx, cy, outerRadius, borderPaint);
    }

    private float dp(float value) {
        return value * density + 0.5f;
    }
}

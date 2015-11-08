/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rainbow.blue.espresso.chart;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;

import com.rainbow.blue.espresso.R;
import com.rainbow.blue.espresso.util.ChartUtil;

import java.util.List;

public class InteractiveLineGraphView extends View {
    private static final String TAG = "Interactive";


    /**
     * Initial fling velocity for pan operations, in screen widths (or heights) per second.
     *
     * @see #panLeft()
     * @see #panRight()
     * @see #panUp()
     * @see #panDown()
     */
    private static final float PAN_VELOCITY_FACTOR = 2f;

    /**
     * The scaling factor for a single zoom 'step'.
     *
     * @see #zoomIn()
     * @see #zoomOut()
     */
    private static final float ZOOM_AMOUNT = 0.75f;
    private static final int NumYLabels = 13;
    private static float VIEW_PORT_AXIS_X_MIN = 0;
    private static float VIEW_PORT_AXIS_X_MAX = 4 * 60 * 60;
    // Viewport extremes. See mCurrentViewport for a discussion of the viewport.
    private static float AXIS_X_MIN = 0f;
    private static float AXIS_X_MAX = 5f;
    private static float AXIS_Y_MIN = 0f;
    private static float AXIS_Y_MAX = 13f;
    // Buffers for storing current X and Y stops. See the computeAxisStops method for more details.
    private final AxisStops mXStopsBuffer = new AxisStops();
    private final AxisStops mYStopsBuffer = new AxisStops();
    private final char[] mLabelBuffer = new char[100];
    /**
     * The scale listener, used for handling multi-finger scale gestures.
     */
    private final ScaleGestureDetector.OnScaleGestureListener mScaleGestureListener
            = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
        /**
         * This is the active focal point in terms of the viewport. Could be a local
         * variable but kept here to minimize per-frame allocations.
         */
        private PointF viewportFocus = new PointF();
        private float lastSpanX;
        private float lastSpanY;

        private float lasSpan;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            lastSpanX = ScaleGestureDetectorCompat.getCurrentSpanX(scaleGestureDetector);
            lastSpanY = ScaleGestureDetectorCompat.getCurrentSpanY(scaleGestureDetector);
            lasSpan = scaleGestureDetector.getCurrentSpan();
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
//            float spanX = ScaleGestureDetectorCompat.getCurrentSpanX(scaleGestureDetector);
//            float spanY = ScaleGestureDetectorCompat.getCurrentSpanY(scaleGestureDetector);
//            float newWidth = lastSpanX / spanX * mCurrentViewport.width();
//            float newHeight = lastSpanY / spanY * mCurrentViewport.height();

            float span = scaleGestureDetector.getCurrentSpan();


//            float originHeight = mCurrentViewport.height();
//            float originWidth = mCurrentViewport.width();
//            float focusX = scaleGestureDetector.getFocusX();
//            float focusY = scaleGestureDetector.getFocusY();
//            hitTest(focusX, focusY, viewportFocus);
//
//            mCurrentViewport.set(
//                    isXZoomable ? viewportFocus.x
//                            - newWidth * (focusX - mContentRect.left)
//                            / mContentRect.width() : mCurrentViewport.left, isYZoomable ?
//                            viewportFocus.y
//                                    - newHeight * (mContentRect.bottom - focusY)
//                                    / mContentRect.height() : mCurrentViewport.top,
//                    0,
//                    0);
//            mCurrentViewport.right = mCurrentViewport.left + (isXZoomable ? newWidth : originWidth);
//            mCurrentViewport.bottom = mCurrentViewport.top + (isYZoomable ? newHeight : originHeight);
//            constrainViewport();
//            ViewCompat.postInvalidateOnAnimation(InteractiveLineGraphView.this);
//
//            lastSpanX = spanX;
//            lastSpanY = spanY;
            return true;
        }
    };
    /**
     * The current viewport. This rectangle represents the currently visible chart domain
     * and range. The currently visible chart X values are from this rectangle's left to its right.
     * The currently visible chart Y values are from this rectangle's top to its bottom.
     * <p/>
     * Note that this rectangle's top is actually the smaller Y value, and its bottom is the larger
     * Y value. Since the chart is drawn onscreen in such a way that chart Y values increase
     * towards the top of the screen (decreasing pixel Y positions), this rectangle's "top" is drawn
     * above this rectangle's "bottom" value.
     *
     * @see #mContentRect
     */
    private RectF mCurrentViewport = new RectF(VIEW_PORT_AXIS_X_MIN, AXIS_Y_MIN, VIEW_PORT_AXIS_X_MAX, AXIS_Y_MAX);
    /**
     * The current destination rectangle (in pixel coordinates) into which the chart data should
     * be drawn. Chart labels are drawn outside this area.
     *
     * @see #mCurrentViewport
     */
    private Rect mContentRect = new Rect();
    private boolean isYZoomable = false;
    private boolean isXZoomable = true;
    private boolean isYScrollable = false;
    // Current attribute values and Paints.
    private float mLabelTextSize;
    private int mLabelSeparation;
    private int mLabelTextColor;
    private Paint mLabelTextPaint;
    private int mMaxLabelWidth;
    private int mLabelHeight;
    private float mGridThickness;
    private int mGridColor;
    private Paint mGridPaint;
    private float mAxisThickness;
    private int mAxisColor;
    private Paint mAxisPaint;
    private float mDataThickness;
    private int mDataColor;
    private Paint mSeries1Paint;
    private Paint mSeries2Paint;
    // State objects and values related to gesture tracking.
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetectorCompat mGestureDetector;
    private OverScroller mScroller;
    private Zoomer mZoomer;
    private PointF mZoomFocalPoint = new PointF();
    private RectF mScrollerStartViewport = new RectF(); // Used only for zooms and flings.
    // Edge effect / overscroll tracking objects.
    private EdgeEffectCompat mEdgeEffectTop;
    private EdgeEffectCompat mEdgeEffectBottom;
    private EdgeEffectCompat mEdgeEffectLeft;
    private EdgeEffectCompat mEdgeEffectRight;
    private boolean mEdgeEffectTopActive;
    private boolean mEdgeEffectBottomActive;
    private boolean mEdgeEffectLeftActive;
    private boolean mEdgeEffectRightActive;
    // Buffers used during drawing. These are defined as fields to avoid allocation during
    // draw calls.
    private float[] mAxisXPositionsBuffer = new float[]{};
    private float[] mAxisYPositionsBuffer = new float[]{};
    private float[] mAxisXLinesBuffer = new float[]{};
    private float[] mAxisYLinesBuffer = new float[]{};
    private Point mSurfaceSizeBuffer = new Point();
    /**
     * The gesture listener, used for handling simple gestures such as double touches, scrolls,
     * and flings.
     */
    private final GestureDetector.SimpleOnGestureListener mGestureListener
            = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            releaseEdgeEffects();
            mScrollerStartViewport.set(mCurrentViewport);
            mScroller.forceFinished(true);
            ViewCompat.postInvalidateOnAnimation(InteractiveLineGraphView.this);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
//            mZoomer.forceFinished(true);
//            if (hitTest(e.getX(), e.getY(), mZoomFocalPoint)) {
//                mZoomer.startZoom(ZOOM_AMOUNT);
//            }
//            ViewCompat.postInvalidateOnAnimation(InteractiveLineGraphView.this);
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // Scrolling uses math based on the viewport (as opposed to math using pixels).
            /**
             * Pixel offset is the offset in screen pixels, while viewport offset is the
             * offset within the current viewport. For additional information on surface sizes
             * and pixel offsets, see the docs for {@link computeScrollSurfaceSize()}. For
             * additional information about the viewport, see the comments for
             * {@link mCurrentViewport}.
             */
            float viewportOffsetX = distanceX * mCurrentViewport.width() / mContentRect.width();
            float viewportOffsetY = -distanceY * mCurrentViewport.height() / mContentRect.height();
            computeScrollSurfaceSize(mSurfaceSizeBuffer);
            int scrolledX = (int) (mSurfaceSizeBuffer.x
                    * (mCurrentViewport.left + viewportOffsetX - AXIS_X_MIN)
                    / (AXIS_X_MAX - AXIS_X_MIN));
            int scrolledY = (int) (mSurfaceSizeBuffer.y
                    * (AXIS_Y_MAX - mCurrentViewport.bottom - viewportOffsetY)
                    / (AXIS_Y_MAX - AXIS_Y_MIN));
            boolean canScrollX = mCurrentViewport.left > AXIS_X_MIN
                    || mCurrentViewport.right < AXIS_X_MAX;
            boolean canScrollY = mCurrentViewport.top > AXIS_Y_MIN
                    || mCurrentViewport.bottom < AXIS_Y_MAX;
            setViewportBottomLeft(
                    mCurrentViewport.left + viewportOffsetX,
                    mCurrentViewport.bottom + viewportOffsetY);

            if (canScrollX && scrolledX < 0) {
                mEdgeEffectLeft.onPull(scrolledX / (float) mContentRect.width());
                mEdgeEffectLeftActive = true;
            }
            if (canScrollY && scrolledY < 0) {
                mEdgeEffectTop.onPull(scrolledY / (float) mContentRect.height());
                mEdgeEffectTopActive = true;
            }
            if (canScrollX && scrolledX > mSurfaceSizeBuffer.x - mContentRect.width()) {
                mEdgeEffectRight.onPull((scrolledX - mSurfaceSizeBuffer.x + mContentRect.width())
                        / (float) mContentRect.width());
                mEdgeEffectRightActive = true;
            }
            if (canScrollY && scrolledY > mSurfaceSizeBuffer.y - mContentRect.height()) {
                mEdgeEffectBottom.onPull((scrolledY - mSurfaceSizeBuffer.y + mContentRect.height())
                        / (float) mContentRect.height());
                mEdgeEffectBottomActive = true;
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            fling((int) -velocityX, (int) -velocityY);
            return true;
        }
    };
    private boolean isDrawContainer = false;
    private Series mSeriesLinesBufferSec;
    private Series mSeriesLinesBuffer2Sec;
    private Series mSeriesLinesBufferMin;
    private Series mSeriesLinesBuffer2Min;
    private Mode mode = Mode.Min;
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and objects related to drawing
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////


    public InteractiveLineGraphView(Context context) {
        this(context, null, 0);
    }

    public InteractiveLineGraphView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InteractiveLineGraphView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs, R.styleable.InteractiveLineGraphView, defStyle, defStyle);

        try {
            mLabelTextColor = a.getColor(
                    R.styleable.InteractiveLineGraphView_labelTextColor, mLabelTextColor);
            mLabelTextSize = a.getDimension(
                    R.styleable.InteractiveLineGraphView_labelTextSize, mLabelTextSize);
            mLabelSeparation = a.getDimensionPixelSize(
                    R.styleable.InteractiveLineGraphView_labelSeparation, mLabelSeparation);

            mGridThickness = a.getDimension(
                    R.styleable.InteractiveLineGraphView_gridThickness, mGridThickness);
            mGridColor = a.getColor(
                    R.styleable.InteractiveLineGraphView_gridColor, mGridColor);

            mAxisThickness = a.getDimension(
                    R.styleable.InteractiveLineGraphView_axisThickness, mAxisThickness);
            mAxisColor = a.getColor(
                    R.styleable.InteractiveLineGraphView_axisColor, mAxisColor);

            mDataThickness = a.getDimension(
                    R.styleable.InteractiveLineGraphView_dataThickness, mDataThickness);
            mDataColor = a.getColor(
                    R.styleable.InteractiveLineGraphView_dataColor, mDataColor);
        } finally {
            a.recycle();
        }

        initPaints();
        initData();

        // Sets up interactions
        mScaleGestureDetector = new ScaleGestureDetector(context, mScaleGestureListener);
        mGestureDetector = new GestureDetectorCompat(context, mGestureListener);

        mScroller = new OverScroller(context);
        mZoomer = new Zoomer(context);

        // Sets up edge effects
        mEdgeEffectLeft = new EdgeEffectCompat(context);
        mEdgeEffectTop = new EdgeEffectCompat(context);
        mEdgeEffectRight = new EdgeEffectCompat(context);
        mEdgeEffectBottom = new EdgeEffectCompat(context);
    }

    private static void computeAxisStopsSpecial(float start, float stop, int labels, AxisStops outStops) {
        int interval = (int) Math.abs(stop - start) / labels;
        float val = start;
        if (outStops.stops.length < labels) {
            // Ensure stops contains at least numStops elements.
            outStops.stops = new float[labels];
        }
        for (int i = 0; i < labels; val += interval, ++i) {
            outStops.stops[i] = (float) val;
        }
        outStops.numStops = labels;
        outStops.interval = interval;

    }

    /**
     * Computes the set of axis labels to show given start and stop boundaries and an ideal number
     * of stops between these boundaries.
     *
     * @param start    The minimum extreme (e.g. the left edge) for the axis.
     * @param stop     The maximum extreme (e.g. the right edge) for the axis.
     * @param steps    The ideal number of stops to create. This should be based on available screen
     *                 space; the more space there is, the more stops should be shown.
     * @param outStops The destination {@link AxisStops} object to populate.
     */
    private void computeAxisStops(float start, float stop, int steps, AxisStops outStops) {
//        Log.d(TAG, "pre-->computeAxisStops start:" + start + ",stop:" + stop + ",step:" + steps);
        double range = stop - start;
        if (steps == 0 || range <= 0) {
            outStops.stops = new float[]{};
            outStops.numStops = 0;
            outStops.interval = 0;
//            Log.d(TAG, "end-->computeAxisStops num:" + outStops.numStops + ",interval:" + outStops.interval);
            return;
        }

        double rawInterval = range / steps;
//        double interval = ChartUtil.roundToOneSignificantFigure(rawInterval);
//        double intervalMagnitude = Math.pow(10, (int) Math.log10(interval));
//        int intervalSigDigit = (int) (interval / intervalMagnitude);
//        if (intervalSigDigit > 5) {
//            // Use one order of magnitude higher, to avoid intervals like 0.9 or 90
//            interval = Math.floor(10 * intervalMagnitude);
//        }

        double interval = (mode == Mode.Min ? 60 * 60 : 2 * 60);
        double first = Math.ceil(start / interval) * interval;
        double last = Math.nextUp(Math.floor(stop / interval) * interval);
        double f;
        int i;
        int n = 0;
        for (f = first; f <= last; f += interval) {
            ++n;
        }

        outStops.numStops = n;

        if (outStops.stops.length < n) {
            // Ensure stops contains at least numStops elements.
            outStops.stops = new float[n];
        }

        for (f = first, i = 0; i < n; f += interval, ++i) {
            outStops.stops[i] = (float) f;
        }

        if (interval < 1) {
            outStops.decimals = (int) Math.ceil(-Math.log10(interval));
        } else {
            outStops.decimals = 0;
        }
        outStops.interval = interval;
//        Log.d(TAG, "end-->computeAxisStops num:" + outStops.numStops + ",decimal:" + outStops.decimals + ",interval:" + outStops.interval);
    }

    private void initData() {
        mSeriesLinesBufferSec = new Series();
        mSeriesLinesBuffer2Sec = new Series();
        mSeriesLinesBufferSec.devInitDataSec(0);
        mSeriesLinesBuffer2Sec.devInitDataSec(7);

        mSeriesLinesBufferMin = new Series();
        mSeriesLinesBuffer2Min = new Series();
        mSeriesLinesBufferMin.devInitDataMin(0);
        mSeriesLinesBuffer2Min.devInitDataMin(7);

        // Viewport extremes. See mCurrentViewport for a discussion of the viewport.
        AXIS_X_MIN = 0;
        AXIS_X_MAX = 12 * 60 * 60;

    }

    /**
     * (Re)initializes {@link Paint} objects based on current attribute values.
     */
    private void initPaints() {
        mLabelTextPaint = new Paint();
        mLabelTextPaint.setAntiAlias(true);
        mLabelTextPaint.setTextSize(mLabelTextSize);
        mLabelTextPaint.setColor(mLabelTextColor);
        mLabelHeight = (int) Math.abs(mLabelTextPaint.getFontMetrics().top);
        mMaxLabelWidth = (int) mLabelTextPaint.measureText("0000");

        mGridPaint = new Paint();
        mGridPaint.setStrokeWidth(mGridThickness);
        mGridPaint.setColor(mGridColor);
        mGridPaint.setStyle(Paint.Style.STROKE);

        mAxisPaint = new Paint();
        mAxisPaint.setStrokeWidth(mAxisThickness);
        mAxisPaint.setColor(mAxisColor);
        mAxisPaint.setStyle(Paint.Style.STROKE);

        mSeries1Paint = new Paint();
        mSeries1Paint.setStrokeWidth(mDataThickness);
        mSeries1Paint.setColor(mDataColor);
        mSeries1Paint.setStyle(Paint.Style.FILL_AND_STROKE);
        mSeries1Paint.setAntiAlias(true);

        mSeries2Paint = new Paint();
        mSeries2Paint.setStrokeWidth(mDataThickness);
        mSeries2Paint.setColor(mDataColor);
        mSeries2Paint.setStyle(Paint.Style.FILL_AND_STROKE);
        mSeries2Paint.setAntiAlias(true);


    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.d(TAG, "onSizeChanged" + " w:" + w + ",h:" + h + ",oldw:" + oldw + ",oldh" + oldh);
        mContentRect.set(
                getPaddingLeft() + mMaxLabelWidth + mLabelSeparation,
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom() - mLabelHeight - mLabelSeparation);

        Log.d(TAG, "onSizeChanged 计算后的内容大小 width:" + mContentRect.width() + ",height" + mContentRect.height());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minChartSize = getResources().getDimensionPixelSize(R.dimen.min_chart_size);
        setMeasuredDimension(
                Math.max(getSuggestedMinimumWidth(),
                        resolveSize(minChartSize + getPaddingLeft() + mMaxLabelWidth
                                        + mLabelSeparation + getPaddingRight(),
                                widthMeasureSpec)),
                Math.max(getSuggestedMinimumHeight(),
                        resolveSize(minChartSize + getPaddingTop() + mLabelHeight
                                        + mLabelSeparation + getPaddingBottom(),
                                heightMeasureSpec)));
        Log.d(TAG, "onMeasure  width:" + getMeasuredWidth() + ",height" + getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Draws axes and text labels
        drawAxes(canvas);

        // Clips the next few drawing operations to the content area
        int clipRestoreCount = canvas.save();
        canvas.clipRect(mContentRect);

        drawDataSeriesUnclipped(canvas);
        drawEdgeEffectsUnclipped(canvas);

        // Removes clipping rectangle
        canvas.restoreToCount(clipRestoreCount);

        // Draws chart container
        if (isDrawContainer)
            canvas.drawRect(mContentRect, mAxisPaint);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and objects related to gesture handling
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Draws the chart axes and labels onto the canvas.
     */
    private void drawAxes(Canvas canvas) {
        // Computes axis stops (in terms of numerical value and position on screen)
        int i;

        computeAxisStops(
                mCurrentViewport.left,
                mCurrentViewport.right,
                mContentRect.width() / mMaxLabelWidth / 2,
                mXStopsBuffer);
        computeAxisStopsSpecial(
                mCurrentViewport.top,
                mCurrentViewport.bottom,
                NumYLabels,
                mYStopsBuffer);


        if (mAxisXPositionsBuffer.length < mXStopsBuffer.numStops) {
            mAxisXPositionsBuffer = new float[mXStopsBuffer.numStops];
        }

        if (mAxisYPositionsBuffer.length < mYStopsBuffer.numStops) {
            mAxisYPositionsBuffer = new float[mYStopsBuffer.numStops];
        }
        if (mAxisXLinesBuffer.length < mXStopsBuffer.numStops * 8) {
            mAxisXLinesBuffer = new float[mXStopsBuffer.numStops * 8];
        }
        if (mAxisYLinesBuffer.length < mYStopsBuffer.numStops * 4) {
            mAxisYLinesBuffer = new float[mYStopsBuffer.numStops * 4];
        }

        // Compute positions
        for (i = 0; i < mXStopsBuffer.numStops; i++) {
            mAxisXPositionsBuffer[i] = getDrawX(mXStopsBuffer.stops[i]);
        }
        for (i = 0; i < mYStopsBuffer.numStops; i++) {
            mAxisYPositionsBuffer[i] = getDrawY(mYStopsBuffer.stops[i]);
        }

        // Draws grid lines using drawLines (faster than individual drawLine calls)
        for (i = 0; i < mXStopsBuffer.numStops; i++) {
            mAxisXLinesBuffer[i * 8 + 0] = (float) Math.floor(mAxisXPositionsBuffer[i]);
            mAxisXLinesBuffer[i * 8 + 1] = mContentRect.top;
            mAxisXLinesBuffer[i * 8 + 2] = (float) Math.floor(mAxisXPositionsBuffer[i]);
            mAxisXLinesBuffer[i * 8 + 3] = getDrawY(7);

            mAxisXLinesBuffer[i * 8 + 4] = (float) Math.floor(mAxisXPositionsBuffer[i]);
            mAxisXLinesBuffer[i * 8 + 5] = getDrawY(4);
            mAxisXLinesBuffer[i * 8 + 6] = (float) Math.floor(mAxisXPositionsBuffer[i]);
            mAxisXLinesBuffer[i * 8 + 7] = mContentRect.bottom;
        }
        canvas.drawLines(mAxisXLinesBuffer, 0, mXStopsBuffer.numStops * 8, mGridPaint);

        for (i = 0; i < mYStopsBuffer.numStops; i++) {
            if (mYStopsBuffer.stops[i] == 5 || mYStopsBuffer.stops[i] == 6)
                continue;
            mAxisYLinesBuffer[i * 4 + 0] = mContentRect.left;
            mAxisYLinesBuffer[i * 4 + 1] = (float) Math.floor(mAxisYPositionsBuffer[i]);
            mAxisYLinesBuffer[i * 4 + 2] = mContentRect.right;
            mAxisYLinesBuffer[i * 4 + 3] = (float) Math.floor(mAxisYPositionsBuffer[i]);
        }
        canvas.drawLines(mAxisYLinesBuffer, 0, mYStopsBuffer.numStops * 4, mGridPaint);
        canvas.drawLine(0, getDrawY(0), getMeasuredWidth(), getDrawY(0), mGridPaint);
        canvas.drawLine(mContentRect.left, 0, mContentRect.left, getDrawY(7), mGridPaint);
        canvas.drawLine(mContentRect.left, getDrawY(4), mContentRect.left, getDrawY(0), mGridPaint);
        // Draws X labels
        int labelOffset;
        int labelLength;

        mLabelTextPaint.setTextAlign(Paint.Align.CENTER);
        for (i = 0; i < mXStopsBuffer.numStops; i++) {
            // Do not use String.format in high-performance code such as onDraw code.
            labelLength = ChartUtil.formatFloat(mLabelBuffer, mXStopsBuffer.stops[i], mXStopsBuffer.decimals);
            labelOffset = mLabelBuffer.length - labelLength;
            canvas.drawText(
                    mLabelBuffer, labelOffset, labelLength,
                    mAxisXPositionsBuffer[i],
                    mContentRect.bottom + mLabelHeight + mLabelSeparation,
                    mLabelTextPaint);
        }

        // Draws Y labels
        mLabelTextPaint.setTextAlign(Paint.Align.RIGHT);
        for (i = 0; i < mYStopsBuffer.numStops; i++) {
            if (mYStopsBuffer.stops[i] == 6 || mYStopsBuffer.stops[i] == 5)
                continue;
            // Do not use String.format in high-performance code such as onDraw code.
//            labelLength = ChartUtil.formatFloat(mLabelBuffer, mYStopsBuffer.stops[i], mYStopsBuffer.decimals);
//            labelOffset = mLabelBuffer.length - labelLength;
            if (mYStopsBuffer.stops[i] >= 1 && mYStopsBuffer.stops[i] <= 4) {
                canvas.drawText(
                        (int) mYStopsBuffer.stops[i] * 25 + 25 + "",
                        mContentRect.left - mLabelSeparation,
                        mAxisYPositionsBuffer[i] + mLabelHeight / 2,
                        mLabelTextPaint);
            } else if (mYStopsBuffer.stops[i] >= 8 && mYStopsBuffer.stops[i] <= 12) {
                canvas.drawText(
                        (int) (mYStopsBuffer.stops[i] - 7) * 5 + 75 + "",
                        mContentRect.left - mLabelSeparation,
                        mAxisYPositionsBuffer[i] + mLabelHeight / 2,
                        mLabelTextPaint);
            }
        }
    }

    /**
     * Computes the pixel offset for the given X chart value. This may be outside the view bounds.
     */
    private float getDrawX(float x) {
        return mContentRect.left
                + mContentRect.width()
                * (x - mCurrentViewport.left) / mCurrentViewport.width();
    }

    /**
     * Computes the pixel offset for the given Y chart value. This may be outside the view bounds.
     */
    private float getDrawY(float y) {
        return mContentRect.bottom
                - mContentRect.height()
                * (y - mCurrentViewport.top) / mCurrentViewport.height();
    }

    public String devLimit() {
        if (mXStopsBuffer.stops == null)
            return "";
        float rate = getCurrentViewport().width() / (AXIS_X_MAX - AXIS_X_MIN);
        int last = mXStopsBuffer.stops.length - 1 <= 0 ? 0 : mXStopsBuffer.stops.length - 1;
        return "left:" + mXStopsBuffer.stops[0] + "-- right:" + mXStopsBuffer.stops[last] + ",rate:" + rate + ",decimal:" + mXStopsBuffer.decimals;
    }

    public float getLeftLimit() {
        float res = 0;
        if (mXStopsBuffer.stops == null || mXStopsBuffer.numStops < 2)
            return res;
        float pre = mXStopsBuffer.stops[0];
        float next = mXStopsBuffer.stops[1];
        res = (float) (mXStopsBuffer.stops[0] - (next - pre));
        if (res <= 0)
            return 0;
        return res;
    }

    public float getRightLimit() {
        float res = 0;
        if (mXStopsBuffer.stops == null || mXStopsBuffer.numStops < 2)
            return res;
        int last = mXStopsBuffer.numStops - 1 <= 0 ? 0 : mXStopsBuffer.numStops - 1;
        float pre = mXStopsBuffer.stops[last - 1];
        float next = mXStopsBuffer.stops[last];
        res = (float) (mXStopsBuffer.stops[last] + (next - pre));
        if (res <= 0)
            return 0;
        return res;
    }

    /**
     * Draws the currently visible portion of the data series  to the
     * canvas. This method does not clip its drawing, so users should call {@link Canvas#clipRect
     * before calling this method.
     */
    private void drawDataSeriesUnclipped(Canvas canvas) {
        List<PointF> list;
        List<PointF> list2;
        if (mode == Mode.Min) {
            list = mSeriesLinesBufferMin.fetchBuffer(Math.floor(getLeftLimit()), Math.ceil(getRightLimit()));
            list2 = mSeriesLinesBuffer2Min.fetchBuffer(Math.floor(getLeftLimit()), Math.ceil(getRightLimit()));
        } else {
            list = mSeriesLinesBufferSec.fetchBuffer(Math.floor(getLeftLimit()), Math.ceil(getRightLimit()));
            list2 = mSeriesLinesBuffer2Sec.fetchBuffer(Math.floor(getLeftLimit()), Math.ceil(getRightLimit()));
        }
        if (list.size() != list2.size())
            return;
        for (int i = 0; i < list.size(); i++) {
            drawLine1:
            {
                PointF prePoint = list.get(i);
                PointF nextPoint = list.get((i + 1) >= list.size() ? list.size() - 1 : i + 1);
                canvas.drawLine(getDrawX(prePoint.x), getDrawY(prePoint.y), getDrawX(nextPoint.x), getDrawY(nextPoint.y), mSeries1Paint);
            }
            drawLine2:
            {
                PointF prePoint = list2.get(i);
                PointF nextPoint = list2.get((i + 1) >= list2.size() ? list2.size() - 1 : i + 1);
                canvas.drawLine(getDrawX(prePoint.x), getDrawY(prePoint.y), getDrawX(nextPoint.x), getDrawY(nextPoint.y), mSeries1Paint);
            }
        }
    }

    /**
     * Draws the overscroll "glow" at the four edges of the chart region, if necessary. The edges
     * of the chart region are stored in {@link #mContentRect}.
     *
     * @see EdgeEffectCompat
     */
    private void drawEdgeEffectsUnclipped(Canvas canvas) {
        // The methods below rotate and translate the canvas as needed before drawing the glow,
        // since EdgeEffectCompat always draws a top-glow at 0,0.

        boolean needsInvalidate = false;

        if (!mEdgeEffectTop.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.left, mContentRect.top);
            mEdgeEffectTop.setSize(mContentRect.width(), mContentRect.height());
            if (mEdgeEffectTop.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectBottom.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(2 * mContentRect.left - mContentRect.right, mContentRect.bottom);
            canvas.rotate(180, mContentRect.width(), 0);
            mEdgeEffectBottom.setSize(mContentRect.width(), mContentRect.height());
            if (mEdgeEffectBottom.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectLeft.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.left, mContentRect.bottom);
            canvas.rotate(-90, 0, 0);
            mEdgeEffectLeft.setSize(mContentRect.height(), mContentRect.width());
            if (mEdgeEffectLeft.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (!mEdgeEffectRight.isFinished()) {
            final int restoreCount = canvas.save();
            canvas.translate(mContentRect.right, mContentRect.top);
            canvas.rotate(90, 0, 0);
            mEdgeEffectRight.setSize(mContentRect.height(), mContentRect.width());
            if (mEdgeEffectRight.draw(canvas)) {
                needsInvalidate = true;
            }
            canvas.restoreToCount(restoreCount);
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Finds the chart point (i.e. within the chart's domain and range) represented by the
     * given pixel coordinates, if that pixel is within the chart region described by
     * {@link #mContentRect}. If the point is found, the "dest" argument is set to the point and
     * this function returns true. Otherwise, this function returns false and "dest" is unchanged.
     */
    private boolean hitTest(float x, float y, PointF dest) {
        if (!mContentRect.contains((int) x, (int) y)) {
            return false;
        }

        dest.set(
                isXZoomable ? mCurrentViewport.left
                        + mCurrentViewport.width()
                        * (x - mContentRect.left) / mContentRect.width() : dest.x, isYZoomable ?
                        mCurrentViewport.top
                                + mCurrentViewport.height()
                                * (y - mContentRect.bottom) / -mContentRect.height() : dest.y);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retVal = mScaleGestureDetector.onTouchEvent(event);
        retVal = mGestureDetector.onTouchEvent(event) || retVal;
        return retVal || super.onTouchEvent(event);
    }

    /**
     * Ensures that current viewport is inside the viewport extremes defined by {@link #AXIS_X_MIN},
     * {@link #AXIS_X_MAX}, {@link #AXIS_Y_MIN} and {@link #AXIS_Y_MAX}.
     */
    private void constrainViewport() {
        mCurrentViewport.left = Math.max(AXIS_X_MIN, mCurrentViewport.left);
        mCurrentViewport.top = Math.max(AXIS_Y_MIN, mCurrentViewport.top);
        mCurrentViewport.bottom = Math.max(Math.nextUp(mCurrentViewport.top),
                Math.min(AXIS_Y_MAX, mCurrentViewport.bottom));
        mCurrentViewport.right = Math.max(Math.nextUp(mCurrentViewport.left),
                Math.min(AXIS_X_MAX, mCurrentViewport.right));
    }

    private void releaseEdgeEffects() {
        mEdgeEffectLeftActive
                = mEdgeEffectTopActive
                = mEdgeEffectRightActive
                = mEdgeEffectBottomActive
                = false;
        mEdgeEffectLeft.onRelease();
        mEdgeEffectTop.onRelease();
        mEdgeEffectRight.onRelease();
        mEdgeEffectBottom.onRelease();
    }

    private void fling(int velocityX, int velocityY) {
        releaseEdgeEffects();
        // Flings use math in pixels (as opposed to math based on the viewport).
        computeScrollSurfaceSize(mSurfaceSizeBuffer);
        mScrollerStartViewport.set(mCurrentViewport);
        int startX = (int) (mSurfaceSizeBuffer.x * (mScrollerStartViewport.left - AXIS_X_MIN) / (
                AXIS_X_MAX - AXIS_X_MIN));
        int startY = (int) (mSurfaceSizeBuffer.y * (AXIS_Y_MAX - mScrollerStartViewport.bottom) / (
                AXIS_Y_MAX - AXIS_Y_MIN));
        mScroller.forceFinished(true);
        mScroller.fling(
                startX,
                startY,
                velocityX,
                velocityY,
                0, mSurfaceSizeBuffer.x - mContentRect.width(),
                0, mSurfaceSizeBuffer.y - mContentRect.height(),
                mContentRect.width() / 2,
                mContentRect.height() / 2);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Computes the current scrollable surface size, in pixels. For example, if the entire chart
     * area is visible, this is simply the current size of {@link #mContentRect}. If the chart
     * is zoomed in 200% in both directions, the returned size will be twice as large horizontally
     * and vertically.
     */
    private void computeScrollSurfaceSize(Point out) {
        out.set(
                (int) (mContentRect.width() * (AXIS_X_MAX - AXIS_X_MIN)
                        / mCurrentViewport.width()),
                (int) (mContentRect.height() * (AXIS_Y_MAX - AXIS_Y_MIN)
                        / mCurrentViewport.height()));
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        boolean needsInvalidate = false;

        if (mScroller.computeScrollOffset()) {
            // The scroller isn't finished, meaning a fling or programmatic pan operation is
            // currently active.

            computeScrollSurfaceSize(mSurfaceSizeBuffer);
            int currX = mScroller.getCurrX();
            int currY = mScroller.getCurrY();

            boolean canScrollX = (mCurrentViewport.left > AXIS_X_MIN
                    || mCurrentViewport.right < AXIS_X_MAX);
            boolean canScrollY = (mCurrentViewport.top > AXIS_Y_MIN
                    || mCurrentViewport.bottom < AXIS_Y_MAX);
            if (canScrollX
                    && currX < 0
                    && mEdgeEffectLeft.isFinished()
                    && !mEdgeEffectLeftActive) {
                mEdgeEffectLeft.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectLeftActive = true;
                needsInvalidate = true;
            } else if (canScrollX
                    && currX > (mSurfaceSizeBuffer.x - mContentRect.width())
                    && mEdgeEffectRight.isFinished()
                    && !mEdgeEffectRightActive) {
                mEdgeEffectRight.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectRightActive = true;
                needsInvalidate = true;
            }

            if (canScrollY
                    && currY < 0
                    && mEdgeEffectTop.isFinished()
                    && !mEdgeEffectTopActive) {
                mEdgeEffectTop.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectTopActive = true;
                needsInvalidate = true;
            } else if (canScrollY
                    && currY > (mSurfaceSizeBuffer.y - mContentRect.height())
                    && mEdgeEffectBottom.isFinished()
                    && !mEdgeEffectBottomActive) {
                mEdgeEffectBottom.onAbsorb((int) OverScrollerCompat.getCurrVelocity(mScroller));
                mEdgeEffectBottomActive = true;
                needsInvalidate = true;
            }

            float currXRange = AXIS_X_MIN + (AXIS_X_MAX - AXIS_X_MIN)
                    * currX / mSurfaceSizeBuffer.x;
            float currYRange = AXIS_Y_MAX - (AXIS_Y_MAX - AXIS_Y_MIN)
                    * currY / mSurfaceSizeBuffer.y;
            setViewportBottomLeft(currXRange, currYRange);
        }

        if (mZoomer.computeZoom()) {
            // Performs the zoom since a zoom is in progress (either programmatically or via
            // double-touch).
            float newWidth = (1f - mZoomer.getCurrZoom()) * mScrollerStartViewport.width();
            float newHeight = (1f - mZoomer.getCurrZoom()) * mScrollerStartViewport.height();
            float pointWithinViewportX = (mZoomFocalPoint.x - mScrollerStartViewport.left)
                    / mScrollerStartViewport.width();
            float pointWithinViewportY = (mZoomFocalPoint.y - mScrollerStartViewport.top)
                    / mScrollerStartViewport.height();
            mCurrentViewport.set(
                    isXZoomable ? mZoomFocalPoint.x - newWidth * pointWithinViewportX : mCurrentViewport.left,
                    isYZoomable ? mZoomFocalPoint.y - newHeight * pointWithinViewportY : mCurrentViewport.top,
                    isXZoomable ? mZoomFocalPoint.x + newWidth * (1 - pointWithinViewportX) : mCurrentViewport.right,
                    isYZoomable ? mZoomFocalPoint.y + newHeight * (1 - pointWithinViewportY) : mCurrentViewport.bottom);
            constrainViewport();
            needsInvalidate = true;
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    /**
     * Sets the current viewport (defined by {@link #mCurrentViewport}) to the given
     * X and Y positions. Note that the Y value represents the topmost pixel position, and thus
     * the bottom of the {@link #mCurrentViewport} rectangle. For more details on why top and
     * bottom are flipped, see {@link #mCurrentViewport}.
     */
    private void setViewportBottomLeft(float x, float y) {
        /**
         * Constrains within the scroll range. The scroll range is simply the viewport extremes
         * (AXIS_X_MAX, etc.) minus the viewport size. For example, if the extrema were 0 and 10,
         * and the viewport size was 2, the scroll range would be 0 to 8.
         */

        float curWidth = mCurrentViewport.width();
        float curHeight = mCurrentViewport.height();
        x = Math.max(AXIS_X_MIN, Math.min(x, AXIS_X_MAX - curWidth));
        y = Math.max(AXIS_Y_MIN + curHeight, Math.min(y, AXIS_Y_MAX));

        mCurrentViewport.set(x, y - curHeight, x + curWidth, y);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods for programmatically changing the viewport
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the current viewport (visible extremes for the chart domain and range.)
     */
    public RectF getCurrentViewport() {
        return new RectF(mCurrentViewport);
    }

    /**
     * Sets the chart's current viewport.
     *
     * @see #getCurrentViewport()
     */
    public void setCurrentViewport(RectF viewport) {
        mCurrentViewport = viewport;
        constrainViewport();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly zooms the chart in one step.
     */
    public void zoomIn() {
        if (mode == Mode.Sec)
            return;
        mode = Mode.Sec;
        float center = (mCurrentViewport.left + mCurrentViewport.right) / 2;
        VIEW_PORT_AXIS_X_MIN = 0;
        VIEW_PORT_AXIS_X_MAX = 10 * 60;
        mCurrentViewport.left = (center - 5 * 60) < AXIS_X_MIN ? AXIS_X_MIN : center - 5 * 60;
        mCurrentViewport.right = (center + 5 * 60) > AXIS_X_MAX ? AXIS_X_MAX : center + 5 * 60;
//        mScrollerStartViewport.set(mCurrentViewport);
//        mZoomer.forceFinished(true);
//        mZoomer.startZoom(ZOOM_AMOUNT);
//        mZoomFocalPoint.set(
//                isXZoomable ? (mCurrentViewport.right + mCurrentViewport.left) / 2 : (mCurrentViewport.right + mCurrentViewport.left),
//                isYZoomable ? (mCurrentViewport.bottom + mCurrentViewport.top) / 2 : (mCurrentViewport.bottom + mCurrentViewport.top));
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly zooms the chart out one step.
     */
    public void zoomOut() {
        if (mode == Mode.Min)
            return;
        mode = Mode.Min;
        float center = (mCurrentViewport.left + mCurrentViewport.right) / 2;
        VIEW_PORT_AXIS_X_MIN = 0;
        VIEW_PORT_AXIS_X_MAX = 4 * 60 * 60;
        mCurrentViewport.left = (center - 2 * 60 * 60) < AXIS_X_MIN ? AXIS_X_MIN : center - 2 * 60 * 60;
        mCurrentViewport.right = (center + 2 * 60 * 60) > AXIS_X_MAX ? AXIS_X_MAX : center + 2 * 60 * 60;
//        mScrollerStartViewport.set(mCurrentViewport);
//        mZoomer.forceFinished(true);
//        mZoomer.startZoom(-ZOOM_AMOUNT);
//        mZoomFocalPoint.set(
//                isXZoomable ? (mCurrentViewport.right + mCurrentViewport.left) / 2 : (mCurrentViewport.right + mCurrentViewport.left), isYZoomable ?
//                        (mCurrentViewport.bottom + mCurrentViewport.top) / 2 : (mCurrentViewport.bottom + mCurrentViewport.top));
        ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Smoothly pans the chart left one step.
     */
    public void panLeft() {
        fling((int) (-PAN_VELOCITY_FACTOR * getWidth()), 0);
    }

    /**
     * Smoothly pans the chart right one step.
     */
    public void panRight() {
        fling((int) (PAN_VELOCITY_FACTOR * getWidth()), 0);
    }

    /**
     * Smoothly pans the chart up one step.
     */
    public void panUp() {
        fling(0, (int) (-PAN_VELOCITY_FACTOR * getHeight()));
    }

    /**
     * Smoothly pans the chart down one step.
     */
    public void panDown() {
        fling(0, (int) (PAN_VELOCITY_FACTOR * getHeight()));
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods related to custom attributes
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public float getLabelTextSize() {
        return mLabelTextSize;
    }

    public void setLabelTextSize(float labelTextSize) {
        mLabelTextSize = labelTextSize;
        initPaints();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public int getLabelTextColor() {
        return mLabelTextColor;
    }

    public void setLabelTextColor(int labelTextColor) {
        mLabelTextColor = labelTextColor;
        initPaints();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public float getGridThickness() {
        return mGridThickness;
    }

    public void setGridThickness(float gridThickness) {
        mGridThickness = gridThickness;
        initPaints();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public int getGridColor() {
        return mGridColor;
    }

    public void setGridColor(int gridColor) {
        mGridColor = gridColor;
        initPaints();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public float getAxisThickness() {
        return mAxisThickness;
    }

    public void setAxisThickness(float axisThickness) {
        mAxisThickness = axisThickness;
        initPaints();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public int getAxisColor() {
        return mAxisColor;
    }

    public void setAxisColor(int axisColor) {
        mAxisColor = axisColor;
        initPaints();
        ViewCompat.postInvalidateOnAnimation(this);
    }

    public float getDataThickness() {
        return mDataThickness;
    }

    public void setDataThickness(float dataThickness) {
        mDataThickness = dataThickness;
    }

    public int getDataColor() {
        return mDataColor;
    }

    public void setDataColor(int dataColor) {
        mDataColor = dataColor;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    //     Methods and classes related to view state persistence.
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.viewport = mCurrentViewport;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        mCurrentViewport = ss.viewport;
    }

    /**
     * Persistent state that is saved by InteractiveLineGraphView.
     */
    public static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        });
        private RectF viewport;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);
            viewport = new RectF(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeFloat(viewport.left);
            out.writeFloat(viewport.top);
            out.writeFloat(viewport.right);
            out.writeFloat(viewport.bottom);
        }

        @Override
        public String toString() {
            return "InteractiveLineGraphView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " viewport=" + viewport.toString() + "}";
        }
    }

    /**
     * A simple class representing axis label values.
     *
     * @see #computeAxisStops
     */
    private static class AxisStops {
        float[] stops = new float[]{};
        int numStops;
        int decimals;
        double interval;
    }
}

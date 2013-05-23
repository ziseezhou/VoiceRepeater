/*************************************************************************
FILENAME LyricsView.java

COPYRIGHT (c) 2011

DESCRIPTION
SurfaceView for lyrics


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/09/15   ZISEEZHOU       0x00000000       base code


*************************************************************************/

package com.ziseezhou.lyrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.AvoidXfermode.Mode;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.util.DisplayMetrics;

public class LyricsView extends SurfaceView implements SurfaceHolder.Callback{
    private static final String TAG = "LyricsView";
    private static float FONT_SIZE;
    private static final int FONT_LINE_SPACE = ILyricsConf.LYRICS_FONT_LINE_SPACE;
    private static final int COLOR_BKG = android.R.color.transparent;
    private static final int COLOR_NOR_LINE = Color.GRAY;
    private static final int COLOR_CUR_LINE = Color.RED;
    private static final int UN = ItemData.POS_NOT_UNKNOWN;
    private static final int NB = ItemData.POS_NOT_BEGING;
    private static final int OV = ItemData.POS_OVER_DURATION;

    private int mViewLineNum     = 0;
    private int mHalfViewLineNum = 0;
    private int mDisplayMode = 0; // oneline, mutiline
    private int mViewHeight = 0;
    private int mViewWidth  = 0;
    private int mLineHeight = 0;
    private int mLyricsStartX = 0;
    private DrawThread mThread;
    private Paint.Align mAlign = Align.CENTER;

    // 200ms 5frames x-height
    private static final int FRAME_INTERVAL   = ILyricsConf.LYRICS_ANIM_FRAME_INTERVAL;
    private static final int FRAME_NUM        = ILyricsConf.LYRICS_ANIM_FRAME_NUM;

    // data
    private String[] mData          = null;
    private int      mDataLen       = 0;
    private int      mDataLastIndex = 0;
    private int      mCurIndex      = -1;
    private int      mScrollNum         = 0;
    private int      mScrollFirst       = 0;
    private int      mScrollLast        = 0;
    private boolean  mScrollModeByLine  = true;

    private OnViewChanged mOnViewChangedListener = null;
    public interface OnViewChanged
    {
        void OnChanged();
    }

    public void setSurfaceChangedListener(OnViewChanged l){
        mOnViewChangedListener = l;
    }

    public void setAlign(int i) {
        Log.d(TAG, ">>>>> setAlign="+i);
        
        if (i == ILyricsConf.FONT_ALIGN_LEFT){
            mAlign = Align.LEFT;
            Log.d(TAG, ">>>>> setAlign, LEFT="+Align.LEFT);
        }else if (i == ILyricsConf.FONT_ALIGN_MIDDLE){
            mAlign = Align.CENTER;
            Log.d(TAG, ">>>>> setAlign, CENTER="+Align.CENTER);
        }

        if (mAlign == Align.LEFT){
            mLyricsStartX = 0;
        }else if (mAlign == Align.CENTER){
            mLyricsStartX = (int)(mViewWidth/2);
        }
    }

    /** one line
     *  --------------------------
     *  N
     *  --------------------------
     *  one line
     */

    public LyricsView(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        setZOrderOnTop(true);
        getHolder().addCallback(this);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);

        if (ILyricsConf.LYRICS_FONT_ALIGN == ILyricsConf.FONT_ALIGN_LEFT){
            mAlign = Align.LEFT;
        }else if (ILyricsConf.LYRICS_FONT_ALIGN == ILyricsConf.FONT_ALIGN_MIDDLE){
            mAlign = Align.CENTER;
        }

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        
        FONT_SIZE = dm.scaledDensity* ILyricsConf.LYRICS_FONT_SIZE;
        Log.d(TAG, ">>>>> scale="+dm.scaledDensity+", mAlign="+mAlign);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        mViewHeight         = height;
        mViewWidth          = width;
        mLineHeight         = (int)getFontHeight()+FONT_LINE_SPACE;
        mViewLineNum        = (int)(height/mLineHeight);
        mHalfViewLineNum    = (int)( (height+mLineHeight)/(2*mLineHeight) );

        if (mAlign == Align.LEFT){
            mLyricsStartX = 0;
        }else if (mAlign == Align.CENTER){
            mLyricsStartX = (int)(width/2);
        }

        Log.d(TAG, ">>>> surfaceChanged VH="+mViewHeight+", VW="+mViewWidth+", LH="+mLineHeight);

        if (null != mOnViewChangedListener) {
            mOnViewChangedListener.OnChanged();
        }
    }

    public void surfaceCreated(SurfaceHolder holder)
    {
        Log.d(TAG, ">>>> surfaceCreated");
    }

    public void surfaceDestroyed(SurfaceHolder holder)
    {
        Log.d(TAG, ">>>> surfaceDestroyed");
        if (mThread!=null) mThread.cancel();
        /*boolean retry = true;
        while (retry) {
            try {
                mThread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
        */
    }

    @Override
    public void onDraw(Canvas canvas){
        super.onDraw(canvas);
        drawFinal();
        Log.d(TAG, "onDraw");
    }

    /**
     *              UNKNOWN
     *            /         \
     *           V           V
     *   NOT_BEGIN <-------->NORMAL

     */

    /**
     * @param d     : the lyricsArray
     * @param being : the row_begin
     * @param end   : the row_end
     * @param anim  : perform animation or not
     * @param requireDuration : indicate a duration
     */
    public void draw(String[]d, int begin, int end, boolean anim, long requireDuration){
        //Log.d(TAG, "draw param : d="+d+", datalen="+d.length+", begin="+begin+", end="+end);

        int b    = begin;
        int e    = end;
        boolean isParamError = false;

        mData    = d;
        mDataLen = d.length;
        mDataLastIndex = mDataLen - 1;
        mCurIndex = end;

        isParamError =  isParamError || (null==d) || (d.length<=0);
        isParamError =  isParamError || ( (b<0 || b>mDataLastIndex) && b!=UN && b!=NB);
        isParamError =  isParamError || ( (e<0 || e>mDataLastIndex) && e!=NB);

        if (isParamError){
            Log.w(TAG, "draw param error: d="+d+", datalen="+d.length+", begin="+b+", end="+e);
            clearView(); // clear screen
            return;
        }

        if (e==b || !anim) {
            mScrollNum   = 0;
            mScrollFirst = mScrollLast = b;
            drawFinal(); // Just fresh the view
            return;
        }


        switch (b) {
            case UN: mScrollFirst = -mHalfViewLineNum ; break;
            //case NB: mScrollFirst = 0 ; break; // NB==-1, is a available value
            default: mScrollFirst = b;
        }

        switch (e) {
            case NB: mScrollLast = 0;
            default: mScrollLast = e;
        }

        mScrollNum = mScrollLast - mScrollFirst;
        mScrollModeByLine = Math.abs(mScrollNum) >= FRAME_NUM;

        drawAnim();
    }

    private void drawFinal(){
        if (null != mThread) mThread.cancel();
        drawAnimFrame(FRAME_NUM-1, FRAME_NUM);
    }

    private void drawAnim(){
        if (null != mThread) mThread.cancel();

        mThread = new DrawThread();
        mThread.start();
    }

    private float getFontHeight(){
        Paint p = new Paint();
        p.setTextSize(FONT_SIZE);
        FontMetrics fm = p.getFontMetrics();
        return (float)Math.ceil(fm.descent - fm.top);
    }


    private void drawAnimFrame(int frameIndex, int frameTotal) {
        Canvas canvas = getHolder().lockCanvas(null);
        if (null == canvas) return;


        // =======================
        // FrameParameters:
        // BaseIndex : of lyrics
        // Y : of BaseIndex

        float BaseY;
        int   BaseIndex;

        int factFrameIndex = frameIndex+1;

        // last frame also use ModeByLine
        if (mScrollModeByLine || factFrameIndex==frameTotal) {
            BaseY = mViewHeight/2 ;

            if (factFrameIndex==frameTotal){// last frame
                BaseIndex = mScrollLast;
            } else {
                BaseIndex = mScrollFirst + (int)mScrollNum*factFrameIndex/frameTotal;
            }
        }else {
            BaseY = mViewHeight/2 - mScrollNum*mLineHeight*factFrameIndex/frameTotal;
            BaseIndex = mScrollFirst;
        }

        //Log.d(TAG, "drawAnimFrame index="+frameIndex+", total="+frameTotal);
        //Log.d(TAG, "drawAnimFrame BaseY="+BaseY+", BaseIndex="+BaseIndex+", mScrollFirst="+mScrollFirst+", mScrollNum="+mScrollNum);

        Paint p = new Paint();
        p.setColor(COLOR_NOR_LINE);
        p.setTextSize(FONT_SIZE);
        p.setTextAlign(mAlign);
        p.setFlags(Paint.ANTI_ALIAS_FLAG);

        // Log.v(TAG, ">>>>> drawAnimFrame: mAlign="+mAlign);

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawColor(COLOR_BKG);

        // Up of the BaseIndex
        for (int i=0;;++i){
            int index = BaseIndex - i;
            if (index < 0) break;

            float Y = BaseY - i*mLineHeight;
            if (Y <= -mLineHeight) break;

            if (index == mCurIndex) p.setColor(COLOR_CUR_LINE);
            canvas.drawText(mData[index], mLyricsStartX, Y, p);
            if (index == mCurIndex) p.setColor(COLOR_NOR_LINE);
        }

        // Down of the BaseIndex
        for (int i=1;;++i){
            int index = BaseIndex + i;
            if (index > mDataLastIndex) break;
            if (index < 0) continue;

            float Y = BaseY + i*mLineHeight;
            if (Y >= mViewHeight+mLineHeight) break;

            if (index == mCurIndex) p.setColor(COLOR_CUR_LINE);
            canvas.drawText(mData[index], mLyricsStartX, Y, p);
            if (index == mCurIndex) p.setColor(COLOR_NOR_LINE);
        }

        getHolder().unlockCanvasAndPost(canvas);
    }

    public void clearView(){
        if (mThread != null) mThread.cancel();

        synchronized (getHolder())
        {
            try {
                Canvas canvas = getHolder().lockCanvas(null);
                if (null != canvas) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    getHolder().unlockCanvasAndPost(canvas);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    class DrawThread extends Thread {
        private boolean mCanceled = false;
        private long mLastDrawDuration = 0;
        private long mInterval = FRAME_INTERVAL;

        public void cancel(){
            mCanceled = true;
        }

        public void setInterval(long i){
            mInterval = i;
        }

        public void run(){
            for (int i=0; i<FRAME_NUM && !mCanceled; ++i)
            {
                long startTime, endTime, interval;
                startTime = System.currentTimeMillis();

                try {
                    interval = mInterval-mLastDrawDuration;
                    if (interval>0) Thread.sleep(interval);
                }
                catch (Exception e) {}

                synchronized (LyricsView.this.getHolder()){
                    // long iBegin = System.currentTimeMillis();
                    drawAnimFrame(i, FRAME_NUM);
                    // long iEnd = System.currentTimeMillis();
                    // Log.d(TAG, "frame spent "+(iEnd-iBegin)+" ms");
                }

                endTime = System.currentTimeMillis();
                mLastDrawDuration = endTime - startTime;
            }
        }
    }
}
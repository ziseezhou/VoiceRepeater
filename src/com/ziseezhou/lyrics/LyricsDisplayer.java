/*************************************************************************
FILENAME LyricsDisplayer.java

COPYRIGHT (c) 2011

DESCRIPTION
For display lyrics.
This module is related to specific platform (like Android)


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/08/30   ZISEEZHOU       0x00000000       base code


 *************************************************************************/

package com.ziseezhou.lyrics;

import com.ziseezhou.voicerepeater.IVoiceRepeaterService;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import com.ziseezhou.voicerepeater.R;

/*************************************************************************
 * define displayer interface
 */

interface ILyricsDisplayer
{
    void play();
    void pause();
    void show();
    void hide();
    void stop(); // now, just for release resource
    void setTrackInfo(String path);
    void setLyricsView(LyricsView v);
    void setLyricsAlign(int i);
    void setLyricsStatusView(View v);
    void setMediaPlayingState(boolean playing);
    void setMediaPlaybackService(IVoiceRepeaterService s);
    void quickOneShot();

    int LYRICS_PLAYER_STATE_IDLE = 0;
    int LYRICS_PLAYER_STATE_PLAYING = 1;
    int LYRICS_PLAYER_STATE_PAUSE = 2;
}

/************************************************************************/

public class LyricsDisplayer implements ILyricsDisplayer, ILyricsConf
{
    private static final String TAG = "LyricsDisplayer";

    private static final int MSG_UPDATE_STATUS  = 2;
    private static final int MSG_UPDATE_DISPLAY = 3;
    private static final int MSG_SET_TRACK_INFO = 4;
    private static final int MSG_LOOPER         = 5;
    private static final int MSG_LOOPER_CANCLE  = 6;
    private static final int MSG_QUICK_ONE_SHOT = 7;
    private static final int MSG_STOP           = 8;

    private int mState = LYRICS_PLAYER_STATE_IDLE;
    private int mEngineState = LyricsEngine.STATE_IDLE;
    private boolean mMediaIsPlaying = false;
    private LyricsView mView = null;
    private View mStatusView = null;
    private String[] mLyricsArray = null;
    private boolean mForceUpdateOnce = false;
    private int mTagOffset = 0;
    private int mFontAlign = ILyricsConf.LYRICS_FONT_ALIGN;

    /**
     * NOW: just for independent, engine is private as a member; FUTURE: we can
     * use singleton mode at engine in music. Then, the engine need to be driven
     * only by playbackService, and can register a or some viewPlayer
     * callBack(s), engine will notify player to update view with the latest
     * data.
     */
    private LyricsEngine mLyricsEngine = new LyricsEngine();


    /**

     Displayer use a looper for fresh lyrics, event :
     1. launch the looper:
        setTrackInfo (delay to LyricsEngine.STATE_READY)
        setMediaPlayingState
        play
        show

     2. stop the looper:
        LyricsEngine.STATE_ERROR
        setMediaPlayingState
        pause
        hide

     */


    public void play()
    {
        show();
    }

    public void pause()
    {
        hide();
    }

    public void show()
    {
        lanuchNewLooperMsg();
    }

    public void hide()
    {
        mHandler.sendEmptyMessage(MSG_LOOPER_CANCLE);
    }

    /**
     * stop current lyrics, release all resources
     */
    public void stop()
    {
        mHandler.sendEmptyMessage(MSG_STOP);
    }

    public void setTrackInfo(String path)
    {
        // remove all old timeout messages
        mHandler.removeMessages(MSG_SET_TRACK_INFO);

        Message msg = new Message();
        msg.what = MSG_SET_TRACK_INFO;
        msg.obj = path;
        mHandler.sendMessage(msg);
    }

    public void setLyricsView(LyricsView v)
    {
        mView = v;
        mView.setSurfaceChangedListener(mLyricsViewListener);
        setLyricsAlign(mFontAlign);
    }

    public void setLyricsAlign(int i)
    {
        if (mView != null ) {
            mView.setAlign(i);
        }

        mFontAlign = i;
    }

    public void setLyricsStatusView(View v)
    {
        mStatusView = v;
    }

    public void setMediaPlayingState(boolean playing)
    {
        mMediaIsPlaying = playing;
        if (mMediaIsPlaying) play();
        else pause();
    }

    public void setMediaPlaybackService(IVoiceRepeaterService m)
    {
        mMediaService = m;
    }

    public void quickOneShot()
    {
        mHandler.removeMessages(MSG_QUICK_ONE_SHOT);
        mHandler.sendEmptyMessage(MSG_QUICK_ONE_SHOT);
    }




    private boolean isMediaPlaying()
    {
        return mMediaIsPlaying;
    }

    private boolean isEngineReady(){
        return mEngineState==LyricsEngine.STATE_READY;
    }

    private boolean canLooper()
    {
        return isMediaPlaying() && isEngineReady();
    }

    private void lanuchNewLooperMsg()
    {
        if (canLooper()){
            mHandler.removeMessages(MSG_LOOPER);
            mHandler.sendEmptyMessage(MSG_LOOPER);
            mForceUpdateOnce = true; // force update, refresh the view
        } else {
            if (isEngineReady()) updateLyricsOneShot();
        }
    }



    public void seek()
    {
        lanuchNewLooperMsg();
    }

    IVoiceRepeaterService mMediaService = null;



    private final Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {

                case MSG_LOOPER:
                    openDisplay();
                    break;

                case MSG_LOOPER_CANCLE:
                    closeDisplay();
                    break;

                case MSG_UPDATE_DISPLAY:
                    updateLyrics();
                    break;

                case MSG_SET_TRACK_INFO:
                    if (mView != null) mView.clearView();
                    mLyricsEngine.setStateListener(mEngineStateListener);
                    mLyricsEngine.start((String) msg.obj);
                    break;

                case MSG_QUICK_ONE_SHOT:
                    updateLyricsOneShot();
                    break;

                case MSG_UPDATE_STATUS:
                    UpdateLyricsStatus();
                    break;
                case MSG_STOP:
                    closeDisplay();
                    mLyricsEngine.stop();
                    break;
            }
        }
    };

    private void openDisplay()
    {
        if (!canLooper())
        {
            Log.d(TAG, "openDisplay failed with (playing="+mMediaIsPlaying+", engine="+mEngineState+")");
            return;
        }

        mHandler.removeMessages(MSG_UPDATE_DISPLAY);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DISPLAY, 50);
    }

    private void closeDisplay()
    {
        mHandler.removeMessages(MSG_UPDATE_DISPLAY);
    }

    private void UpdateLyricsStatus(){
        if (null == mStatusView) return;

        Log.d(TAG, "UpdateLyricsStatus mEngineState="+mEngineState);
        int visibility, resid;

        switch (mEngineState) {
            case LyricsEngine.STATE_LOADING:
                resid = R.string.lyrics_load;
                visibility = View.VISIBLE;
                break;

            case LyricsEngine.STATE_READY:
                resid = R.string.lyrics_load;
                visibility = View.INVISIBLE;
                break;

            case LyricsEngine.STATE_ERROR:
                if (mLyricsEngine.mErrIndex == ILyricsError.NO_FILE){
                    resid = R.string.no_text_file;
                    visibility = View.VISIBLE;
                    break;
                }else if (mLyricsEngine.mErrIndex != ILyricsError.CANCELLED){
                    resid = R.string.lyrics_file_err;
                    visibility = View.VISIBLE;
                    break;
                }

            default: return;
        }

        try {
            ((TextView)mStatusView).setText(resid);
            mStatusView.setVisibility(visibility);
        }catch (Exception e ){
            e.printStackTrace();
        };

    }
    private void updateEngineState(int newState)
    {
        mEngineState = newState;

        mLyricsArray = null;

        if (newState == LyricsEngine.STATE_LOADING) {
            mView.clearView();
        }

        if (newState == LyricsEngine.STATE_ERROR)
        {
            // error handle ...
            // we cannot make sure this function will be invoked only in
            // main UI thread.
            // so use a new remove-message action.
            mHandler.sendEmptyMessage(MSG_LOOPER_CANCLE);
            mView.clearView();
            Log.d(TAG, ">>>> OnStateUpdate Err=" + mLyricsEngine.mErrIndex);
        }
        else if (newState == LyricsEngine.STATE_READY)
        {
            // get offset tag
            mTagOffset = 0;
            String tagOffset = mLyricsEngine.getTagItem("offset");
            if (null != tagOffset) {
                try {
                    mTagOffset = Integer.valueOf(tagOffset);
                }catch (Exception e){}
            }
            Log.d(TAG, "<<<< OnStateUpdate ready:offset="+mTagOffset);

            // get lyrics data
            mLyricsArray = mLyricsEngine.getLyricsArray();
            Log.d(TAG, "<<<< OnStateUpdate ready:mLyricsArray=" + mLyricsArray);

            // initialize with a default values
            mLastItemData = new ItemData();

            // launch display loop
            lanuchNewLooperMsg();
        }

        mHandler.sendEmptyMessage(MSG_UPDATE_STATUS);
    }

    private LyricsEngine.OnStateUpdateListener mEngineStateListener = new LyricsEngine.OnStateUpdateListener()
    {
        public void OnStateUpdate(int state)
        {
            Log.d(TAG, ">>>> OnStateUpdate State=" + state);

            updateEngineState(state);
        }
    };

    private LyricsView.OnViewChanged mLyricsViewListener = new LyricsView.OnViewChanged()
    {
        public void OnChanged()
        {
            Log.d(TAG, ">>>> LyricsView OnChanged");
            lanuchNewLooperMsg();
        }
    };

    private ItemData mLastItemData = new ItemData();

    private void updateLyrics()
    {
        mHandler.removeMessages(MSG_UPDATE_DISPLAY);

        long interval = 300;

        //Log.d(TAG, "====");

        // ==========================
        // fetch music position
        long curMusicPos = fetchMusicPosition();

        // ==========================
        // display
        if (null != mLyricsArray && null != mView)
        {
            ItemData curItemData = new ItemData();
            mLyricsEngine.fetchItem(curMusicPos, curItemData, mLastItemData);

            /*
            Log.d(TAG, "= last (t="+curMusicPos+
                    ", cp="+mLastItemData.mCurLyricsPos+
                    ", np="+mLastItemData.mCurLyricsPos+
                    ", ct="+mLastItemData.mCurLyricsTime+
                    ", nt="+mLastItemData.mNexLyricsTime+")");

            Log.d(TAG, "= curr (t="+curMusicPos+
                    ", cp="+curItemData.mCurLyricsPos+
                    ", np="+curItemData.mCurLyricsPos+
                    ", ct="+curItemData.mCurLyricsTime+
                    ", nt="+curItemData.mNexLyricsTime+")");
            */

            if (ItemData.POS_OVER_DURATION == curItemData.mNexLyricsPos) {
                interval = 3000;
            } else {
                interval = curItemData.mNexLyricsTime - curMusicPos;
            }

            if      (interval < 0)    { interval = 0; }
            else if (interval > 5000) { interval = 5000; }

            /*
            Log.d(TAG, "interval="+interval+
                    ", cur.pos="+curItemData.mCurLyricsPos+
                    ", las.pos="+mLastItemData.mCurLyricsPos+
                    ", force="+mForceUpdateOnce);
             */

            if (curItemData.mCurLyricsPos != mLastItemData.mCurLyricsPos)
            {
                // if interval is too short to animation, then do not animation
                if (interval < LYRICS_ANIM_DURATION) {
                    mView.draw(mLyricsArray, curItemData.mCurLyricsPos, curItemData.mCurLyricsPos, false, 0);
                }else {
                    mView.draw(mLyricsArray, mLastItemData.mCurLyricsPos, curItemData.mCurLyricsPos, true, 0);
                }

                mLastItemData = curItemData;
                mForceUpdateOnce = false;
            }
            else if (mForceUpdateOnce)
            {
                Log.d(TAG, "got here");
                updateLyricsOneShot();
            }
        }
        else
        {
            Log.w(TAG, "mLyricsArray="+mLyricsArray+", mView="+mView);
        }

        // ==========================
        // setup next
        // Log.d(TAG, "= next interval=" + interval);
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_DISPLAY, interval);
    }

    private void updateLyricsOneShot()
    {
        mHandler.removeMessages(MSG_QUICK_ONE_SHOT);

        long curMusicPos = fetchMusicPosition();

        Log.d(TAG, "1111 updateLyricsOneShot");

        // ==========================
        // display
        if (null != mLyricsArray && null != mView)
        {
            ItemData curItemData = new ItemData();
            mLyricsEngine.fetchItem(curMusicPos, curItemData, mLastItemData);

            mView.draw(mLyricsArray, curItemData.mCurLyricsPos, curItemData.mCurLyricsPos, false, 0);
            mLastItemData = curItemData;
        }

        mForceUpdateOnce = false;
    }


    private long fetchMusicPosition()
    {
        if (null != mMediaService)
        {
            try
            {
                long pos = mMediaService.position() + mTagOffset;
                //Log.d(TAG, "fetchMusicPosition: pos="+pos+", offset="+mTagOffset);
                return pos;
            }
            catch (Exception e)
            {
                Log.e(TAG, "fetchMusicPosition error");
                e.printStackTrace();
                return 0;
            }
        }

        Log.e(TAG, "fetchMusicPosition mediaservice is null");
        return 0;
    }
}

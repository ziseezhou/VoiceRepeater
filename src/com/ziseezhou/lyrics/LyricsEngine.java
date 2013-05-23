/*************************************************************************
FILENAME LyricsEngine.java

COPYRIGHT (c) 2011

DESCRIPTION
The core module.
Manage the submodules and provide action and data interfaces.

--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/08/30   ZISEEZHOU       0x00000000       base code
2011/09/13   ZISEEZHOU       0x00000000       milestone, parser complete


*************************************************************************/

package com.ziseezhou.lyrics;

import android.util.Log;
import java.io.File;


class ItemData {
    // POS special value:
    // -1 represents: not begin
    // -2 represents: over duration
    // -9 represents: not initialed
    // 0 - DURATION is state: MID

    public static final int POS_NOT_UNKNOWN     = -9;
    public static final int POS_NOT_BEGING      = -1;
    // just will be set on mNexLyricsPos, indicates that lyrics reach the last one
    public static final int POS_OVER_DURATION   = 0x0FFFFFFF;



    public int     mCurLyricsPos   = POS_NOT_UNKNOWN;
    public int     mNexLyricsPos   = POS_NOT_UNKNOWN;
    public long    mCurLyricsTime  = 0;
    public long    mNexLyricsTime  = 0;
}


public class LyricsEngine{
    private static final String TAG = "LyricsEngine";

    public static final int STATE_ERROR     = -1;
    public static final int STATE_IDLE      = 0;
    public static final int STATE_LOADING   = 1; // include download process
    public static final int STATE_PARSING   = 2;
    public static final int STATE_READY     = 3;
    public static final int STATE_CANCELLED = 4;

    public int              mErrIndex = ILyricsError.NO_ERROR;

    private Thread          mParsingThread;
    private int             mState             = STATE_IDLE;
    private String          mCurMusicFilePath  = null;


    private ILyricsParser    mParser     = null;
    private LyricsFileDriver mFileDriver = null;
    private ILyricsDB        mDB         = null;

    private OnStateUpdateListener mOnStateUpdateListener = null;
    public interface OnStateUpdateListener
    {
        /**
         * Called to update LyricsDisplayer state.
         *
         * @param lp      the LyricsDisplayer the update pertains to
         * @param state   STATE_XXX
         */
        void OnStateUpdate(int state);
    }

    public void setStateListener(OnStateUpdateListener l){
        mOnStateUpdateListener = l;
    }


    private synchronized void checkCancelled(int nextState) throws Exception {
        if (getState() == STATE_CANCELLED) {
            throw new ErrException(ILyricsError.CANCELLED);
        }

        setState(nextState);
    }

    class ParsingThread implements Runnable {
        public void run(){
            try {
                Log.d(TAG, "ParsingThread +++++++++++++++++++++++++++ >>> run...thread="+Thread.currentThread().getId());

                long startTime = System.currentTimeMillis();

                mParser     = null;
                mFileDriver = null;
                mDB         = new LyricsDB();

                //****************************************************
                // step1. loading ...
                mFileDriver = new LyricsFileDriver(mCurMusicFilePath);
                mFileDriver.loadFile();

                //****************************************************
                // step2. parsing ...
                checkCancelled(STATE_PARSING);
                mParser = parserFactory(mFileDriver, mDB);
                mParser.parse();
                mDB.sort();

                //****************************************************
                // step3. ready
                checkCancelled(STATE_READY);

                long endTime = System.currentTimeMillis();

                Log.d(TAG, "parse time: "+(endTime-startTime)+"ms");
                Log.d(TAG, "ParsingThread +++++++++++++++++++++++++++ <<< finished");

                if (ILyricsConf.DUMP_OPEN){
                    mDB.dump();
                }

            }catch(ErrException e){
                int n = e.getErrNo();
                if (n!= ILyricsError.CANCELLED){
                    setErrState(n, "ParsingThread got ErrException");
                    e.printStackTrace();
                }
            }catch(Exception e){
                setErrState(ILyricsError.UNKNOWN, "ParsingThread got unknown Exception");
            }finally{
                mParser     = null; // release source
                mFileDriver = null; // release source
            }
        }
    }

    public void start(String musicFilePath){
        Log.d(TAG, "startParsing path="+musicFilePath);

        if (null==mCurMusicFilePath || !mCurMusicFilePath.equals(musicFilePath)){
            // stop last one
            stop();

            // set current file path
            mCurMusicFilePath = musicFilePath ;

            //****************************************************
            // step0. set loading state
            setState(STATE_LOADING);

            // launch working thread
            mParsingThread   = new Thread(new ParsingThread());
            mParsingThread.start();
        }
    }

    public void updateLyrics(int position){

    }

    public void stop () {
        boolean bNeedWait = false;
        synchronized (LyricsEngine.this){
            int curState = getState();
            if (curState==STATE_LOADING || curState==STATE_PARSING ) {
                bNeedWait = true;
                setState(STATE_CANCELLED);
            }
        }

        if (mFileDriver != null) mFileDriver.cancel();
        if (mParser != null)     mParser.cancel();

        // wait thread exit gracefully
        // this is very important, and need more care.
        if (bNeedWait){
            try {
                mParsingThread.join(5000); // avoid UI thread ANR
            } catch (InterruptedException e) {
                Log.w(TAG, "mParsingThread.join Exception");
                e.printStackTrace();
            }
        }

        // release sources...
        // {{{
        // }}}

        mParsingThread = null; // force release source.
        mCurMusicFilePath = null;

        setState(STATE_IDLE);  // reset IDLE
    }

    public String[] getLyricsArray(){
        return mDB.getLyricsArray();
    }

    public String getTagItem(String id) {
        return mDB.getTagItem(id);
    }

    public void fetchItem(long t, ItemData curItemDataOut, ItemData lastItemData){
        mDB.fetchItem(t, curItemDataOut, lastItemData);
    }



    private synchronized void setState(int state){
        mState = state;
        NotifyStateListener();
    }

    private void NotifyStateListener(){
        if (mOnStateUpdateListener != null){
            mOnStateUpdateListener.OnStateUpdate(mState);
        }
    }

    private synchronized int getState(){
        return mState;
    }

    private void setErrState(int errType, String errStr){
        Log.d(TAG, errStr);
        mErrIndex = errType;
        setState(STATE_ERROR);
    }

    private ILyricsParser parserFactory(LyricsFileDriver fd, ILyricsDB db){
        ILyricsParser parser = null;

        try {
            switch (fd.mLyricsType)
            {
                case ILyricsType.LRC:
                    parser = new LyricsLRCParser();
                    parser.setFD(fd);
                    parser.setDB(db);
                    break;

                case ILyricsType.KSC:
                    // extension for KSC
                    break;
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        Log.d(TAG, "parser="+parser);

        return parser;
    }
}
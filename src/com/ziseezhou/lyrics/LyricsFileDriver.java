/*************************************************************************
FILENAME LyricsFileDriver.java

COPYRIGHT (c) 2011

DESCRIPTION
File related actions.


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/08/30   ZISEEZHOU       0x00000000       base code


*************************************************************************/

package com.ziseezhou.lyrics;

import android.util.Log;
import java.io.File;

public class LyricsFileDriver{
    public static final String TAG = "LyricsFileDriver";
    public String mMusicFilePath  = null;
    public String mLyricsFilePath = null;
    public int mLyricsType        = ILyricsType.NONE;
    private boolean mCancelled    = false;
    private String mLineArray[]   = null;
    private static final int FILESIZE_MAX = 10*1024; // 10k

    // encoding
    // bytelineArray [MAX_ARRAY],
    // we do not define line-max, as we can be more flexible
    // when several lyrics in one-line;

    public LyricsFileDriver(String path){
        mMusicFilePath = path;
        mCancelled     = false;
    }

    private void checkRetAndThrowExp(int n) throws Exception{
        if (ILyricsError.NO_ERROR != n){
            throw new ErrException(n);
        }
    }

    public void loadFile() throws Exception{
        checkRetAndThrowExp(searchLyricsFile());
        return ;
    }

    public void cancel(){
        mCancelled = true;
    }

    private int searchLyricsFile(){
        String prePath  = null;
        String tryPath  = null;

        mLyricsFilePath = null;
        mLyricsType     = ILyricsType.NONE;

        Log.d(TAG, "searchLyricsFile path[in]="+mMusicFilePath);

        // remove the extension name
        if (null!=mMusicFilePath && mMusicFilePath.length()>0){
            int i = mMusicFilePath.lastIndexOf('.');
            if (i>-1 && i<mMusicFilePath.length()){
                prePath = mMusicFilePath.substring(0, i);
            }
        }

        if (null==prePath || prePath.length()<=0){
            Log.e(TAG, "invaliable prePath="+prePath);
            return ILyricsError.NO_FILE;
        }

        // try LRC
        String lrcExt[] = {"lrc", "LRC", "Lrc", "lRc", "lrC", "LRc", "lRC", "LrC"};
        for (int i=0; i<lrcExt.length; ++i){
            tryPath = prePath + "." + lrcExt[i];

            Log.d(TAG, "tryPath="+tryPath);

            File f = new File(tryPath);
            if (f.isFile() && f.exists()){
                if (FILESIZE_MAX < f.length()){
                    Log.e(TAG, "file is too big, size="+f.length());
                    return ILyricsError.FILE_TOO_BIG;
                }

                mLyricsFilePath = tryPath;
                mLyricsType     = ILyricsType.LRC;
                return ILyricsError.NO_ERROR;
            }
        }

        // try KSC {{{ }}}
        // try DOWNLOAD {{{ }}}

        // final, still there is no available one.
        Log.d(TAG, "no available lyrics file.");
        return ILyricsError.NO_FILE;
    }


    // extension block
    // lyrics download function
    // {{{
    // }}}
}
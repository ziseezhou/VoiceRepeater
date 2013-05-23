/*************************************************************************
FILENAME LyricsLRCDBDriver.java

COPYRIGHT (c) 2011

DESCRIPTION
DB for LRC.


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/08/30   ZISEEZHOU       0x00000000       base code


*************************************************************************/

package com.ziseezhou.lyrics;

import android.os.DropBoxManager.Entry;
import android.util.Log;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class LyricsDB implements ILyricsDB{
    private static final String TAG ="LyricsDB";
    private ArrayList<TimeItem> mTimeArray = new ArrayList<TimeItem>();
    private HashMap<String, String> mIdTagMap = new HashMap<String, String>();
    private String []mLyricsArray = null; // retrieve to display layer
    private int m_sLASTINDEX = 0; // for query both of mTimeArray & mLyricsArray
    private TimeItem m_sLAST_TIME_ITEM = null;
    private TimeItem m_sFIRST_TIME_ITEM = null;


    class TimeItem {
        TimeItem(long t, String l){
            mTime = t;
            mLyrics = l;
        }
        public long mTime;
        public String mLyrics;
    }

    class DBItemComparator implements Comparator<TimeItem> {
        public int compare(TimeItem i1, TimeItem i2)
        {
            if (i1.mTime > i2.mTime) return 1;
            else if (i1.mTime < i2.mTime) return -1;

            return 0;
        }
    }



    public void saveTimeItem(LinkedList<Long> timeList, String lyrics){
        Iterator<Long> it = timeList.iterator();
        while(it.hasNext()){
            long time = it.next();
            //Log.d(TAG, "@@@@ TI: "+time+" ["+lyrics+"]");
            mTimeArray.add(new TimeItem(time, lyrics));
        }
    }

    public void saveTagItem(String id, String content){
        //Log.d(TAG, "@@@@ ID: "+id+" ["+content+"]");
        mIdTagMap.put(id, content);
    }

    public String getTagItem(String id){
        if (mIdTagMap.containsKey(id)) {
            return mIdTagMap.get(id);
        }
        return null;
    }

    public Iterator getAllTags(){ return null;}

    public String[] getLyricsArray(){
        return mLyricsArray;
    }

    public void dump(){
        Log.d(TAG, "@@@@@@@@@@@@@@@@@@@@@@@");
        Iterator<java.util.Map.Entry<String, String>> idIt = mIdTagMap.entrySet().iterator();
        while (idIt.hasNext()){
        	java.util.Map.Entry<String, String> i = idIt.next();
        	Log.d(TAG, ""+i.getKey()+":"+i.getValue());
        }
        Log.d(TAG, "-----------------------");
        Iterator<TimeItem> it = mTimeArray.iterator();
        while (it.hasNext()){
            TimeItem t = it.next();
        	Log.d(TAG, ""+t.mTime+" "+t.mLyrics);
        }
        Log.d(TAG, "@@@@@@@@@@@@@@@@@@@@@@@");
    }

    public void sort() throws Exception {
        Log.d(TAG, "-------------");
        long startTime = System.currentTimeMillis();

        if (mTimeArray.size() <= 0) {
            throw new ErrException(ILyricsError.NO_DATA);
        }

        Collections.sort(mTimeArray, new DBItemComparator());

        mLyricsArray = new String[mTimeArray.size()];
        for (int i=0; i<mLyricsArray.length; ++i){
            mLyricsArray[i] = mTimeArray.get(i).mLyrics;
        }

        // initialize static reference for query
        m_sLASTINDEX       = mTimeArray.size() - 1;
        m_sLAST_TIME_ITEM  = mTimeArray.get(m_sLASTINDEX);
        m_sFIRST_TIME_ITEM = mTimeArray.get(0);

        long endTime = System.currentTimeMillis();
        Log.d(TAG, "sort time: "+(endTime-startTime)+"ms");
        Log.d(TAG, "-------------");
    }

    private static void setCurItemData(ItemData c, int curPos, int nexPos, long curTime, long nexTime){
        c.mCurLyricsPos  = curPos;
        c.mNexLyricsPos  = nexPos;
        c.mCurLyricsTime = curTime;
        c.mNexLyricsTime = nexTime;
    }

    public void fetchItem(long t, ItemData curItemData, ItemData lastItemData){
        int speedFeed = lastItemData.mNexLyricsPos;

        // Log.d(TAG, "fetchItem t="+t+", speedFeed="+speedFeed);

        // BOUNDARY check
        if (t<m_sFIRST_TIME_ITEM.mTime) {
            setCurItemData(curItemData,
                    ItemData.POS_NOT_BEGING,
                    0,
                    0,
                    m_sFIRST_TIME_ITEM.mTime);
            return;
        }

        if (t>=m_sLAST_TIME_ITEM.mTime) {
            setCurItemData(curItemData,
                    m_sLASTINDEX,
                    ItemData.POS_OVER_DURATION,
                    m_sLAST_TIME_ITEM.mTime,
                    m_sLAST_TIME_ITEM.mTime+1000);
            return;
        }


        // USE speed-feed to find current lyrics index
        if (0 <= speedFeed && speedFeed <= m_sLASTINDEX) {
            if (t >= mTimeArray.get(speedFeed).mTime) {
                if (speedFeed == m_sLASTINDEX) {
                    setCurItemData(curItemData,
                            m_sLASTINDEX,
                            ItemData.POS_OVER_DURATION,
                            m_sLAST_TIME_ITEM.mTime,
                            m_sLAST_TIME_ITEM.mTime+1000);
                    return;
                } else if (t < mTimeArray.get(speedFeed+1).mTime) {
                    setCurItemData(curItemData,
                            speedFeed,
                            (speedFeed+1),
                            mTimeArray.get(speedFeed).mTime,
                            mTimeArray.get(speedFeed+1).mTime);
                    return;
                }
            }
        }

        // binary search
        int index = binSearch(t);
        if (index >= m_sLASTINDEX) {
            setCurItemData(curItemData,
                    m_sLASTINDEX,
                    ItemData.POS_OVER_DURATION,
                    m_sLAST_TIME_ITEM.mTime,
                    m_sLAST_TIME_ITEM.mTime+1000);
            return;
        }else {
            setCurItemData(curItemData,
                    index,
                    (index+1),
                    mTimeArray.get(index).mTime,
                    mTimeArray.get(index+1).mTime);
        }
    }

    private int binSearch(long t){
        int mid;
        int start = 0;
        int end = mTimeArray.size()-1;

        while (start <= end) {
            mid = (start + end) / 2;

            if (mTimeArray.get(mid).mTime<=t && t<mTimeArray.get(mid+1).mTime){
                return mid;
            }else if (t > mTimeArray.get(mid+1).mTime){
                start = mid + 1;
            }
            else if (t < mTimeArray.get(mid).mTime){
                end   = mid - 1;
            }
        }

        // should never happen, as we has handle boundary in fetchItem function.
        return ItemData.POS_OVER_DURATION;
    }
}
/*************************************************************************
FILENAME LyricsLRCParse.java

COPYRIGHT (c) 2011

DESCRIPTION
LRC parser.

--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/08/30   ZISEEZHOU       0x00000000       base code


*************************************************************************/

package com.ziseezhou.lyrics;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.regex.Pattern;

public class LyricsLRCParser implements ILyricsParser{
    private static final String TAG ="LyricsLRCParser";
    private static final int    LINE_MAX_LEN = 500;
    private static final char[] mDividers = {'[',':',']','<','>'};
    private LyricsFileDriver    mfd;
    private ILyricsDB           mdb;
    private boolean             mCancelled   = false;
    private String              mCurLine     = null;
    private int                 mLexerCursor = 0;
    private LinkedList<String>  mWordList    = new LinkedList<String>();
    private LinkedList<Long>    mTimeList    = new LinkedList<Long>();
    private String              mLyrics      = null;


    public void cancel(){
        mCancelled = true;
    }

    public void setFD(LyricsFileDriver fd){
        mfd = fd;
    }

    public void setDB(ILyricsDB db){
        mdb = db;
    }

    public void parse() throws Exception{
        Log.d(TAG, "parse >>>");
        try {
            File f = new File(mfd.mLyricsFilePath);
            if (f.isFile() && f.exists()){
                long startTime = System.currentTimeMillis();
                String enc = LyricsFileEncodingSampleDet.getEncodeingName(mfd.mLyricsFilePath);
                long endTime = System.currentTimeMillis();

                Log.d(TAG, "path     : "+mfd.mLyricsFilePath);
                Log.d(TAG, "enc      : "+enc);
                Log.d(TAG, "det time : "+(endTime-startTime)+"ms");

                InputStreamReader reader= new InputStreamReader(new FileInputStream(f), enc);
                BufferedReader in = new BufferedReader(reader);
                String line;
                while (null != (line=in.readLine())){
                    checkCancelled();
                    setLexerSrc(line);
                    p_Line();
                }
            }
        }catch (Exception e){
            e.printStackTrace();
            throw e;
        }finally {
            ;
        }
    }


    private void setLexerSrc(String line){
        mCurLine = line.trim();
        mLexerCursor = 0;
        mWordList.clear();
    }


    private void checkCancelled() throws Exception {
        if (mCancelled) {
            throw new ErrException(ILyricsError.CANCELLED);
        }
    }

    private void removeParsedData(int cursor){
        //remove parsed data from mWordList
        if (cursor>=mWordList.size()) mWordList.clear();
        else {
            for (int i=0; i<=cursor; ++i){
                mWordList.poll();
            }
        }
    }

    private String getWordFromBuffer(int cursor){
        try {
            if (cursor<mWordList.size())
                return mWordList.get(cursor);
            else
                return null;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }


    /*****************************************************************************************
     * Lexer
     *---------------------------------------------------------------------------------------
     *
     ****************************************************************************************/
    private String getSymbol(){
        int cursor = -1;
        String word = null;

        if (mLexerCursor >= mCurLine.length()) {
            // {blank line|catch the end}
            return null;
        }

        for (int i=0; i<mDividers.length; ++i){
            int index = mCurLine.indexOf(mDividers[i], mLexerCursor);
            if (-1!=index && (cursor==-1 || index<cursor)){
                cursor = index; // found the nearest divider symbol
            }
        }

        // fetch the word
        if (-1 == cursor){
            word = mCurLine.substring(mLexerCursor, mCurLine.length()); // to the end
            mLexerCursor = mCurLine.length();
        } else {
            if (mLexerCursor == cursor){
                word = mCurLine.substring(mLexerCursor, cursor+1);
                mLexerCursor = cursor+1;
            } else if(mLexerCursor < cursor){
                word = mCurLine.substring(mLexerCursor, cursor);
                mLexerCursor = cursor;
            } else {
                // never be here
                Log.e(TAG, "fatal error");
            }
        }

        return word;
    }



    /*****************************************************************************************
     *
     *---------------------------------------------------------------------------------------
     * <Line>        ::= {<Sentence>}
     * <Sentence>    ::= <CommentTag>|<IdTag>|<AlphaString>
     * <CommentTag>  ::= <TagBegin>{<Space>}<TagMid><Words><TagEnd>
     * <IdTag>       ::= <TagBegin><Words><TagMid><Words><TagEnd>
     * <TimeTag>     ::= <TagBegin><TimeValue1><TagMid><TimeValue2><TagEnd>
     * <AlphaString> ::= <TimeTag>{<TimeTag>}<Lyrics>
     * <Lyrics>      ::= <LyPre>{<TimeTagExt>}<Words>{<TimeTagExt><Words>}
     * <TimeTagExt>  ::= <ExtTagBegin><TimeValue2><ExtTagEnd>
     * <TimeValue1>  ::= <Digital>{<Digital>}
     * <TimeValue2>  ::= <TimeValue1>{'.'<TimeValue1>}
     * <Words>       ::= {<Word>}
     * <LyPre>       ::= ['F'|'M'|'D']
     * <TagBegin>    ::= '['
     * <TagMid>      ::= ':'
     * <TagEnd>      ::= ']'
     * <ExtTagBegin> ::= '<'
     * <ExtTagEnd>   ::= '>'
     * <Digital>     ::= 0|1|2|...|8|9
     *
     *
     ****************************************************************************************/

    private boolean p_Line(){
        //Log.d(TAG, "p_Line");

        // check line max length
        if (mCurLine.length() > LINE_MAX_LEN){
            // ignore this line
            Log.w(TAG, "Ignore this line, length=>"+LINE_MAX_LEN);
            return true;
        }

        // buffer line words
        String word;
        mWordList.clear();
        while (null != (word=getSymbol())){
            if (false == mWordList.offer(word)){
                Log.e(TAG, "Word queue offer error");
            }
        }

        while (p_Sentence()){
            ;
        }

        return true;
    }

    private boolean p_Sentence(){
        //Log.d(TAG, "p_Sentence");

        if (mWordList.isEmpty()) {
            return false;
        }

        if (p_AlphaString()){}
        else if (p_CommentTag()){}
        else if (p_IdTag()){}
        else {
            // fault tolerance
            if (mWordList.size()>0 && ILyricsConf.FAULT_TOLERANCE_OPEN){
                mWordList.poll();
                return true;
            }else {
                mWordList.clear();
                return false;
            }
        }

        // got a AlphaString|Comment|ID
        return true;
    }

    private boolean p_CommentTag(){
        String word = null;
        int cursor = 0;

        // [
        word = getWordFromBuffer(cursor);
        if (!p_TagBegin(word)){
            return false;
        }

        // trim space and get ':'
        ++cursor;
        for (;cursor<mWordList.size() && null!=(word=mWordList.get(cursor)) && word.trim().length()==0; ++cursor ){}
        if (!p_TagMid(word)){
            return false;
        }

        // Yes, we got a comment tag
        // Just search the next sentence begin symbol '[',
        // and remove the all words in mWordList before the next '['
        for (;cursor<mWordList.size() &&  null!=(word=mWordList.get(cursor)); ++cursor){
            if (p_TagBegin(word)){
                for (int i=0; i<cursor; ++i){
                    mWordList.poll();
                }
                return true;
            }
        }

        // not find: next '[', consider the whole line as comment
        mWordList.clear();

        return true;
    }

    private boolean p_IdTag(){
        String word    = null;
        String idName  = "";
        String idValue = "";
        int cursor = 0;

        // [
        word = getWordFromBuffer(cursor);
        if (!p_TagBegin(word)){
            return false;
        }

        // search ID-name
        ++cursor;
        for (; cursor<mWordList.size() && null!=(word=mWordList.get(cursor)); ++cursor){
            // ':'
            if (p_TagMid(word)){
                if (idName.length()<=0) return false; //it's a comment
                break;
            }
            // '['
            else if (p_TagBegin(word)){
                return false; // another sentence begin
            }

            idName += word;
        }

        // not ':'
        if (!p_TagMid(word)){
            return false;
        }

        // search ID-Value
        ++cursor;
        for (;cursor<mWordList.size() && null!=(word=mWordList.get(cursor)); ++cursor){
            // ']'
            if (p_TagEnd(word)){
                break;
            }
            // '['
            else if (p_TagBegin(word)){
                return false; // another sentence begin
            }

            idValue += word;
        }

        // not ']'
        if (!p_TagEnd(word)){
            return false;
        }

        // Yes, we got a IDTAG, Store it in DB
        mdb.saveTagItem(idName, idValue);

        //remove id info from mWordList
        removeParsedData(cursor);

        return true;
    }


    private boolean p_AlphaString(){
        mTimeList.clear();

        if (!p_TimeTag()){
            return false;
        }

        while (p_TimeTag()){}

        if (!p_Lyrics()){
            return false;
        }

        // Yes, we got time-lyrics, store it in DB
        mdb.saveTimeItem(mTimeList, mLyrics);
        return true;
    }

    private boolean p_TimeTag(){
        String word  = null;
        Long   time1 = 0L;
        Long   time2 = 0L;
        int cursor   = 0;

        // '['
        word = getWordFromBuffer(cursor);
        if (!p_TagBegin(word)){
            return false;
        }

        // time1
        word = getWordFromBuffer(++cursor);
        if (-1 == (time1=p_TimeValue1(word))){
            return false;
        }

        // ':'
        word = getWordFromBuffer(++cursor);
        if (!p_TagMid(word)){
            return false;
        }

        // time2
        word = getWordFromBuffer(++cursor);
        if (-1 == (time2=p_TimeValue2(word))){
            return false;
        }

        // ']'
        word = getWordFromBuffer(++cursor);
        if (!p_TagEnd(word)){
            return false;
        }

        // Yes, we got the time-list, store it in mTimeList
        mTimeList.offer(time1+time2);

        // remove parsed data
        removeParsedData(cursor);

        return true;
    }

    private boolean p_Lyrics(){
        String word;

        mLyrics = "";

        while (!mWordList.isEmpty() && null!=(word=mWordList.get(0))){
            // '['
            if (p_TagBegin(word)){
                break; // another sentence begin
            }

            if (p_LyPre(word)){
                // just ignore now
            }

            if (p_TimeTagExt()){
                /**
                 * Here, just ignore enhanced format
                 * You can extend this to support enhanced format
                 * Refer to http://en.wikipedia.org/wiki/LRC_%28file_format%29#Enhanced_format
                 */
                continue;
            }

            mLyrics += word;

            // remove parsed data one by one
            mWordList.poll();
        }

        return true;
    }

    private boolean p_TimeTagExt(){
        String word  = null;
        Long   time2 = 0L;
        int cursor   = 0;

        // '<'
        word = getWordFromBuffer(cursor);
        if (!p_ExtTagBegin(word)){
            return false;
        }

        // time2
        word = getWordFromBuffer(++cursor);
        if (-1 == (time2=p_TimeValue2(word))){
            return false;
        }

        // '>'
        word = getWordFromBuffer(++cursor);
        if (!p_ExtTagEnd(word)){
            return false;
        }

        // now, just ignore this time tag
        // {{{
        // extend to support KARAOKE mode
        // }}}

        // remove parsed data
        removeParsedData(cursor);

        return true;
    }


    /**
     *
     * @param w
     * @return -1 represents error.
     */
    private long p_TimeValue1(String w){
        long time1 = 0L;

        if (!p_Number(w)){
            return -1;
        }

        if (w.length()>2) w = w.substring(0, 2);
        try { time1 = (long)Long.parseLong(w); }
        catch (Exception e){
            Log.e(TAG, "p_TimeValue1 toLong error");
            return -1;
        }

        // 0 <= hour <=59
        if (time1<0 || time1>59) {
            Log.e(TAG, "p_TimeValue1 toLong error");
            return -1;
        }

        time1 = time1*1000*60; // convert to millisecond

        return time1;
    }

    private long p_TimeValue2(String w){
        int index = -1;
        long time2 = 0L;

        if ((index=w.indexOf('.')) > -1){
            String w1 = w.substring(0, index);
            String w2 = w.substring(index+1);

            if (!p_Number(w1) || !p_Number(w2)){
                return -1;
            }

            if (w1.length()>2) w1 = w1.substring(0, 2);
            try { time2 = (long)Long.parseLong(w1); }
            catch (Exception e){
                Log.e(TAG, "p_TimeValue2 toLong error 1");
                return -1;
            }

            // minute 0 <= second <=59
            if (time2<0 || time2>59) {
                return -1;
            }

            time2 = time2*1000; // convert to millisecond

            if (w2.length()>3) w2 = w2.substring(0, 3);
            try {
                long milli = Long.parseLong(w2);
                switch (w2.length()){
                    case 2: time2 += milli*10; break;   //xx.xx
                    case 3: time2 += milli; break;      //xx.xxx
                    case 1: time2 += milli*100; break;  //xx.x
                }
            }
            catch (Exception e){
                Log.e(TAG, "p_TimeValue2 toLong error 2");
                return -1;
            }

        }else {
            if (!p_Number(w)){
                return -1;
            }

           if (w.length()>2) w = w.substring(0, 2);
           try { time2 = (long)Long.parseLong(w); }
           catch (Exception e){
               time2=0L;
               Log.e(TAG, "p_TimeValue2 toLong error 3");
           }

           // minute 0 <= second <=59
           if (time2<0 || time2>59) {
               return -1;
           }

           time2 = time2*1000; // convert to millisecond
        }

        return time2;
    }

    private boolean p_Words(){return true;}

    private boolean p_LyPre(String w){
        if ("F".equals(w) || "M".equals(w) || "D".equals(w)){
            return true;
        }

        return false;
    }

    private boolean p_TagBegin(String w){
        if ("[".equals(w)) return true;
        return false;
    }

    private boolean p_TagMid(String w){
        if (":".equals(w)) return true;
        return false;
    }

    private boolean p_TagEnd(String w){
        if ("]".equals(w)) return true;
        return false;
    }

    private boolean p_ExtTagBegin(String w){
        if ("<".equals(w)) return true;
        return false;
    }

    private boolean p_ExtTagEnd(String w){
        if (">".equals(w)) return true;
        return false;
    }

    private boolean p_Number(String w){
        Pattern pattern = Pattern.compile("[0-9]*");

        if (pattern.matcher(w).matches()){
            return true;
        }

        return false;
    }

    private boolean p_Digital(char c){
        return Character.isDigit(c);
    }
}
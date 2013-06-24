/*************************************************************************
FILENAME LyricsFileEncodingSampleDet.java

COPYRIGHT (c) 2011

DESCRIPTION
.


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/09/06   ZISEEZHOU       0x00000000       base code


*************************************************************************/

package com.ziseezhou.lyrics;

import org.mozilla.universalchardet.UniversalDetector;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class LyricsFileEncodingSampleDet {
    private static HashMap<String, String> encMap= new HashMap<String, String>();
    static {
        encMap.put("GB18030", "");
    }

    public static final int PACKAGE_SIZE = 4096;
    public static final int MAX_GUESS_PACKAGE_NUM = 4;
    public static final String TAG = "LyricsFileEncodingSampleDet";
    public static String getEncodeingName(String filePath) {
        Log.d(TAG, ">>>> file="+filePath);
        String encName = null;
        File f = new File(filePath);

        if (!f.isFile() || !f.exists()){
            Log.e(TAG, "file not exists");
            return encName;
        }

        try{
            FileInputStream in = new FileInputStream(f);
            byte[] head = new byte[3];
            in.read(head);

            //Log.d(TAG, ">>>> byte["+head[0]+", "+head[1]+", "+head[2]+"]");

            // check BOM firstly
            if (head[0]==-1 && head[1]==-2){
                encName = "UTF-16LE";
            }else if (head[0]==-2 && head[1]==-1){
                encName = "UTF-16BE";
            }
            else if (head[0]==-17 && head[1]==-69 && head[2]==-65){
                encName = "UTF-8";
            }
            else {
                encName = detectEnc(in);
            }

            in.close();
        }catch (Exception e){
            Log.e(TAG, "file error");
        }

        return encName;
    }
    public static void printAndroidAvailableCharsets(){
        Map charsets = Charset.availableCharsets();
        Iterator iterator = charsets.values().iterator();
        while (iterator.hasNext()){
            Charset cs = (Charset) iterator.next();
            Log.d(TAG, cs.name());
        }
    }

    private static String detectEnc(FileInputStream fis){
        String encName = "GBK"; // anyway, default is GBK
        byte[] buf = new byte[PACKAGE_SIZE]; // 1k

        UniversalDetector detector = new UniversalDetector(null);

        // (2)
        try {
            int nread;
            //while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
            for (int i=0; i<MAX_GUESS_PACKAGE_NUM && (nread = fis.read(buf)) > 0 && !detector.isDone(); ++i) {
              detector.handleData(buf, 0, nread);
            }
        }catch (Exception e){
            Log.e(TAG, "det file error");
            e.printStackTrace();
        }

        // (3)
        detector.dataEnd();

        // (4)
        String encoding = detector.getDetectedCharset();
        if (encoding != null) {
            encName = encoding;
            Log.d(TAG, "det enc="+encName);
        } else {
            Log.e(TAG, "det failed");
        }

        // (5)
        detector.reset();
        
        /*
         * @see https://code.google.com/p/juniversalchardet/
         * Encodings that can be detected

            Chinese
                ISO-2022-CN
                BIG5
                EUC-TW
                GB18030
                HZ-GB-23121 

            Cyrillic
                ISO-8859-5
                KOI8-R
                WINDOWS-1251
                MACCYRILLIC
                IBM866
                IBM855 

            Greek
                ISO-8859-7
                WINDOWS-1253 

            Hebrew
                ISO-8859-8
                WINDOWS-1255 

            Japanese
                ISO-2022-JP
                SHIFT_JIS
                EUC-JP 

            Korean
                ISO-2022-KR
                EUC-KR 

            Unicode
                UTF-8
                UTF-16BE / UTF-16LE
                UTF-32BE / UTF-32LE / X-ISO-10646-UCS-4-34121 / X-ISO-10646-UCS-4-21431 

            Others
                WINDOWS-1252 
        */
        
        // fix bug, if Others was detected, we set it to default "GBK"
        if ("WINDOWS-1252".equalsIgnoreCase(encName)) {
            Log.i(TAG, "det WINDOWS-1252 manully change to GBK");
            encName = "GBK";
        }

        return encName;
    }
}
/*************************************************************************
FILENAME Util.java

COPYRIGHT (c) 2013

DESCRIPTION
This file define Util, the main activity

--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2013/05/18   ZISEEZHOU       0x00000000       base code
*************************************************************************/
package com.ziseezhou.voicerepeater;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.Formatter;
import java.util.Locale;

public class Utils {
	private static final String TAG = "VoiceRepeaterUtil";
	
	static int getCardId(Context context) {
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Uri.parse("content://media/external/fs_id"), null, null, null, null);
        int id = -1;
        if (c != null) {
            c.moveToFirst();
            id = c.getInt(0);
            c.close();
        }
        return id;
    }
	
	public static Cursor query(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder, int limit) {
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return null;
            }
            if (limit > 0) {
                uri = uri.buildUpon().appendQueryParameter("limit", "" + limit).build();
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
         } catch (UnsupportedOperationException ex) {
            return null;
        }
        
    }
	
    public static Cursor query(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        return query(context, uri, projection, selection, selectionArgs, sortOrder, 0);
    }
    
    private static StringBuilder sFormatBuilder = new StringBuilder();
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
    private static final Object[] sTimeArgs = new Object[5];
    
    public static String makeTimeString(Context context, long secs) {
        String durationformat = context.getString(
                secs < 3600 ? R.string.durationformatshort : R.string.durationformatlong);
        
        /* Provide multiple arguments so the format can be changed easily
         * by modifying the xml.
         */
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }
    
    static int getIntPref(Context context, String name, int def) {
        SharedPreferences prefs = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        return prefs.getInt(name, def);
    }
    
    static void setIntPref(Context context, String name, int value) {
        final SharedPreferences prefs =
            context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        Editor ed = prefs.edit();
        ed.putInt(name, value);
        ed.commit();
    }
    
    static String getStringPref(Context context, String name, String def) {
        SharedPreferences prefs = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        return prefs.getString(name, def);
    }
    
    static void setStringPref(Context context, String name, String value) {
        final SharedPreferences prefs =
            context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        Editor ed = prefs.edit();
        ed.putString(name, value);
        ed.commit();
    }
    
    public static long [] getSongListForCursor(Cursor cursor) {
        if (cursor == null) {
            return new long[0];
        }
        int len = cursor.getCount();
        long [] list = new long[len];
        cursor.moveToFirst();
        int colidx = -1;
        try {
            colidx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID);
        } catch (IllegalArgumentException ex) {
            colidx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        }
        for (int i = 0; i < len; i++) {
            list[i] = cursor.getLong(colidx);
            cursor.moveToNext();
        }
        return list;
    }
}
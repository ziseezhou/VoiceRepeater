/*************************************************************************
FILENAME ILyricsDBDriver.java

COPYRIGHT (c) 2011

DESCRIPTION
DB interface.


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/08/30   ZISEEZHOU       0x00000000       base code


*************************************************************************/

package com.ziseezhou.lyrics;

import java.util.Iterator;
import java.util.LinkedList;

public interface ILyricsDB {
    public void saveTimeItem(LinkedList<Long> timeList, String lyrics);
    public void saveTagItem(String id, String content);
    public String getTagItem(String id);
    public Iterator getAllTags();
    public void dump();
    public void sort()throws Exception;;
    public String[] getLyricsArray();
    public void fetchItem(long t, ItemData curItemData, ItemData lastItemData);
}
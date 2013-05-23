/*************************************************************************
FILENAME ILyricsType.java

COPYRIGHT (c) 2011

DESCRIPTION
LRC parser.


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/09/06   ZISEEZHOU       0x00000000       base code
2011/09/06   ZISEEZHOU       0x00000000       add type:txt


*************************************************************************/
package com.ziseezhou.lyrics;

public interface ILyricsType{
    public static final int NONE        = 0;
    public static final int LRC         = 1;
    public static final int KSC         = 2;
    public static final int TXT         = 3;
}
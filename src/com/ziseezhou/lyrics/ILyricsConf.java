/*************************************************************************
FILENAME ILyricsConf.java

COPYRIGHT (c) 2011

DESCRIPTION
Like global configuration, can be used for debug,dump...


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/09/06   ZISEEZHOU       0x00000000       base code


*************************************************************************/

package com.ziseezhou.lyrics;

interface ILyricsConf{
    // const
    int FONT_ALIGN_LEFT = 0;
    int FONT_ALIGN_MIDDLE = 1;

    // configuration
    boolean DUMP_OPEN = false; // for debug out engine result or not
    boolean FAULT_TOLERANCE_OPEN = true; // for lyrics engine parse strictly or not

    int LYRICS_ANIM_FRAME_NUM = 30; // frame number of a animation
    int LYRICS_ANIM_FRAME_INTERVAL = 25; //ms
    int LYRICS_ANIM_DURATION = LYRICS_ANIM_FRAME_NUM*LYRICS_ANIM_FRAME_INTERVAL;

    int LYRICS_FONT_SIZE = 16;
    int LYRICS_FONT_LINE_SPACE = 2;
    int LYRICS_FONT_ALIGN = FONT_ALIGN_LEFT;
}

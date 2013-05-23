/*************************************************************************
FILENAME ILyricsError.java

COPYRIGHT (c) 2011

DESCRIPTION
LRC parser.


--------------------------------------------------------------------------
HISTORY
DATE         AUTHOR          ACTIVEID         BRIEF
2011/09/06   ZISEEZHOU       0x00000000       base code


*************************************************************************/
package com.ziseezhou.lyrics;

class ErrException extends Exception{
    private static final long serialVersionUID = 1L;

    private int mErrno = ILyricsError.UNKNOWN;
    public ErrException(int errno){
        mErrno = errno;
    }
    public int getErrNo(){
        return mErrno;
    }
}

public interface ILyricsError{
    public static final int NO_ERROR        = 0;
    public static final int CANCELLED       = 1;
    public static final int NO_FILE         = 2;
    public static final int FILE_TOO_BIG    = 3;
    public static final int NO_DATA         = 4;
    public static final int UNKNOWN         = 0x0FFFFFFF;
}
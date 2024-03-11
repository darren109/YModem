package com.darren.ymodem;

public interface CRC {
    int getCRCLength();

    long calcCRC(byte[] block);
}

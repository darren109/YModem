package com.darren.ymodem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * XModem with 1K block
 */
public class XModem1K {
    private Modem modem;

    /**
     * Constructor
     *
     * @param inputStream  stream for reading received data from other side
     * @param outputStream stream for writing data to other side
     */
    public XModem1K(InputStream inputStream, OutputStream outputStream) {
        this.modem = new Modem(inputStream, outputStream);
    }

    /**
     * Send a file. <br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param file
     * @throws IOException
     */
    public void send(File file) throws IOException, InterruptedException {
        modem.send(file, true);
    }

    /**
     * Receive file <br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param file file path for storing
     * @throws IOException
     */
    public void receive(File file) throws IOException {
        modem.receive(file, false);
    }

}

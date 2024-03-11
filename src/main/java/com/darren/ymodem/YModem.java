package com.darren.ymodem;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * YModem.<br/>
 * Block 0 contain minimal file information (only filename)
 */
public class YModem {
    private Modem modem;

    /**
     * Constructor
     *
     * @param inputStream  stream for reading received data from other side
     * @param outputStream stream for writing data to other side
     */
    public YModem(InputStream inputStream, OutputStream outputStream) {
        this.modem = new Modem(inputStream, outputStream);
    }

    /**
     * Send a file.<br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param file
     * @throws IOException
     */
    public void send(File file) throws IOException {
        //check filename
        if (!file.getName().matches("\\w{1,50}\\.\\w{1,3}")) {
            throw new IOException("Filename must be in DOS style (no spaces, max 8.3)");
        }

        //open file
        try (DataInputStream dataStream = new DataInputStream(new FileInputStream(file))) {

            Timer timer = new Timer(Modem.WAIT_FOR_RECEIVER_TIMEOUT).start();
            boolean useCRC16 = modem.waitReceiverRequest(timer);
            CRC crc;
            if (useCRC16)
                crc = new CRC16();
            else
                crc = new CRC8();

            //send block 0
            String fileNameString = file.getName() + (char) 0 + file.length() + " " + (char) 0;
            byte[] fileNameBytes = Arrays.copyOf(fileNameString.getBytes(), 128);
            modem.sendBlock(0, Arrays.copyOf(fileNameBytes, 128), 128, crc);

            modem.waitReceiverRequest(timer);
            //send data
            byte[] block = new byte[1024];
            modem.sendDataBlocks(dataStream, 1, crc, block);

            modem.sendEOT();
        }
    }

    /**
     * Send files in batch mode.<br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param files
     * @throws IOException
     */
    public void batchSend(File... files) throws IOException {
        for (File file : files) {
            send(file);
        }

        sendBatchStop();
    }

    private void sendBatchStop() throws IOException {
        Timer timer = new Timer(Modem.WAIT_FOR_RECEIVER_TIMEOUT).start();
        boolean useCRC16 = modem.waitReceiverRequest(timer);
        CRC crc;
        if (useCRC16)
            crc = new CRC16();
        else
            crc = new CRC8();

        //send block 0
        byte[] bytes = new byte[128];
        modem.sendBlock(0, bytes, bytes.length, crc);
    }

    /**
     * Receive single file <br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param directory directory where file will be saved
     * @return path to created file
     * @throws IOException
     */
    public File receiveSingleFileInDirectory(File directory) throws IOException {
        return receive(directory, true);
    }

    /**
     * Receive files in batch mode <br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param directory directory where files will be saved
     * @throws IOException
     */
    public void receiveFilesInDirectory(File directory) throws IOException {
        while (receive(directory, true) != null) {
        }
    }

    /**
     * Receive path <br/>
     * <p>
     * This method support correct thread interruption, when thread is interrupted "cancel of transmission" will be send.
     * So you can move long transmission to other thread and interrupt it according to your algorithm.
     *
     * @param path path to file where data will be saved
     * @return path to file
     * @throws IOException
     */
    public File receive(File path) throws IOException {
        return receive(path, false);
    }

    private File receive(File path, boolean inDirectory) throws IOException {
        DataOutputStream dataOutput = null;
        File filePath;
        try {
            CRC crc = new CRC16();
            int errorCount = 0;

            // process block 0
            byte[] block;
            int character;
            while (true) {
                character = modem.requestTransmissionStart(true);
                try {
                    // read file name from zero block
                    block = modem.readBlock(0, (character == Modem.SOH), crc);
                    if (inDirectory) {
                        StringBuilder sb = new StringBuilder();
                        if (block[0] == 0) {
                            //this is stop block of batch file transfer
                            modem.sendByte(Modem.ACK);
                            return null;
                        }
                        for (int i = 0; i < block.length; i++) {
                            if (block[i] == 0) {
                                break;
                            }
                            sb.append((char) block[i]);
                        }
                        filePath = new File(path, sb.toString());
                    } else {
                        filePath = path;
                    }
                    dataOutput = new DataOutputStream(new FileOutputStream(filePath));
                    modem.sendByte(Modem.ACK);
                    break;
                } catch (TimeoutException | Modem.InvalidBlockException e) {
                    errorCount++;
                    if (errorCount == Modem.MAXERRORS) {
                        modem.interruptTransmission();
                        throw new IOException("Transmission aborted, error count exceeded max");
                    }
                    modem.sendByte(Modem.NAK);
                } catch (Modem.RepeatedBlockException | Modem.SynchronizationLostException e) {
                    //fatal transmission error
                    modem.interruptTransmission();
                    throw new IOException("Fatal transmission error", e);
                }
            }

            //receive data blocks
            modem.receive(filePath, true);
        } finally {
            if (dataOutput != null) {
                dataOutput.close();
            }
        }
        return filePath;
    }
}

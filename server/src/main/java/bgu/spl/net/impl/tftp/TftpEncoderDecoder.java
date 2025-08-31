package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Vector;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private short opCode = 0;
    private Vector<Byte> bytes = new Vector<>();
    private byte[] BytesArray = null;
    private boolean TheReceivedOpCode = false;
    private short packetSize = 0;
    private final int RRQ = 1;
    private final int WRQ = 2;
    private final int DATA = 3;
    private final int ACK = 4;
    private final int ERROR = 5;
    private final int DIRQ = 6;
    private final int LOGRQ = 7;
    private final int DELRQ = 8;
    private final int BCAST = 9;
    private final int DISC = 10;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        bytes.add(nextByte);

        if (bytes.size() == 1) {
            opCode = nextByte;
        }
        if (bytes.size() == 2) {
            opCode = (short) (((short) opCode & 0xff) << 8 | (short) (nextByte & 0xff));
            TheReceivedOpCode = true;
            if (opCode == DIRQ || opCode == DISC) {
                BytesArray = FromVectorToArray(bytes);
                bytes = new Vector<>();
                TheReceivedOpCode = false;
                return BytesArray;
            }
        }

        if (TheReceivedOpCode) {
            switch (opCode) {
                case RRQ:
                case WRQ:
                case LOGRQ:
                case DELRQ:
                    if (nextByte == 0) {
                        BytesArray = FromVectorToArray(bytes);
                        String filename = new String(BytesArray, 2, BytesArray.length - 3, StandardCharsets.UTF_8);
                        bytes = new Vector<>();
                        TheReceivedOpCode = false;
                        return MakePacket(opCode, filename);
                    }
                    break;
                case DATA:
                    if (bytes.size() == 3) {
                        packetSize = nextByte;
                    }
                    if (bytes.size() == 4) {
                        packetSize = (short) (packetSize << 8 | (short) (nextByte & 0xff));
                    }
                    if (bytes.size() == packetSize + 6) {
                        BytesArray = FromVectorToArray(bytes);
                        short blocknumber = FromByteToShort(Arrays.copyOfRange(BytesArray, 4, 6));
                        byte[] data = Arrays.copyOfRange(BytesArray, 6, BytesArray.length);
                        bytes = new Vector<>();
                        TheReceivedOpCode = false;
                        return MakePacket(opCode, packetSize, blocknumber, data);
                    }
                    break;
                case ACK:
                    if (bytes.size() == 4) {
                        BytesArray = FromVectorToArray(bytes);
                        short blockNumber = FromByteToShort(Arrays.copyOfRange(BytesArray, 2, 4));
                        bytes = new Vector<>();
                        TheReceivedOpCode = false;
                        return MakePacket(opCode, blockNumber);
                    }
                    break;
                case ERROR:
                    if (bytes.size() >= 4 && nextByte == 0) {
                        BytesArray = FromVectorToArray(bytes);
                        short errorCode = FromByteToShort(Arrays.copyOfRange(BytesArray, 2, 4));
                        String errorMessage = new String(BytesArray, 4, BytesArray.length - 5, StandardCharsets.UTF_8);
                        bytes = new Vector<>();
                        TheReceivedOpCode = false;
                        return MakePacket(opCode, errorCode, errorMessage);
                    }
                    break;
                case BCAST:
                    if (nextByte == 0 && bytes.size() != 2) {
                        BytesArray = FromVectorToArray(bytes);
                        bytes = new Vector<>();
                        TheReceivedOpCode = false;
                        return BytesArray;
                    }
                    break;
            }
        }

        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        short opCode = FromByteToShort(Arrays.copyOfRange(message, 0, 2));
        switch (opCode) {
            case DATA:
                return encodeDataPacket(message);
            case ACK:
                return encodeAckPacket(message);
            case ERROR:
                return encodeErrorPacket(message);
            case BCAST:
                return encodeBcastPacket(message);
            default:
                return message;
        }
    }

    private byte[] encodeAckPacket(byte[] message) {
        short blockNumber = FromByteToShort(Arrays.copyOfRange(message, 2, 4));
        return MakeTheACKPacket((short) 4, blockNumber);
    }

    private byte[] encodeDataPacket(byte[] message) {
        short packetSize = FromByteToShort(Arrays.copyOfRange(message, 2, 4));
        short blockNum = FromByteToShort(Arrays.copyOfRange(message, 4, 6));
        byte[] data = Arrays.copyOfRange(message, 6, message.length);
        return MakeDATAPacket((short) 3, packetSize, blockNum, data);
    }

    private byte[] MakePacket(short opCode, Object... args) {
        switch (opCode) {
            case RRQ:
            case WRQ:
                String filename = (String) args[0];
                return MakeRWPacket(opCode, filename);
            case DATA:
                short packetSize = (short) args[0];
                short blockNum = (short) args[1];
                byte[] data = (byte[]) args[2];
                return MakeDATAPacket(opCode, packetSize, blockNum, data);
            case ACK:
                short blockNumber = (short) args[0];
                return MakeTheACKPacket(opCode, blockNumber);
            case ERROR:
                short errorCode = (short) args[0];
                String errorMessage = (String) args[1];
                return MakeTheERRORPacket(opCode, errorCode, errorMessage);
            case DIRQ:
                return MakeDIRQPacket(opCode);
            case LOGRQ:
                String username = (String) args[0];
                return MakeLogrqPacket(opCode, username);
            case DELRQ:
                String delFilename = (String) args[0];
                return MakeDelrqPacket(opCode, delFilename);
            case DISC:
                return MakeDiscPacket(opCode);
            default:
                return null;
        }
    }

    private byte[] MakeRWPacket(short opCode, String filename) {
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[filenameBytes.length + 3];
        System.arraycopy(FromShortToByte(opCode), 0, packet, 0, 2);
        System.arraycopy(filenameBytes, 0, packet, 2, filenameBytes.length);
        packet[packet.length - 1] = 0;
        return packet;
    }

    private byte[] MakeDelrqPacket(short opCode, String filename) {
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[filenameBytes.length + 3];
        System.arraycopy(FromShortToByte(opCode), 0, packet, 0, 2);
        System.arraycopy(filenameBytes, 0, packet, 2, filenameBytes.length);
        packet[packet.length - 1] = 0;
        return packet;
    }

    private byte[] MakeDATAPacket(short opCode, short packetSize, short blockNum, byte[] data) {
        byte[] packet = new byte[data.length + 6];
        System.arraycopy(FromShortToByte(opCode), 0, packet, 0, 2);
        System.arraycopy(FromShortToByte(packetSize), 0, packet, 2, 2);
        System.arraycopy(FromShortToByte(blockNum), 0, packet, 4, 2);
        System.arraycopy(data, 0, packet, 6, data.length);
        return packet;
    }

    private byte[] MakeTheACKPacket(short opCode, short blockNumber) {
        byte[] packet = new byte[4];
        System.arraycopy(FromShortToByte(opCode), 0, packet, 0, 2);
        System.arraycopy(FromShortToByte(blockNumber), 0, packet, 2, 2);
        return packet;
    }

    private byte[] MakeTheERRORPacket(short opCode, short errorCode, String errorMessage) {
        byte[] errorMessageArr = errorMessage.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[errorMessageArr.length + 5];
        System.arraycopy(FromShortToByte(opCode), 0, packet, 0, 2);
        System.arraycopy(FromShortToByte(errorCode), 0, packet, 2, 2);
        System.arraycopy(errorMessageArr, 0, packet, 4, errorMessageArr.length);
        packet[packet.length - 1] = 0;
        return packet;
    }

    private byte[] MakeDIRQPacket(short opCode) {
        return FromShortToByte(opCode);
    }

    private byte[] MakeLogrqPacket(short opCode, String username) {
        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[usernameBytes.length + 3];
        System.arraycopy(FromShortToByte(opCode), 0, packet, 0, 2);
        System.arraycopy(usernameBytes, 0, packet, 2, usernameBytes.length);
        packet[packet.length - 1] = 0;
        return packet;
    }

    private byte[] MakeDiscPacket(short opCode) {
        return FromShortToByte(opCode);
    }

    private byte[] encodeErrorPacket(byte[] message) {
        short errorCode = FromByteToShort(Arrays.copyOfRange(message, 2, 4));
        String errorMessage = new String(message, 4, message.length - 5, StandardCharsets.UTF_8);
        return MakeTheERRORPacket((short) 5, errorCode, errorMessage);
    }

    private byte[] encodeBcastPacket(byte[] message) {
        byte addDel = message[2];
        String filename = new String(message, 3, message.length - 4, StandardCharsets.UTF_8);
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[filenameBytes.length + 4];
        System.arraycopy(FromShortToByte((short) 9), 0, packet, 0, 2);
        packet[2] = addDel;
        System.arraycopy(filenameBytes, 0, packet, 3, filenameBytes.length);
        packet[packet.length - 1] = 0;
        return packet;
    }

    private byte[] FromVectorToArray(Vector<Byte> vec) {
        byte[] BytesArray = new byte[vec.size()];
        for (int i = 0; i < BytesArray.length; i++)
            BytesArray[i] = vec.get(i);
        return BytesArray;
    }

    private short FromByteToShort(byte[] byteArr) {
        return (short) ((byteArr[0] & 0xff) << 8 | (byteArr[1] & 0xff));
    }

    private byte[] FromShortToByte(short num) {
        byte[] BytesArray = new byte[2];
        BytesArray[0] = (byte) ((num >> 8) & 0xFF);
        BytesArray[1] = (byte) (num & 0xFF);
        return BytesArray;
    }
}
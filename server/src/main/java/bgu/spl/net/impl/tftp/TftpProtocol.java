package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private boolean shouldTerminate;
    private int connectionId;
    private Connections<byte[]> connections;
    private String username;
    private ConcurrentHashMap<String, FileOutputStream> fileOutputStreams;
    private ConcurrentHashMap<String, FileInputStream> fileInputStreams;
    private ConcurrentHashMap<String, Short> blockNumbers;
    private static ConcurrentHashMap<Integer, String> loggedInUsers = new ConcurrentHashMap<>();
    private String fileName;
    private final int RRQ=1;
    private final int WRQ=2;
    private final int DATA=3;
    private final int ACK=4;
    private final int ERROR=5;
    private final int DIRQ =6;
    private final int LOGRQ=7;
    private final int DELRQ=8;
    private final int DISC=10;


    public TftpProtocol(Connections<byte[]> connections) {
        shouldTerminate= false;
        this.connections= connections;
        fileOutputStreams=new ConcurrentHashMap<>();
        fileInputStreams= new ConcurrentHashMap<>();
        blockNumbers=new ConcurrentHashMap<>();
    }

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId =connectionId;
    }

    @Override
    public byte[] process(byte[] message) {
        short opcode=fromBytesToShort(Arrays.copyOfRange(message,0,2));
        System.out.println("the opc:" + opcode);
        switch (opcode) {
            case RRQ: 
                return RRQcase(message);
            case WRQ: 
                return WRQcase(message);
            case DATA: 
                return DATAcase(message);
            case ACK: 
                return ACKcase(message);
            case ERROR: 
                return ERRORcase(message);
            case DIRQ: 
                return DIRQcase();
            case LOGRQ: 
                return LOGRQcase(message);
            case DELRQ: 
                return DELRQcase(message);
            case DISC: // DISC
                return DISCcase();
            default:
                return creatERROR(4, "Illegal TFTP operation");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private byte[] RRQcase(byte[] message) {
        if (checkIfLogedIn()) {
            String filename = getFilename(message);
            FileInputStream fileInputStream = fileInputStreams.get(filename);
            if (fileInputStream == null) {
                try {
                    File TheFile= new File("Files");
                    if (!TheFile.exists()) {
                        TheFile.mkdirs();
                    }
                     String filePath= "Files/"+ filename ;
                     File file = new File(filePath);
                    if (file.exists()) {
                        if (blockNumbers.containsKey(filename)) {
                            return creatERROR(2, "Access violation");
                        }
                        fileInputStream =new FileInputStream(file);
                        fileInputStreams.put(filename, fileInputStream);
                        blockNumbers.put(filename,(short) 1);
                        setfilename(filename);
                        byte[]data =readDataBlockHelpFunc(fileInputStream);
                        sendDATA(filename, (short)1, data);
                    } else {
                        return creatERROR(1, "File not found");
                    }
                } catch (IOException e) {
                    return creatERROR(0, "Error reading file");
                }
            } else {
                return creatERROR(2, "Access violation");
            }
        }
        return null;
    }

    private byte[] readDataBlockHelpFunc(FileInputStream fileInputStream) throws IOException { //read data block
        byte[] buffer = new byte[512];
        int bytesRead =fileInputStream.read(buffer);
        if (bytesRead ==-1) {return new byte[0];
        } else
         {
            return Arrays.copyOf(buffer, bytesRead);
        }
    }

    private void setfilename(String filename) {
        fileName = filename;
    }

private byte[] WRQcase(byte[] message) {
    if (checkIfLogedIn()) {
        String filename =getFilename(message);
        FileOutputStream fileOutputStream = fileOutputStreams.get(filename);

        if (fileOutputStream== null) {
            try {
                File directory= new File("Files");
                if (!directory.exists()){ directory.mkdirs();
                }
                String filePath = "Files/" + filename;
                File file = new File(filePath);
                if (file.exists()) {
                    return creatERROR(5, "File already exists");
                }
                fileOutputStream =new FileOutputStream(file);
                fileOutputStreams.put(filename, fileOutputStream);
                blockNumbers.put(filename,(short) 0);
                setfilename(filename);
                sendACK(filename, (short) 0);
            } catch (IOException e) {
                return creatERROR(0, "Error creating file");
            }
        } else {
            return creatERROR(5, "File already exists");
        }
    }
    return null;
}
    private byte[] DATAcase(byte[] message) {
        if (checkIfLogedIn()) {
            short blockNumber = fromBytesToShort(Arrays.copyOfRange(message, 4, 6));
            byte[] data =Arrays.copyOfRange(message, 6, message.length);
            String filename= fileName;
            Short excBlockNumber = blockNumbers.get(filename);
            if (excBlockNumber !=null && blockNumber== excBlockNumber+1) {
                try {
                    FileOutputStream fileOutputStream =fileOutputStreams.get(filename);
                    if (fileOutputStream !=null) {
                        fileOutputStream.write(data);
                        sendACK(filename, (short)(excBlockNumber+1));
                        blockNumbers.put(filename, (short) (excBlockNumber+1));
    
                        if (data.length <512) {
                            fileOutputStream.close();
                            fileOutputStreams.remove(filename);
                            //close file input stream and remove from the map
                            FileInputStream fileInputStream= fileInputStreams.get(filename);
                            if (fileInputStream!= null) {
                                fileInputStream.close();
                                fileInputStreams.remove(filename);
                            }
    
                            blockNumbers.remove(filename);
                            sendRIRQ(1,filename);
                        }
                    }
                } catch (IOException e) {
                    return creatERROR(0, "Error writing to file");
                }
            } else {
                return creatERROR(0, "Invalid block number");
            }
        }
        return null;
    }

    private byte[] ACKcase(byte[] message) {
        if (checkIfLogedIn()) {
        
            short blockNumber = fromBytesToShort(Arrays.copyOfRange(message,2,4));
            if (blockNumber==0) {
                //ACK for LOGRQ, WRQ, DELRQ, or DISC
                String filename=fileName;
                if(filename!=null) {
                //ACK for WRQ
                    FileInputStream fileInputStream= fileInputStreams.get(filename);
                    if (fileInputStream!= null) {
                        try {
                            byte[] data =readDataBlockHelpFunc(fileInputStream);
                            sendDATA(filename, (short) 1, data);
                            blockNumbers.put(filename, (short) 1);
                        } catch (IOException e) {
                            return creatERROR(0, "Error reading file");
                        }
                    }
                }
            }
             else
              {
              
                if (fileName != null) {                    //ACK for DATA packet
                    String filename = fileName;
                    FileInputStream fileInputStream = fileInputStreams.get(filename);
                    Short excBlockNumber = blockNumbers.get(filename);
                    if (excBlockNumber != null && blockNumber == excBlockNumber) {
                        try {
                            byte[] data =readDataBlockHelpFunc(fileInputStream);
                            if (data.length == 0) {
                                fileInputStream.close();
                                fileInputStreams.remove(filename);
                                blockNumbers.remove(filename);
                            } else {
                                sendDATA(filename,(short) (blockNumber+1), data);
                                blockNumbers.put(filename, (short) (blockNumber + 1));
                            }
                        } catch (IOException e) {
                            return creatERROR(0, "Error reading file");
                        }
                    } else {
                        return creatERROR(0, "Invalid block number");
                    }
                }
            }
        }
        return null;
    }
    private byte[] ERRORcase(byte[] message) {
        short TheErrorCode = fromBytesToShort(Arrays.copyOfRange(message,2,4));
        String errorMessage = getERROR(message);
        System.out.println("Error received: Code " + TheErrorCode + ", Message: " + errorMessage);
        shouldTerminate = true;
        return null;
    }

    private String getERROR(byte[] message) {
        int index = 4;
        while (message[index] != 0) {
            index++;
        }
        return new String(message,4, index-4, StandardCharsets.UTF_8);
    }

    private byte[] DIRQcase() {
        if (checkIfLogedIn()) {
            File directory = new File("Files");
            File[] files= directory.listFiles();
            StringBuilder directoryListing = new StringBuilder();
    
            if (files != null) {
                for (File f: files) {
                    if (f.isFile()) {
                        directoryListing.append(f.getName()).append('\0');
                    }
                }
            }
    
            byte[] data = directoryListing.toString().getBytes(StandardCharsets.UTF_8);
            sendDATA("", (short) 1, data);
            blockNumbers.put("", (short) 1);
        } 
        return null;
    }

    private byte[] LOGRQcase(byte[] message) {
        System.out.println("trying to log in");
        if (username != null) {
            System.out.println("already logged in");
            return creatERROR(7, "User already logged in");
        } else {
            System.out.println("you can log");
            username =getUserName(message);
            loggedInUsers.put(connectionId, username);
            sendACK("", (short) 0);
        }
        return null;
    }

    private String getUserName(byte[] message) {
        int index = 2;
        while (message[index] != 0) {
            index++;
        }
        return new String(message, 2, index - 2, StandardCharsets.UTF_8);
    }

    private byte[] DELRQcase(byte[] message) {
        if (checkIfLogedIn()) {
            String filename = getFilename(message);
            String filePath = "Files/" + filename;
            File file = new File(filePath);
            if (file.exists()) {
                //close the file input stream and remove it from the map if it exists
                FileInputStream fileInputStream = fileInputStreams.get(filename);
                if (fileInputStream != null) {
                    try {
                        fileInputStream.close();
                    } catch (IOException e) {
                        //handle the exception appropriately
                        return creatERROR(0, "Error closing file input stream");
                    }
                    fileInputStreams.remove(filename);
                }
                //close the file output stream and remove it from the map if it exists
                FileOutputStream fileOutputStream =fileOutputStreams.get(filename);
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        //handle the exception appropriately 
                        return creatERROR(0, "Error closing file output stream");
                    }
                    fileOutputStreams.remove(filename);
                }
                //remove the block number associated with the file
                blockNumbers.remove(filename);
                if (file.delete()) {
                    sendACK("", (short) 0);
                    sendRIRQ(0, filename);
                } else {
                    return creatERROR(0, "Failed to delete the file");
                }
            } else {
                return creatERROR(1, "File not found");
            }
        }
        return null;
    }

    private byte[] DISCcase(){
        if (checkIfLogedIn()) {
        loggedInUsers.remove(connectionId);
            username = null;
            sendACK("", (short) 0);
            shouldTerminate = true;
        }
        return null;
    }

    private boolean checkIfLogedIn(){
        if (username == null) {
              sendEROOR(6, "User not logged in");
            return false;
        }
        return true;
    }

    private void sendACK(String filename, short blockNumber) {
        byte[] ackPacket =creatACK(blockNumber);
        connections.send(connectionId, ackPacket);
    }



    private void sendDATA(String filename, short blockNumber, byte[] data) {
        System.out.println("the data here  "+data);
        byte[] dataPacket = createDATA(blockNumber, data);
        System.out.println("data packet "+ Arrays.toString(dataPacket));
        connections.send(connectionId,dataPacket);
    }
    
    private byte[] creatACK(short blockNumber) {
        byte[] packet = new byte[4];
        packet[0]=0;
        packet[1]=ACK;
        packet[2]=(byte)((blockNumber >> 8) & 0xFF);
        packet[3]=(byte)(blockNumber& 0xFF);
        return packet;
    }

    private byte[] createDATA(short blockNumber, byte[] data) {
        int packetSize = data.length ; //opcode (2 bytes) + Block number (2 bytes)
        byte[] packet = new byte[packetSize+6 ]; //additional 2 bytes for packet size
        //opcode
        packet[0]=0;
        packet[1]=DATA;

        //packet size
        short packetSizeShort=(short) data.length;
        System.out.println("maybe  " + packetSizeShort);
        packet[2]=(byte)((packetSizeShort >> 8) & 0xFF);
        packet[3]=(byte)(packetSizeShort & 0xFF);

        //block number
        packet[4]=(byte)((blockNumber >>8) & 0xFF);
        packet[5]=(byte)(blockNumber & 0xFF);

        //data
        System.arraycopy(data,0,packet, 6,data.length);

        System.out.println("Packet created: " + packet.length + " bytes");
        return packet;
    }

    private void sendEROOR(int TheErrorCode,String errorMessage) {
        byte[] errorPacket=creatERROR(TheErrorCode, errorMessage);
        connections.send(connectionId,errorPacket);
    }

    private void sendRIRQ(int addedDeleted, String filename) {
        byte[] bcastPacket=creatBCAST(addedDeleted, filename);
        connections.broadcast(bcastPacket);
    }

   

    private byte[] creatERROR(int TheErrorCode, String errorMessage) {
        byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[errorMessageBytes.length + 5];
        packet[0]=0;
        packet[1]=5;
        packet[2]=(byte)((TheErrorCode >> 8) & 0xFF);
        packet[3]=(byte) (TheErrorCode & 0xFF);
        System.arraycopy(errorMessageBytes, 0, packet,4, errorMessageBytes.length);
        packet[packet.length-1] =0;
        return packet;
    }

    private byte[] creatBCAST(int addedDeleted, String filename) {
        byte[] filenameBytes=filename.getBytes(StandardCharsets.UTF_8);
        byte[] packet=new byte[filenameBytes.length + 4];
        packet[0]=0;
        packet[1]=9;
        packet[2]=(byte) addedDeleted;
        System.arraycopy(filenameBytes,0, packet,3, filenameBytes.length);
        packet[packet.length-1]= 0;
        return packet;
    }


    private String getFilename(byte[] message) {
        int index= 2;
        while (message[index]!= 0) {
            index++;
        }
        return new String(message,2, index-2, StandardCharsets.UTF_8);
    }

    private short fromBytesToShort(byte[] bytes) {
        return (short) ((bytes[0] & 0xff) << 8 | (bytes[1] & 0xff));
    }
}
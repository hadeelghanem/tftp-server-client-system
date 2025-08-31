package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;


import java.io.BufferedReader;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.File;

import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.FileInputStream;

import java.io.FileOutputStream;
import java.io.IOException;

import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;


import java.nio.file.Paths;




public class TftpClient {
    private static final int RRQ=1;
    private static final int WRQ=2;
    private static final int DATA=3;
    private static final int ACK=4;
    private static final int ERROR=5;
    private static final int DIRQ =6;
    private static final int LOGRQ=7;
    private static final int DELRQ=8;
    private static final int BCAST=9; //IMPORTANT!!!!
    private static final int DISC=10;

    public static void main(String[] args) {
    if (args.length< 2) {
        System.out.println("Usage: java TftpClient <host> <port>");
        System.exit(1);
    }

    String host = args[0];
    int port = Integer.valueOf(args[1]);//"7777"

    try (Socket socket = new Socket(host, port);
         BufferedInputStream bufin = new BufferedInputStream(socket.getInputStream());
         BufferedOutputStream bufuot = new BufferedOutputStream(socket.getOutputStream())) {

        Connections Clientonnection = new Connections(bufin, bufuot);
        System.out.println("Connected to server!");

        Thread listenerThread = new Thread(() -> {
            try {
                int bytes= bufin.read();
                while (Clientonnection.shouldTerminate==false && bytes!= -1) {
                    byte[] response = Clientonnection.encdec.decodeNextByte((byte) bytes);
                    if (response != null) {
                        Start(response, Clientonnection);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Thread keyboardThread = new Thread(() -> {
            try (BufferedReader keyboardInput = new BufferedReader(new InputStreamReader(System.in))) {
                String command;
                while (!Clientonnection.shouldTerminate && (command = keyboardInput.readLine()) != null) {
                    if (Clientonnection.IsWaiting) {
                        System.out.println("The client is curruntly waiting");
                        continue;
                    }
                    if (returnIfVal(command)) {
                        String[] commandParts = command.split(" ", 2);
                        HelpFuncForCom(commandParts, Clientonnection);
                    } else {
                        System.out.println("Invalid command");
                    }
                }
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        });

        listenerThread.start();


        keyboardThread.start();

        try {
            listenerThread.join();


            keyboardThread.join();
        } catch (InterruptedException e) {}

        System.out.println("Client is now disconnected!");
    } catch (IOException e) {
        System.err.println("Error: " + e.getMessage());
    }
}




      public static void Start(byte[] ans, Connections Client) {
        short opCode = (short) (((short) ans[0] & 0xff) << 8 | (short) (ans[1] & 0xff));
    
        switch (opCode) {
            case DATA:
                dataCase(ans, Client);
                break;
    
            case ACK:
                ACKcase(ans, Client);
                break;
    
            case ERROR:
                ERRORcase(ans, Client);
                break;
    
            case BCAST:
                BCASTcase(ans, Client);
                break;
        }
    }


  public static void BCASTcase(byte[] ans, Connections Client){
    String ADDorDEL;
    if (ans[2] == (byte) 1) {

    ADDorDEL = "add";
    } else {
    ADDorDEL = "del";
    }
     String filename = new String(Arrays.copyOfRange(ans, 3, ans.length), StandardCharsets.UTF_8);
     String output= "BCAST: " + ADDorDEL + " " + filename;
      System.out.println(output);
  }

  public static void ERRORcase(byte[] ans, Connections Client){
    String message = new String(Arrays.copyOfRange(ans, 4, ans.length), StandardCharsets.UTF_8);

      short errorCode = (short) (((short) ans[2] & 0xff) << 8 | (short) (ans[3] & 0xff));
      String output="Error " + errorCode + ": " + message;
      System.err.println(output);


      Clean(Client.ConQueue1, Client.ConQueue2, Client.IsWaiting, Client.opCode);

      Client.IsWaiting=false;
      
  }

  public static void Clean(ConcurrentLinkedQueue<byte[]> ConQueue1, ConcurrentLinkedQueue<byte[]> ConQueue2, boolean IsWaiting, int opCode){
    ConQueue1.clear();

      ConQueue2.clear();

      IsWaiting = false;
      opCode = 0;
  }

  public static void HelpFuncForCom(String[] cmd, Connections Client) {
    byte[] arr1;
    byte[] arr2;

    switch (cmd[0]) {
        case "LOGRQ":
            arr1 = new byte[]{0, 7};
            arr2 = (cmd[1] + "\0").getBytes();
            sendRequest(Client, arr1, arr2, 7);
            break;
        case "RRQ":
        System.out.println("got RRQ");
            arr1 = new byte[]{0, 1};
            arr2 = (cmd[1] + "\0").getBytes();
            Client.filename = cmd[1];
            sendRequest(Client, arr1, arr2, 1);
            break;
        case "WRQ":
        arr1 = new byte[]{0, 2};
        File file = new File(System.getProperty("user.dir"), cmd[1]);
        if (!file.exists()) {
            System.out.println("File doesn't exist");
            return;
        }
        arr2 = (cmd[1] + "\0").getBytes();
        Client.filename = cmd[1];
        sendRequest(Client, arr1, arr2, 2);
        break;
        case "DELRQ":
        System.out.println("got here ahd");
            arr1 = new byte[]{0, 8};
            arr2 = (cmd[1] + "\0").getBytes();
            sendRequest(Client, arr1, arr2, 8);
            break;
        case "DIRQ":
            arr1 = new byte[]{0, 6};
            sendRequest(Client, arr1, new byte[0], 6);
            break;
        case "DISC":
        System.out.println("got here hadeel");
            arr1= new byte[]{0, 10};
            sendRequest(Client, arr1, new byte[0], 10);
            Client.shouldTerminate = true;
            break;
        default:
            System.out.println("Invalid command");
            break;
    }
}

public static List<String> returnDIRQ() {
    File currentDir = new File(System.getProperty("user.dir"));
    List<String> files1 = new ArrayList<>();
    
    File[] filesList = currentDir.listFiles();
    if (filesList != null) {
        for (File file : filesList) {
            if (file.isFile()) {
                files1.add(file.getName());
            }
        }
    }

    return files1;
}


public static void dataCase(byte[] ans, Connections Client) {
    short blockNum = (short) (((short) ans[4] & 0xff) << 8 | (short) (ans[5] & 0xff));
    short dataLength = (short) (((short) ans[2] & 0xff) << 8 | (short) (ans[3] & 0xff));

    if (Client.opCode == DIRQ) {
        if (ans.length > 6) {
            byte[] data = Arrays.copyOfRange(ans, 6, ans.length);
            Client.ConQueue1.add(data);

        }
        if (dataLength <512) {
            List<String> files = returnFiles(Client.ConQueue1);
            for (String fileName : files) {
                System.out.println(fileName);
            }
            
        }
        Clean2(Client.IsWaiting, Client.opCode, Client.ConQueue1);
        Client.IsWaiting=false;
    } else {
        if (blockNum != Client.ConQueue1.size() + 1) {

            byte[] error = ReturnErr((short) 0, "Invalid block number");
            try {
                Client.Output.write(error);
                Client.Output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Clean3(Client.IsWaiting, Client.ConQueue1);
            return;
        }
        if (ans.length > 6) {

            byte[] subArray = new byte[ans.length - 6];
            System.arraycopy(ans, 6, subArray, 0, subArray.length);
            Client.ConQueue1.add(subArray);
        }
        if (dataLength < 512) {

            if (Client.opCode == RRQ) {

                Path filePath = Paths.get(System.getProperty("user.dir"), Client.filename);

                File file = filePath.toFile();

                Client.IsWaiting = false;



                Client.opCode = 0;
                
                if (file.exists()) {
                    file.delete();
                }
                try {
                    file.createNewFile();
                    FileOutputStream inpt = new FileOutputStream(file);



                    while (!Client.ConQueue1.isEmpty()) {
                        inpt.write(Client.ConQueue1.remove());

                    }
                    Clean4(Client.IsWaiting, Client.opCode);
                    System.out.println("file complete");
                    inpt.close();

                } catch (IOException e) {
                    e.printStackTrace();


                    Clean2(Client.IsWaiting, Client.opCode, Client.ConQueue1);
                }
            }
        }
    }

    byte[] ACKarr = new byte[4];
    ACKarr[0]= 0;

ACKarr[1]= 4;
    ACKarr[2] = ans[4];

    ACKarr[3] = ans[5];
    try {
        Client.Output.write(Client.encdec.encode(ACKarr));
        Client.Output.flush();
    } catch (IOException e) {
        e.printStackTrace();
    }
}

  public static void Clean2(boolean IsWaiting, short opCode, ConcurrentLinkedQueue<byte[]> ConQueue1){
    IsWaiting = false;

          opCode = 0;

        ConQueue1.clear();
  }
  
  public static void Clean3(boolean IsWaiting, ConcurrentLinkedQueue<byte[]> ConQueue1){

    IsWaiting = false;

        ConQueue1.clear();
  }
  public static void Clean4(boolean IsWaiting, short opCode){
    IsWaiting = false;
    
          opCode = 0;
  }




  public static void ACKcase(byte[] ans, Connections Client) {
    
    short blockNum = (short) (((short) ans[2] & 0xff) << 8 | (short) (ans[3] & 0xff));

    if (Client.opCode == LOGRQ || Client.opCode == DELRQ) {
        if (blockNum == 0) {
            System.out.println("ACK 0");
             
        }
        Client.IsWaiting = false;
    } else if (Client.opCode == RRQ) {

        System.out.println("ACK " + blockNum);
        if (blockNum == Client.ConQueue1.size()) {
            Client.IsWaiting = false; 

        }
    } else if (Client.opCode == DISC) {
        
        if (blockNum == 0) {
            System.out.println("ACK 0");
            Client.IsWaiting = false;
            Client.shouldTerminate = true;

        }
    } else if(Client.opCode==   WRQ){
        if (blockNum == 0) {
            sendDataPackets(Client);

            Client.IsWaiting = false; 
        } else {

            sendDataPackets(Client);
            Client.IsWaiting = false; 
        }

    }
}


public static void sendDataPackets(Connections Client) {
    File file = new File(System.getProperty("user.dir"), Client.filename);
    try {
        FileInputStream fileInput = new FileInputStream(file);
        byte[] buffer =new byte[512];
        int bytesRead;
        short blockNum =1;

        while ((bytesRead = fileInput.read(buffer))!= -1) {
            byte[] dataPacket = createDataPacket(blockNum, Arrays.copyOfRange(buffer, 0, bytesRead));
            Client.Output.write(Client.encdec.encode(dataPacket));
            Client.Output.flush();
            blockNum++;
        }
        fileInput.close();
        System.out.println("File sent");

        Client.IsWaiting = false;
        Client.opCode = 0;
    } catch (IOException e) {
        e.printStackTrace();
        ReturnErr((short) 0, "Error reading the file");
        Client.IsWaiting = false;
        Client.opCode = 0;
    }
}

public static byte[] ReturnErr(short opCode, String msg) {
    byte[] error = new byte[2];

    error[0]=(byte) (opCode >> 8);
    error[1]=(byte) (opCode & 0xff);

    byte[] errorCode = new byte[2];
    errorCode[0]=0;
    errorCode[1]=5;

    byte[] starterr = Arrays.copyOf(errorCode, errorCode.length + error.length);
    System.arraycopy(error, 0, starterr, errorCode.length, error.length);

byte[] messg = Arrays.copyOf(msg.getBytes(), msg.length() + 1);

messg[msg.length()] = 0;

    return combine(starterr, messg);
  }

  public static byte[] combine(byte[] comb, byte[] combi) {
    int totalLength = comb.length + combi.length;

    byte[] combinedArray = new byte[totalLength];
    int index = 0;
        for (int i = 0; i < comb.length; i++) {
        combinedArray[index++] = comb[i];
    }
        for (int i = 0; i < combi.length; i++) {
        combinedArray[index++] = combi[i];
    }
    
    return combinedArray;
  }

public static byte[] createDataPacket(short blockNum, byte[] data) {
    byte[] packet = new byte[data.length + 6];

    packet[0]= 0;
    packet[1] =3;

    packet[2] =(byte) (data.length >> 8);
 packet[3] = (byte) data.length;
    packet[4] = (byte) (blockNum >> 8);

    packet[5] = (byte) blockNum;
    System.arraycopy(data, 0, packet, 6, data.length);
    return packet;
}







public static List<String> returnFiles(ConcurrentLinkedQueue<byte[]> bytesQueue) {
    List<String> files = new ArrayList<>();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    for (byte[] byteArray : bytesQueue) {
        if (byteArray != null) {
            outputStream.write(byteArray, 0, byteArray.length);
        }
    }

    byte[] combinedArray = outputStream.toByteArray();
    String combinedString = new String(combinedArray, StandardCharsets.UTF_8);
    String[] fileNames = combinedString.split("\0");

    for (String fileName : fileNames) {
        if (!fileName.isEmpty()) {
            files.add(fileName);
        }
    }

    return files;
}

public static boolean returnIfVal(String cmd) {
    String[] parts = cmd.split(" ", 2);
    String command = parts[0].toUpperCase();

    switch (command) {
        case "DIRQ":
        case "DISC":
            return parts.length == 1;
        case "LOGRQ":
        case "RRQ":
        System.out.println("got here");
        case "WRQ":
        System.out.println("got here2");
        case "DELRQ":
            return parts.length == 2 && !parts[1].isEmpty();
        default:
            return false;
    }
}





private static void sendRequest(Connections Client, byte[] start, byte[] arr2, int opCode) {
    try {
        Client.opCode = (short)opCode;
        Client.IsWaiting = true;
        Client.Output.write(Client.encdec.encode(combine(start, arr2)));
        Client.Output.flush();
    } catch (IOException e) {
        Client.opCode = 0;

        
        Client.IsWaiting = false;
        if (opCode == 1) {
            Client.filename = "";
            Client.TheCount = 0;
        } else if (opCode == 2) {
            Client.filename = "";
        }
        e.printStackTrace();
    }
}



}
package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;


public class Connections {

    public int TheCount=0;
    public short opCode=0;

    public String filename="";

  public boolean shouldTerminate=false;
  public boolean IsWaiting=false;


  TftpEncoderDecoder encdec= new TftpEncoderDecoder();
  
  BufferedInputStream Input;
  BufferedOutputStream Output;

  public ConcurrentLinkedQueue<byte[]> ConQueue1;
  public ConcurrentLinkedQueue<byte[]> ConQueue2;

  public Connections(BufferedInputStream inBuff,BufferedOutputStream outBuff) {
    ConQueue2 = new ConcurrentLinkedQueue<>();
    ConQueue1=  new ConcurrentLinkedQueue<>();
    Input = inBuff;
    Output = outBuff;
  }
}
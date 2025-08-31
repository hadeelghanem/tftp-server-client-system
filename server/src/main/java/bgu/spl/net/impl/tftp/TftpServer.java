package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;

public class TftpServer {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Please provide a port number as a command line argument.");
            return;
        }
        int port = Integer.parseInt(args[0]);
        //create a ConnectionsImpl to handle the client connection
        ConnectionsImpl<byte[]> connections = new ConnectionsImpl<>();
        //create new of BaseServer using the port and factories that we have
        BaseServer<byte[]> server = new BaseServer<byte[]>(port,() -> new TftpProtocol(connections),
        () -> new TftpEncoderDecoder(),() -> connections) 
        {
            private int nextIDofTheConnections=0;
            @Override
            protected void execute(BlockingConnectionHandler<byte[]> handler) {
                int theIDofTheConnections=nextIDofTheConnections++;
                handler.setConnectionId(theIDofTheConnections);
                //add the new connection to the ConnectionsImpl
                connections.connect(handler.getConnectionId(), handler);
                //start a new thread to handle the client connection
                new Thread(handler).start();
            }
        };
        //print a message when the server starts listening
        System.out.println("TFTP Server started on port " + port);
     
        server.serve();       //start the server
    }
    }

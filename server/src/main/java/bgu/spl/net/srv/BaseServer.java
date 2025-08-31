package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.BidiMessagingProtocol;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

public abstract class BaseServer<T> implements Server<T> {
    private final int port;
    private final Supplier<BidiMessagingProtocol<T>> protocolFactory;
    private final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    private final Supplier<Connections<T>> connectionsFactory;
    private ServerSocket sock;

    public BaseServer(
            int port,
            Supplier<BidiMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encdecFactory,
            Supplier<Connections<T>> connectionsFactory
    ) {
        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
        this.connectionsFactory = connectionsFactory;
        this.sock = null;
    }

    @Override
    public void serve() {
        try (ServerSocket serverSock = new ServerSocket(port)) {
            System.out.println("Server started");
            this.sock = serverSock;
            Connections<T> connections = connectionsFactory.get();

            while (!Thread.currentThread().isInterrupted()) {

            
                Socket clientSock = serverSock.accept();
                BidiMessagingProtocol<T> protocol = protocolFactory.get();
                BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<>(
                        clientSock,
                        encdecFactory.get(),
                        protocol
            
                );
                int connectionId = connections.addNewConnection(handler);
                protocol.start(connectionId, connections);
                execute(handler);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        System.out.println("Server closed!!!");
    }

    @Override
    public void close() throws IOException {
        if (sock != null)
            sock.close();
    }

    protected abstract void execute(BlockingConnectionHandler<T> handler);
}
package bgu.spl.net.impl.tftp;



import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionsImpl<T> implements Connections<byte[]> {
    private ConcurrentHashMap<Integer, BlockingConnectionHandler<byte[]>> activeConnections;
    private AtomicInteger idCounter;

    public ConnectionsImpl() {
        this.activeConnections = new ConcurrentHashMap<Integer, BlockingConnectionHandler<byte[]>>();
        this.idCounter = new AtomicInteger(0);
    }

    @Override
    public boolean send(int connectionId, byte[] msg) {
        System.out.println("I am here");
        BlockingConnectionHandler<byte[]> handler = activeConnections.get(connectionId);
        if (handler != null) {
          System.out.println("hadeel");
        
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void broadcast(byte[] msg) {
        for (BlockingConnectionHandler<byte[]> handler : activeConnections.values()) {
            handler.send(msg);
        }

    }

    @Override
    public void disconnect(int connectionId) {
        BlockingConnectionHandler<byte[]> handler = activeConnections.remove(connectionId);
        if (handler != null) {
            try {
                handler.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void connect(int connectionId, BlockingConnectionHandler<byte[]> handler) {
        activeConnections.put(connectionId, handler);
    }

    public ConcurrentHashMap<Integer, BlockingConnectionHandler<byte[]>> getActiveConnections() {
        return activeConnections;
    }

    public int addNewConnection(BlockingConnectionHandler<byte[]> handler) {
        int connectionId = idCounter.getAndIncrement();
        activeConnections.put(connectionId, handler);
        return connectionId;
    }
}
package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;

public interface BidiMessagingProtocol<T>  {
	/**
	 * Used to initiate the current client protocol with it's personal connection ID and the connections implementation
	**/
    void start(int connectionId, Connections<T> connections);
    
    T process(T message);   // HERE  WAS byte[] not T before process 
	
	/**
     * @return true if the connection should be terminated
     */
    boolean shouldTerminate();
}

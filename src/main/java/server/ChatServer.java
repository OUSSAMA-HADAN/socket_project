package server;


import java.io.IOException;
import java.net.*;
public class ChatServer {
    private ServerSocket skt_obj= null;

    //constructor with port :
    public ChatServer(int port) throws IOException {


        skt_obj= new ServerSocket(port);
        System.out.println("server started");
        System.out.println("Waiting for a client ...");
        Socket clientSocket = skt_obj.accept();
        String client_ip=clientSocket.getInetAddress().getHostAddress();
        System.out.println("client connected with ip: "+ client_ip);


    }
    public static void main(String args[]) throws IOException {
        ChatServer s = new ChatServer(5000);
    }

}

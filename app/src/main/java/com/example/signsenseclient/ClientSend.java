package com.example.signsenseclient;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class ClientSend implements Runnable {
    int port = 6071;
    InetAddress serverAddr;
    DatagramSocket udpSocket;
    @Override
    public void run() {
        try {
            Log.d("Networking","OPENING SOCKET!!!!!!!!");
            udpSocket = new DatagramSocket(port);
            serverAddr = InetAddress.getByName("159.89.120.165");
            byte[] buf = ("INIT").getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length,serverAddr, port);
            udpSocket.send(packet);
        } catch (SocketException e) {
            Log.e("Udp:", "Socket Error:", e);
        } catch (IOException e) {
            Log.e("Udp Send:", "IO Error:", e);
        }
    }

    public void sendData(String data){
        byte[] buf = data.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length,serverAddr, port);
        try {
            if(udpSocket != null) {
                Log.d("Networking", "Sending Packet");
                udpSocket.send(packet);
            }
        } catch (IOException e) {
            Log.e("Udp Send:", "IO Error:", e);
        }
    }
}

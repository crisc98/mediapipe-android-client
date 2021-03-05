package com.example.signsenseclient;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.Deque;

import static android.content.ContentValues.TAG;

public class ClientSend implements Runnable {
    private boolean exitFlag = false;
    int port = 6071;
    InetAddress serverAddr;
    DatagramSocket udpSocket;
    Deque<String> messageQueue = new ArrayDeque<>();
    @Override
    public void run() {
        initOutgoing();
        while(true) {
            if(!messageQueue.isEmpty())
                if(exitFlag){
                    break;
                }
                else {
                    sendData(messageQueue.pop());
                }
        }
    }

    private void initOutgoing(){
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

    private void sendData(String data){
        try {
            StandardCryptoUtilAES.encrypt(data);
        }
        catch (Exception e){
            Log.e("Networking", e.getMessage());
        }
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

    public void setExitFlag(boolean value) {
        exitFlag = value;
    }

    public void addToQueue(String data) {
        messageQueue.addLast(data);
    }
}

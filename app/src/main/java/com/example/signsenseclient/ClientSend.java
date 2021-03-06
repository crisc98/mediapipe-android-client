package com.example.signsenseclient;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayDeque;
import java.util.Deque;

public class ClientSend implements Runnable {
    private boolean exitFlag = false;
    int port = 6071;
    InetAddress serverAddr;
    DatagramSocket udpSocket;
    Deque<String> messageQueue = new ArrayDeque<>();

    @Override
    public void run() {
        init();
        while(true) {
            if(!messageQueue.isEmpty()) {
                if (exitFlag) {
                    break;
                } else {
                    sendData(messageQueue.pop());
                }
            }
        }
    }

    private void init(){
        try {
            Log.d("Networking","OPENING SOCKET!!!!!!!!");
            udpSocket = new DatagramSocket(port);
            serverAddr = InetAddress.getByName("159.89.120.165");
            byte[] buf = ("INIT").getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length,serverAddr, port);
            udpSocket.send(packet);

            byte[] recvBuf = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
            new Thread(() -> {
                try {
                    udpSocket.receive(recvPacket);
                    String str = new String(recvPacket.getData(), 0, recvPacket.getLength());
                    Log.d("Networking:", str);
                }
                catch (IOException e) {
                    Log.e("Networking:", "IO Error:", e);
                }
            }).start();
        } catch (SocketException e) {
            Log.e("Networking:", "Socket Error:", e);
        } catch (IOException e) {
            Log.e("Networking:", "IO Error:", e);
        }
    }

    private void sendData(String data){
//        try {
//            StandardCryptoUtilAES.encrypt(data);
//        }
//        catch (Exception e){
//            Log.e("Networking", e.getMessage());
//        }
        byte[] buf = data.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length,serverAddr, port);
        try {
            if(udpSocket != null) {
                Log.d("Networking", "Sending Packet");
                udpSocket.send(packet);
            }
        } catch (IOException e) {
            Log.e("Networking:", "IO Error:", e);
        }
    }

    public void setExitFlag(boolean value) {
        exitFlag = value;
    }

    public void addToQueue(String data) {
        messageQueue.addLast(data);
    }
}
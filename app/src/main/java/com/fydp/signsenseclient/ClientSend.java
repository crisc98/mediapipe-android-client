package com.fydp.signsenseclient;

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
    int sendPort = 9999;
    int recvPort = 9998;
    InetAddress serverAddr;
    DatagramSocket udpSendSocket, updRecvSocket;
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
            udpSendSocket = new DatagramSocket(sendPort);
            updRecvSocket = new DatagramSocket(recvPort);
            serverAddr = InetAddress.getByName("99.199.188.34");
            byte[] buf = ("INIT").getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length,serverAddr, sendPort);
            udpSendSocket.send(packet);

            byte[] recvBuf = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
            new Thread(() -> {
                try {
                    while (true) {
                        if (exitFlag) break;
                        updRecvSocket.receive(recvPacket);
                        String str = new String(CryptoChaCha20.decrypt(recvPacket.getData()), 0, recvPacket.getLength()-CryptoChaCha20.NONCE_LEN);
                        Log.d("Networking:", "Receiving Packet!!!");
                        Log.d("Networking", str);
                    }
                }
                catch (IOException e) {
                    Log.e("Networking:", "IO Error:", e);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (SocketException e) {
            Log.e("Networking:", "Socket Error:", e);
        } catch (IOException e) {
            Log.e("Networking:", "IO Error:", e);
        }
    }

    private void sendData(String data){
        try {
            byte[] buf = CryptoChaCha20.encrypt(data.getBytes());
            DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, sendPort);

            if(udpSendSocket != null) {
                //Log.d("Networking", "Sending Packet");
                //Log.d("Networking", buf.toString());
                try {
                    //Log.d("Networking", new String(CryptoChaCha20.decrypt(buf)));
                }
                catch (Exception e) {
                    Log.e("Networking", e.getMessage());
                }

                udpSendSocket.send(packet);
            }
        } catch (Exception e) {
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
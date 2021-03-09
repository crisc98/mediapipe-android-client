package com.fydp.signsenseclient;

import android.content.Context;
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
    private final int SEND_PORT = 9999;
    private final int RECV_PORT = 9998;
    private Context context;
    InetAddress serverAddr;
    DatagramSocket udpSendSocket, updRecvSocket;
    Deque<String> messageQueue = new ArrayDeque<>();

    public ClientSend(Context context){
        this.context = context;
    }

    @Override
    public void run() {
        init();
        while(!exitFlag) {
            if(!messageQueue.isEmpty()) {
                sendData(messageQueue.pop());
            }
        }
    }

    private void init(){
        try {
            Log.d("Networking","OPENING SOCKET!!!!!!!!");
            udpSendSocket = new DatagramSocket(SEND_PORT);
            updRecvSocket = new DatagramSocket(RECV_PORT);
            serverAddr = InetAddress.getByName("99.199.188.34");
            byte[] buf = ("INIT").getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length,serverAddr, SEND_PORT);
            udpSendSocket.send(packet);

            byte[] recvBuf = new byte[1024];
            DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
            new Thread(() -> {
                try {
                    while (!exitFlag) {
                        updRecvSocket.receive(recvPacket);
                        String str = new String(CryptoChaCha20.decrypt(recvPacket.getData()), 0, recvPacket.getLength() - CryptoChaCha20.NONCE_LEN);
                        Log.d("Networking:", "Receiving Packet!!!");
                        Log.d("Networking", str);
                        StreamActivity act = (StreamActivity) context;
                        act.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                act.setPredictionLabelText(str);
                            }
                        });
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
            DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, SEND_PORT);

            if(udpSendSocket != null) {
                Log.d("Networking", "Sending Packet");
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
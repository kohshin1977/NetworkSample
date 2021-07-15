package com.kohshin.networksample;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class GuestActivity extends AppCompatActivity {

    private TextView mTextView;

    boolean waiting;
    int udpPort = 9999;//ホスト、ゲストで統一

    WifiManager wifi;

    ServerSocket serverSocket;
    Socket connectedSocket;
    int tcpPort = 3333;//ホスト、ゲストで統一

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guest);

        mTextView = (TextView) findViewById(R.id.text);


    }

    //同一Wi-fiに接続している全端末に対してブロードキャスト送信を行う
    void sendBroadcast(){
        final String myIpAddress = getIpAddress();
        waiting = true;
        new Thread() {
            @Override
            public void run() {
                int count = 0;
                //送信回数を10回に制限する
                while (count < 10) {
                    try {
                        DatagramSocket udpSocket = new DatagramSocket(udpPort);
                        udpSocket.setBroadcast(true);
                        DatagramPacket packet = new DatagramPacket(myIpAddress.getBytes(), myIpAddress.length(), getBroadcastAddress(), udpPort);
                        udpSocket.send(packet);
                        udpSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //5秒待って再送信を行う
                    try {
                        Thread.sleep(5000);
                        count++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    String getIpAddress(){

        String ipAddress;

        ipAddress = getChromeOsIpAddress();     //Chromebook用のコンテナではなく物理のIPアドレスを取得する処理

        if(ipAddress.equals("")){       //取得できなければ通常の取得処理を実施。
            wifi = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo w_info = wifi.getConnectionInfo();
            int ipAddress_int = wifi.getConnectionInfo().getIpAddress();
            if(ipAddress_int == 0){
                ipAddress = null;
            }else {
                ipAddress = (ipAddress_int & 0xFF) + "." + (ipAddress_int >> 8 & 0xFF) + "." + (ipAddress_int >> 16 & 0xFF) + "." + (ipAddress_int >> 24 & 0xFF);
            }
        }

        return ipAddress;
    }

    public String getChromeOsIpAddress(){
        Process process= null;
        try {
            process = new ProcessBuilder().command("/system/bin/getprop", "arc.net.ipv4.host_address").start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String ipAddress= readInput(process.getInputStream());
        return ipAddress;
    }

    public String readInput(InputStream inputStream) {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();

        String line = null;
        while (true) {
            try {
                line = bufferedReader.readLine();
                while(line != null){
//                    System.out.println(line);
                    break;
                }
                break;
//                if (!(bufferedReader.readLine().also({ line = it}) != null)) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            stringBuilder.append(line).append('\n');
        }

        try {
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return line;
    }

    //ブロードキャストアドレスの取得
    InetAddress getBroadcastAddress(){
        DhcpInfo dhcpInfo = wifi.getDhcpInfo();
        int broadcast = (dhcpInfo.ipAddress & dhcpInfo.netmask) | ~dhcpInfo.netmask;
        byte[] quads = new byte[4];
        for (int i = 0; i < 4; i++){
            quads[i] = (byte)((broadcast >> i * 8) & 0xFF);
        }
        try {
            return InetAddress.getByAddress(quads);
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

    //ホストからTCPでIPアドレスが返ってきたときに受け取るメソッド
    void receivedHostIp(){
        new Thread() {
            @Override
            public void run() {
                while (waiting) {
                    try {
                        if(serverSocket == null) {
                            serverSocket = new ServerSocket(tcpPort);
                        }
                        connectedSocket = serverSocket.accept();
                        //↓③で使用
                        inputDeviceNameAndIp(connectedSocket);
                        if (serverSocket != null) {
                            serverSocket.close();
                            serverSocket = null;
                        }
                        if (connectedSocket!= null) {
                            connectedSocket.close();
                            connectedSocket = null;
                        }
                    } catch (IOException e) {
                        waiting = false;
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    //端末名とIPアドレスのセットを受け取る
    void inputDeviceNameAndIp(Socket socket){
        try {
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            int infoCounter = 0;
            String remoteDeviceInfo;
            //ホスト端末情報(端末名とIPアドレス)を保持するためのクラスオブジェクト
            //※このクラスは別途作成しているもの
            SampleDevice hostDevice = new SampleDevice();
            while((remoteDeviceInfo = bufferedReader.readLine()) != null && !remoteDeviceInfo.equals("outputFinish")){
                switch(infoCounter){
                    case 0:
                        //1行目、端末名の格納
                        hostDevice.setDeviceName(remoteDeviceInfo);
                        infoCounter++;
                        break;
                    case 1:
                        //2行目、IPアドレスの取得
                        hostDevice.setDeviceIpAddress(remoteDeviceInfo);
                        infoCounter++;
                        return;
                    default:
                        return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //IPアドレスが判明したホストに対して接続を行う
    void connect(String remoteIpAddress){
        waiting = false;
        new Thread() {
            @Override
            public void run() {
                try{
                    if(connectedSocket == null) {
                        connectedSocket = new Socket(remoteIpAddress, tcpPort);
                        //この後はホストに対してInputStreamやOutputStreamを用いて入出力を行ったりするが、ここでは割愛
                    }
                }catch(UnknownHostException e){
                    e.printStackTrace();
                }catch (ConnectException e){
                    e.printStackTrace();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

}
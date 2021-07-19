package com.kohshin.networksample;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.os.Build;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class HostActivity extends AppCompatActivity {

    Button mStartButton;
    private EditText mEditText;

    DatagramSocket receiveUdpSocket;
    boolean waiting;
    int udpPort = 9999;//ホスト、ゲストで統一

    ServerSocket serverSocket;
    Socket connectedSocket;
    int tcpPort = 3333;//ホスト、ゲストで統一

    Socket returnSocket;

    // メイン(UI)スレッドでHandlerのインスタンスを生成する
    final Handler handler = new Handler();

    LinearLayout linearLayout;
    ArrayAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        mStartButton = (Button)findViewById(R.id.host_start_button);
        mEditText = (EditText)findViewById(R.id.editTextTextMultiLineHost);

        mStartButton.setOnClickListener(new StartButtonListener());


    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        if(receiveUdpSocket != null) {
            receiveUdpSocket.close();
            mEditText.append("receiveUdpSocket クローズ。" + "\n");
        }

        if(returnSocket != null){
            try {
                returnSocket.close();
                mEditText.append("returnSocket クローズ。" + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(serverSocket != null) {
            try {
                serverSocket.close();
                mEditText.append("serverSocket クローズ。" + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(connectedSocket != null){
            try {
                connectedSocket.close();
                mEditText.append("connectedSocket クローズ。" + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }


    //ブロードキャスト受信用ソケットの生成
    //ブロードキャスト受信待ち状態を作る
    void createReceiveUdpSocket() {
        waiting = true;
        new Thread() {
            @Override
            public void run(){
                String address = null;
                try {
                    // Handlerを使用してメイン(UI)スレッドに処理を依頼する
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mEditText.append("ブロードキャストの待ち受けを開始しました。\n");
                        }
                    });
                    //waiting = trueの間、ブロードキャストを受け取る
                    while(waiting){
                        //受信用ソケット
                        receiveUdpSocket = new DatagramSocket(udpPort);
                        byte[] buf = new byte[256];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        //ゲスト端末からのブロードキャストを受け取る
                        //受け取るまでは待ち状態になる
                        receiveUdpSocket.receive(packet);

                        //受信バイト数取得
                        int length = packet.getLength();
                        //受け取ったパケットを文字列にする
                        address = new String(buf, 0, length);
                        String finalAddress = address;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                mEditText.append("ゲストのIPアドレスを受信 : "+ finalAddress + "\n");
                            }
                        });
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                adapter.add("いいいいい");
                            }
                        });
                        //↓③で使用
                        returnIpAdress(address);
                        receiveUdpSocket.close();
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    //ゲストからの接続を待つ処理
    void connect(){
        new Thread(){
            @Override
            public void run(){
                try {
                    // Handlerを使用してメイン(UI)スレッドに処理を依頼する
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mEditText.append("TCPの待ち受けを開始しました。\n");
                        }
                    });
                    //ServerSocketを生成する
                    serverSocket = new ServerSocket(tcpPort);
                    //ゲストからの接続が完了するまで待って処理を進める
                    connectedSocket = serverSocket.accept();
                    //この後はconnectedSocketに対してInputStreamやOutputStreamを用いて入出力を行ったりするが、ここでは割愛
                }catch (SocketException e){
                    e.printStackTrace();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }


    //ブロードキャスト発信者(ゲスト)にIPアドレスと端末名を返す
    void returnIpAdress(final String address){
        new Thread() {
            @Override
            public void run() {
                try{
                    if(returnSocket != null){
                        returnSocket.close();
                        returnSocket = null;
                    }
                    if(returnSocket == null) {
                        returnSocket = new Socket(address, tcpPort);
                    }
                    //端末情報をゲストに送り返す
                    outputDeviceNameAndIp(returnSocket, getDeviceName(), getIpAddress());
                }catch(UnknownHostException e){
                    e.printStackTrace();
                }catch (java.net.ConnectException e){
                    e.printStackTrace();
                    try{
                        if(returnSocket != null) {
                            returnSocket.close();
                            returnSocket = null;
                        }
                    }catch(IOException e1) {
                        e.printStackTrace();
                    }
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        }.start();
    }

    String getDeviceName(){
        return android.os.Build.MODEL;
    }

    String getIpAddress(){

        String ipAddress;

        ipAddress = getChromeOsIpAddress();     //Chromebook用のコンテナではなく物理のIPアドレスを取得する処理

        if(ipAddress.equals("")){       //取得できなければ通常の取得処理を実施。
            WifiManager wifi;
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

    //端末名とIPアドレスのセットを送る
    void outputDeviceNameAndIp(final Socket outputSocket, final String deviceName, final String deviceAddress){
        new Thread(){
            @Override
            public void run(){
                final BufferedWriter bufferedWriter;
                try {
                    bufferedWriter = new BufferedWriter( new OutputStreamWriter(outputSocket.getOutputStream())
                    );
                    //デバイス名を書き込む
                    bufferedWriter.write(deviceName);
                    bufferedWriter.newLine();
                    //IPアドレスを書き込む
                    bufferedWriter.write(deviceAddress);
                    bufferedWriter.newLine();
                    //出力終了の文字列を書き込む
                    bufferedWriter.write("outputFinish");
                    //出力する
                    bufferedWriter.flush();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            mEditText.append("ホストのデバイス名を送信 : "+ deviceName + "\n");
                            mEditText.append("ホストのIPアドレスを送信 : "+ deviceAddress + "\n");
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private class StartButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            createReceiveUdpSocket();

            connect();

            showReceivingBroadcastFromGuestDialog();
        }
    }


    private void showReceivingBroadcastFromGuestDialog() {
        //Inflating a LinearLayout dynamically to add TextInputLayout
        //This will be added in AlertDialog
        linearLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.receiving_broadcast_from_guest_dialog, null);

        // ListViewに表示するリスト項目をArrayListで準備する
        ArrayList data = new ArrayList<>();
        data.add("国語");
        data.add("社会");
        data.add("算数");
        data.add("理科");

        // リスト項目とListViewを対応付けるArrayAdapterを用意する
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, data);

        // ListViewにArrayAdapterを設定する
        ListView listView = (ListView)linearLayout.findViewById(R.id.listView);
        listView.setAdapter(adapter);

        adapter.add("ああああ");

        //Finally building an AlertDialog
        final AlertDialog builder = new AlertDialog.Builder(this)
                .setTitle("受信待ち")
//                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", null)
                .setView(linearLayout)
                .setCancelable(false)
                .create();
        builder.show();
        //Setting up OnClickListener on positive button of AlertDialog
        builder.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // OKクリック時の処理
                builder.dismiss();
            }
        });
    }
}
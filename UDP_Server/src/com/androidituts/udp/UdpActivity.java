package com.androidituts.udp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import android.R.integer;
import android.app.Activity;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class UdpActivity extends Activity {
	// configure connection
	public static final String	SERVER_IP		= "10.0.2.2";	// 'Within' the emulator!
	public static final int		SERVER_PORT		= 5555;

	public int						packetSize		= 100;

	public TextView					outputTxt;
	public Handler					Handler;
	public Button					exitBtn;

	static final String			LOG_TAG			= "UDP_Server";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		outputTxt = (TextView) findViewById(R.id.textView1);
		exitBtn = (Button) findViewById(R.id.ext);

		// start server
		new Thread(new Server()).start();

		Handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				String text = (String) msg.obj;
				outputTxt.append(text);
			}
		};

		exitBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				finish();
				System.exit(0);
			}
		});
	}

	public class Server implements Runnable {
	    DatagramSocket	socket;
	    InetAddress		clientAddress;
	    int				clientPort;
	    DatagramPacket	packet;
	    String			fileName;

	    public Server() {
	        InetAddress serverAddress;
	        try {
	            serverAddress = InetAddress.getByName(SERVER_IP);
	            socket = new DatagramSocket(SERVER_PORT);
	            byte[] buf = new byte[packetSize];
	            packet = new DatagramPacket(buf, buf.length);
	            socket.setReuseAddress(true);
	        }
	        catch (UnknownHostException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	            Log.i(LOG_TAG, e.getMessage());
	        }
	        catch (SocketException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	            Log.i(LOG_TAG, e.getMessage());
	        }
	    }

	    @Override
	    public void run() {
	        try {
	        	//-------------------------------------------------------------------
				// (1) listen
				//-------------------------------------------------------------------
	        	updateTrack("listening ...\n");
	        	
	            socket.receive(packet);
	            
	            updateTrack("request received.\n");

	            clientAddress = packet.getAddress();
	            clientPort = packet.getPort();

	            final ByteArrayInputStream byteInputStream = new ByteArrayInputStream(packet.getData());
	            final DataInputStream dataInputStream = new DataInputStream(byteInputStream);
	            packetSize = dataInputStream.readInt();
	            int requestLen = dataInputStream.readInt();

	            byte[] dataBuffer = new byte[requestLen];
	            dataInputStream.read(dataBuffer);

	            fileName = new String(dataBuffer, 0, requestLen);

	            updateTrack("requested file: " + fileName + "\n");
	        }
	        catch (Exception e) {
	            updateTrack("Server: Error!\n");
	        }

	        try {
	            File audio = new File(Environment.getExternalStorageDirectory().getPath() + "/" + fileName);
	            FileInputStream audio_stream = new FileInputStream(audio);

	            long file_size = audio.length();
	            int bytes_read = 0;
	            int bytes_count = 0;

	            //-------------------------------------------------------------------
				// (2) send file size & ack
				//-------------------------------------------------------------------
	            final ByteArrayOutputStream ackBAOS = new ByteArrayOutputStream();
	            final DataOutputStream ackDAOS = new DataOutputStream(ackBAOS);
	            ackDAOS.writeLong(file_size);
	            ackDAOS.close();

	            updateTrack("requested file size: " + file_size + "\n");
	            updateTrack("sending file info ...\n");
	            
	            packet = new DatagramPacket(ackBAOS.toByteArray(), ackBAOS.size(), clientAddress, clientPort);
	            socket.send(packet);

	            //-------------------------------------------------------------------
				// (3) receive ack
				//-------------------------------------------------------------------
	            boolean ackTimeOut = false;
	            byte[] ackBuffer = null;
	            DatagramPacket ackPacket = null;
	            ByteArrayInputStream byteAckpacket = null;
	            DataInputStream dataAck = null;
	            int seqNum = -1;
	            
	            do {
	            	ackBuffer = new byte[packetSize];
					ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
					try {
						socket.setSoTimeout(100);
						socket.receive(ackPacket);

						byteAckpacket = new ByteArrayInputStream(ackPacket.getData());
						dataAck = new DataInputStream(byteAckpacket);
						seqNum = dataAck.readInt();
						ackTimeOut = false;
						updateTrack("file info ack received.\n");
					}
					catch (SocketTimeoutException e) {
						ackTimeOut = true;
						updateTrack("file info ack time outed.\n");
						socket.send(packet);
					}
	            }
	            while(ackTimeOut || seqNum != 1);

	            //-------------------------------------------------------------------
				// (4) send media file
				//-------------------------------------------------------------------
	            int seqNumber = 1;
	            int currentSeqNum = 1;
	            DatagramPacket receivedAckPacket = null;
	            DatagramPacket newPacket = null;
	            boolean timeOut = false;
	            ByteArrayInputStream byteInputStream = null;
				DataInputStream dataInputStream = null;
				
				// calculate total packets
				int temp = (int) (file_size / (packetSize - 4));
				int totalPackets = ((int) (file_size % (packetSize - 4)) == 0) ? (temp) : (++temp);
				
	            while (/*bytes_count < file_size*/ seqNumber <= totalPackets) {
	                byte[] buf = new byte[packetSize];
	                byte[] ackByte = null;
	                
	                if (!timeOut) {
	                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
	                    final DataOutputStream daos = new DataOutputStream(baos);
	                    daos.writeInt(seqNumber);
	                    if ((packetSize - 4) + bytes_count > file_size) {
	                        buf = new byte[(int) (file_size - bytes_count)];
	                        bytes_read = audio_stream.read(buf, 0, (int) (file_size - bytes_count));
	                    }
	                    else {
	                        bytes_read = audio_stream.read(buf, 0, 46);
	                    }
	                    daos.write(buf);
	                    daos.close();

	                    int msg_length = baos.size();
	                    byte[] message = baos.toByteArray();

	                    newPacket = new DatagramPacket(message, msg_length, clientAddress, clientPort);

	                    bytes_count += bytes_read;

	                    socket.send(newPacket);

	                    ackByte = new byte[packetSize];
	                    receivedAckPacket = new DatagramPacket(ackByte, ackByte.length);
	                }

	                //-------------------------------------------------------------------
    				// (4) receive ack
    				//-------------------------------------------------------------------
	                timeOut = false;
	                try {
	                	ackByte = new byte[packetSize];
	                    receivedAckPacket = new DatagramPacket(ackByte, ackByte.length);
	                    
	                    socket.setSoTimeout(100);
	                    socket.receive(receivedAckPacket);
	                    
	                    byteInputStream = new ByteArrayInputStream(ackByte);
						dataInputStream = new DataInputStream(byteInputStream);
						currentSeqNum = dataInputStream.readInt();
	                }
	                catch (SocketTimeoutException e) {
	                    socket.send(newPacket);
	                    timeOut = true;
	                    updateTrack("ACK(" + seqNumber + ") time outed.\n");
	                }
	                catch (IOException e) {
	                	updateTrack("Server: Error!\n");
	                    socket.send(newPacket);
	                    timeOut = true;
	                }
	                if (!timeOut) {
	                    final ByteArrayInputStream byteReceivedAck = new ByteArrayInputStream(receivedAckPacket.getData());
	                    final DataInputStream dataReciverAck = new DataInputStream(byteReceivedAck);
	                    int ackNum = dataReciverAck.readInt();

	                    if (ackNum % 10 == 0) {
	                        updateTrack("ACK(" + String.valueOf(ackNum) + ")\n");
	                    }

	                    seqNumber++;
	                }
	                Log.d(LOG_TAG, "bytes_count : " + bytes_count);
	                Thread.sleep(50, 0);
	            }
	        }
	        catch (IOException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	            Log.i(LOG_TAG, e.getMessage());
	
	        }
	        catch (InterruptedException e) {
	            // TODO Auto-generated catch block
	            e.printStackTrace();
	            Log.i(LOG_TAG, e.getMessage());
	        }
	    }
	}
	
	public void updateTrack(String str) {
		Message msg = new Message();
		msg.obj = str;
		Handler.sendMessage(msg);
	}
}
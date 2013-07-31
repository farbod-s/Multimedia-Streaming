package network.udp.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import android.app.Activity;
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
import android.media.MediaPlayer;

public class UDP_client extends Activity {
	// configure connection
	public static final String 	SERVER_IP		= "10.0.2.2";	// 'Within' the emulator!
	public static final int		SERVER_PORT		= 5555;
	public static final int		CLIENT_PORT		= 6666;

	public static final int		PACKET_SIZE		= 100;
	public int						currentSeqNum	= 1;
	public long					fileSize;

	public TextView					outputTxt;
	public EditText					inputTxt;
	public Button					sendBtn;
	public boolean					startFlag;
	public Handler					HandlerObj;
	public Button					exitBtn;
	public Button					playBtn;

	static final String			LOG_TAG			= "UDP_Client";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// initialization
		outputTxt = (TextView) findViewById(R.id.textView1);
		inputTxt = (EditText) findViewById(R.id.editText1);
		sendBtn = (Button) findViewById(R.id.button1);
		exitBtn = (Button) findViewById(R.id.ext);
		playBtn = (Button) findViewById(R.id.button2);
		startFlag = false;

		// message handler
		HandlerObj = new Handler() {
    		@Override
        	public void handleMessage(Message msg) {
        		String text = (String) msg.obj;
            	outputTxt.append(text);
        	}
    	};

    	sendBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
    			// TODO Auto-generated method stub
				if(!startFlag) {
					startFlag = true;
					new Thread(new Client()).start();
				}
			}
		});

    	// exit application
    	exitBtn.setOnClickListener(new OnClickListener() {
    		@Override
			public void onClick(View v) {
    			// TODO Auto-generated method stub
    			finish();
    			System.exit(0);
			}
    	});

    	// play media file
        playBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaPlayer mp = new MediaPlayer();
                try {
                	String path = Environment.getExternalStorageDirectory().getPath() + "/" + inputTxt.getText().toString();
                	updateTrack("PATH=" + path + "\n");
                    mp.setDataSource(path);
                    mp.prepare();
                    mp.start();
                }
                catch (Exception e) {
                    updateTrack("Client: can not play media file!\n");
                }
            }
        });
	}
	
	// UDP client
    public class Client implements Runnable {
		DatagramSocket 	socket;			// client socket
		InetAddress 	serverAddress;	// server address

		// client constructor
		public Client() {
			try {
				socket = new DatagramSocket(CLIENT_PORT);
				socket.setReuseAddress(true);
				serverAddress = InetAddress.getByName(SERVER_IP);
				updateTrack("initializing ...\n");
			}
			catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			try {
				byte[] inputBuffer;
				if (!inputTxt.getText().toString().isEmpty()) {
					inputBuffer = inputTxt.getText().toString().getBytes();
				}
				else {
					updateTrack("Client: Unknown file name!\n");
					return;
				}

				//-------------------------------------------------------------------
				// (1) send request
				//-------------------------------------------------------------------
				updateTrack("request is sending ...\n");
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				final DataOutputStream daos = new DataOutputStream(baos);

				daos.writeInt(PACKET_SIZE);
				daos.writeInt(inputTxt.length());
				daos.write(inputBuffer);
				daos.close();

				// packet = { packet size (4 byte) + file name length (4 byte) + file name }
				DatagramPacket packet = new DatagramPacket(baos.toByteArray(), baos.size(), serverAddress, SERVER_PORT);

				socket.send(packet);

				//-------------------------------------------------------------------
				// (2) receive file size from server
				//-------------------------------------------------------------------
				byte[] fileSizeBuffer = new byte[PACKET_SIZE];
				DatagramPacket fileSizePacket = new DatagramPacket(fileSizeBuffer, fileSizeBuffer.length);
				socket.receive(fileSizePacket);

				final ByteArrayInputStream byteFileSizepacket = new ByteArrayInputStream(fileSizePacket.getData());
				final DataInputStream dataFileSize = new DataInputStream(byteFileSizepacket);
				fileSize = dataFileSize.readLong();
				updateTrack("file size: " + fileSize + "\n");

				//-------------------------------------------------------------------
				// (3) send file size ack
				//-------------------------------------------------------------------
				final ByteArrayOutputStream byteFileSizeAck = new ByteArrayOutputStream();
				final DataOutputStream dataFileSizeAck = new DataOutputStream(byteFileSizeAck);

				dataFileSizeAck.writeInt(currentSeqNum);
				dataFileSizeAck.close();

				DatagramPacket dataFileSizeAckPacket = new DatagramPacket(byteFileSizeAck.toByteArray(),
																		  byteFileSizeAck.size(),
															  			  serverAddress,
															  			  SERVER_PORT);

				socket.send(dataFileSizeAckPacket);

				updateTrack("file info ack sent.\n");
				
				// output file
				File root = Environment.getExternalStorageDirectory();
				FileOutputStream f = new FileOutputStream(new File(root, inputTxt.getText().toString()));

				// calculate total packets
				int temp = (int) (fileSize / (PACKET_SIZE - 4));
				int totalPackets = ((int) (fileSize % (PACKET_SIZE - 4)) == 0) ? (temp) : (++temp);

				updateTrack("total packets: " + totalPackets + "\n");
				
				//-------------------------------------------------------------------
				// (4) receive incoming packets
				//-------------------------------------------------------------------
				updateTrack("receive incoming packet.\n");
				byte[] buff = new byte[PACKET_SIZE];
				DatagramPacket newPack = new DatagramPacket(buff, buff.length);
				socket.receive(newPack);

				ByteArrayInputStream byteInputStream = null;
				DataInputStream dataInputStream = null;
				int seqNumber = -1;
				
				do {
					byteInputStream = new ByteArrayInputStream(buff);
					dataInputStream = new DataInputStream(byteInputStream);
					seqNumber = dataInputStream.readInt();

					if(seqNumber == currentSeqNum) {
						byte[] dataBuffer = new byte[PACKET_SIZE - 4];
						dataInputStream.read(dataBuffer);

						if (seqNumber == totalPackets) {
							updateTrack("transfer completed.\n");
							f.write(dataBuffer, 0, (int) fileSize - ((totalPackets - 1) * (PACKET_SIZE - 4)));
						}
						else {
							f.write(dataBuffer,	0, (PACKET_SIZE - 4));
						}

						//-------------------------------------------------------------------
						// (5) send ack
						//-------------------------------------------------------------------
						double randNumber = Math.random();
						if (randNumber < .95 ) {
							final ByteArrayOutputStream byteReqAck = new ByteArrayOutputStream();
							final DataOutputStream dataReqAck = new DataOutputStream(byteReqAck);
	
							dataReqAck.writeInt(currentSeqNum);
							dataReqAck.close();
	
							DatagramPacket dataAckPacket = new DatagramPacket(byteReqAck.toByteArray(),
																			  byteReqAck.size(),
																			  serverAddress,
																			  SERVER_PORT);
	
							socket.send(dataAckPacket);
						}
						
						if (seqNumber % 10 == 0) {
	                        updateTrack("ACK(" + String.valueOf(seqNumber) + ")\n");
	                    }
						
						currentSeqNum++;
					}
					else if(seqNumber < currentSeqNum) {
						final ByteArrayOutputStream byteReqAck = new ByteArrayOutputStream();
						final DataOutputStream dataReqAck = new DataOutputStream(byteReqAck);

						dataReqAck.writeInt(seqNumber);
						dataReqAck.close();

						DatagramPacket dataAckPacket = new DatagramPacket(byteReqAck.toByteArray(),
																		  byteReqAck.size(),
																		  serverAddress,
																		  SERVER_PORT);

						socket.send(dataAckPacket);
						
						updateTrack("ACK(" + seqNumber + ") lost.\n");
					}
					buff = null;
					buff = new byte[PACKET_SIZE];
					newPack = null;
					newPack = new DatagramPacket(buff, buff.length);
					socket.receive(newPack);
				}
				while (currentSeqNum <= totalPackets);

				f.close();
			}
			catch (Exception e) {
				updateTrack("Client: Error!\n");
				Log.i(LOG_TAG, e.getMessage());
			}
		}
	}

	public void updateTrack(String str) {
		Message msg = new Message();
		msg.obj = str;
		HandlerObj.sendMessage(msg);
	}
}
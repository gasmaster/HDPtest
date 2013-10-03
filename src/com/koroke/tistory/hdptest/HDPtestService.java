package com.koroke.tistory.hdptest;



import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


import com.koroke.tistory.hdptest.R;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHealth;
import android.bluetooth.BluetoothHealthAppConfiguration;
import android.bluetooth.BluetoothHealthCallback;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class HDPtestService extends Service {

	// �α� ��� ����� ���� tag
	private static final String TAG = "HDPtestService";
	private static final String DTAG = "DEBUG";
	
	
	// ��������� ��ȯ���� üũ�ϱ� ���� ���
	public static final int RESULT_OK = 0;
	public static final int RESULT_FAIL = -1;
	
	// UI Ŭ���̾�Ʈ�� ���� ���� �ڵ� 
	public static final int STATUS_HEALTH_APP_REG = 100;
	public static final int STATUS_HEALTH_APP_UNREG = 101;
	public static final int STATUS_CREATE_CHANNEL = 102;
	public static final int STATUS_DESTROY_CHANNEL = 103;
	public static final int STATUS_READ_DATA = 104;
	public static final int STATUS_READ_DATA_DONE = 105;
	
	
	// UI Ŭ���̾�Ʈ�� ���� ������ �����ڵ�
	public static final int MSG_REG_CLIENT = 200;
	public static final int MSG_UNREG_CLIENT = 201;
	public static final int MSG_REG_HEALTH_APP = 300;
	public static final int MSG_UNREG_HEALTH_APP = 301;
	public static final int MSG_CONNECT_CHANNEL = 400;
	public static final int MSG_DISCONNECT_CHANNEL = 401;
	
	//�����, �̿ϱ�, �ƹ� ���ſϷ� ��ȣ(UI Ŭ���̾�Ʈ�κ���)
	public static final int RECEIVED_SYS = 500;
	public static final int RECEIVED_DIA = 501;
	public static final int RECEIVED_PUL = 502;
	
	//packet �� �����ֱ����� �޽���
	public static final int RECEIVED_PACK1 = 600;
	public static final int RECEIVED_PACK2 = 601;
	public static final int RECEIVED_PACK3 = 602;
	
	// ������� ��ġ�� ����ϱ� ���� �ȵ���̵� ��ü��
	private BluetoothHealthAppConfiguration mHealthAppConfig;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothHealth mBluetoothHealth;
	private BluetoothDevice mDevice;
	private int mChannelId;
	
	// UI client�� ����ϱ� ���� �޽��� ����
	// IncomingHandler�� ���ڸ� �޾� �ν��Ͻ��� �����Ѵ�.
	private Messenger mClient;
	
	// �������(���а�)�κ��� �����͸� ������ ������ 
	// ��� State �� ��Ÿ���� ����
	int count = 0;
	byte invoke[] = new byte[] { (byte) 0x00, (byte) 0x00 };
	
	
	private class IncomingHandler extends Handler{
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
			// UI clent���� �� ���񽺸� ��ϿϷ��Ͽ����� ����
			// client�� �޽����� ����Ͽ� ���񽺿��� UI client�� �޽�����
			// ���� �� �ֵ��� �Ѵ�. 
            case MSG_REG_CLIENT:
                Log.d(DTAG,"MSG_REG_CLIENT");
                mClient = msg.replyTo;
                break;
                
            // ��ϵ� ���񽺸� ��������Ͽ����� Ŭ���̾�Ʈ�� �޽�����������
            case MSG_UNREG_CLIENT:
            	Log.d(DTAG,"MSG_UNREG_CLIENT");
                mClient = null;
                break;
                
            // ������� �ҽ�(���)�� ����� ��ũ(��)�� ����Ѵ�.
            case MSG_REG_HEALTH_APP:
            	Log.d(DTAG,"MSG_REG_HEALTH_APP");
                registerApp(msg.arg1);
                break;
                
            // ������� �ҽ�(���)�� ����Ǿ��ִ� ��ũ(��)�� ����
            case MSG_UNREG_HEALTH_APP:
            	Log.d(DTAG,"MSG_UNREG_HEALTH_APP");
                unregisterApp();
                break;
                
            // ����̽��� ���� ������ �����Ͽ� BluetoothDevice �ν��Ͻ�
            // �� ��´�. 
            case MSG_CONNECT_CHANNEL:
            	Log.d(DTAG,"MSG_CONNECT_CHANNEL");
                mDevice = (BluetoothDevice) msg.obj;
                connectChannel();
                break;
            // ����̽��� ������ ���´�. 
            case MSG_DISCONNECT_CHANNEL:
            	Log.d(DTAG,"MSG_DISCONNECT_CHANNEL");
                mDevice = (BluetoothDevice) msg.obj;
                disconnectChannel();
                break;
            default:
                super.handleMessage(msg);
			}
		}
	}
	
	final Messenger mMessenger = new Messenger(new IncomingHandler());
	
	/**
	 * ��������� Health Profile �� �������� ���񽺰� �����ɶ� üũ�Ѵ�. 
	 * ��������� �������� �ʴ������ ���񽺸� �������� �ʰ� �ٷ� �����Ų��.
	 */
	@Override
	public void onCreate(){
		super.onCreate();
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
			// ��������� �����Ǵ� ��� �ƴϰų� ���� Ȱ��ȭ �Ǿ����� �ʴٸ� Ȱ��ȭ��Ų��
			// �ٽ� ���񽺸� ������Ѿ� �Ѵ�.
			stopSelf();
			return;
		}
		// BluetoothAdapter �� getProfileProxy�� �̿��Ͽ� proxy object �� ���´�.
		// ���� proxy object �� 2��° ���ڷ� ������ callback �ڵ鷯�� ���Ͽ� 
		// BluetoothHealth Ŭ���� ���������� �����Ͽ� �����Ѵ�.
		Log.d(DTAG,"service:getProfileProxy()");
		if(!mBluetoothAdapter.getProfileProxy(this, mBluetoothServiceListener,
				BluetoothProfile.HEALTH)){
			Toast.makeText(this,R.string.bluetooth_health_profile_not_available,
					Toast.LENGTH_SHORT).show();
			stopSelf();
			return;
		}
	}
	
	/*
	 * ������� ���Ͻ� ������Ʈ�� ���� ����Listener 
	 */
	private final BluetoothProfile.ServiceListener mBluetoothServiceListener =
			new BluetoothProfile.ServiceListener() {
				
				@Override
				public void onServiceDisconnected(int profile) {
					// TODO Auto-generated method stub
					if(profile == BluetoothProfile.HEALTH){
						mBluetoothHealth = null;
					}
				}
				
				@Override
				public void onServiceConnected(int profile, BluetoothProfile proxy) {
					// TODO Auto-generated method stub
					if(profile == BluetoothProfile.HEALTH){
						Log.d(DTAG,"service:onServiceConnected()");
						mBluetoothHealth = (BluetoothHealth)proxy;
						if(Log.isLoggable(TAG, Log.DEBUG))
							Log.d(TAG,"onServiceConnected to profile: " + profile);
					}
				}
	};
	
	private final BluetoothHealthCallback mHealthCallback = new BluetoothHealthCallback(){
		// ���ø����̼�(HDPtestService)�� ��� �� ���� �̺�Ʈ�� ���� �ݹ��Լ� 
		// HDPtestService �� sendMessage �Լ��� ���� UI Client���� ���¸� �����Ѵ�.
		public void onHealthAppConfigurationStatusChange(BluetoothHealthAppConfiguration config,
				int status){
	     if (status == BluetoothHealth.APP_CONFIG_REGISTRATION_FAILURE) {
                mHealthAppConfig = null;
                sendMessage(STATUS_HEALTH_APP_REG, RESULT_FAIL);
            } else if (status == BluetoothHealth.APP_CONFIG_REGISTRATION_SUCCESS) {
                mHealthAppConfig = config;
                sendMessage(STATUS_HEALTH_APP_REG, RESULT_OK);
            } else if (status == BluetoothHealth.APP_CONFIG_UNREGISTRATION_FAILURE ||
                    status == BluetoothHealth.APP_CONFIG_UNREGISTRATION_SUCCESS) {
                sendMessage(STATUS_HEALTH_APP_UNREG,
                        status == BluetoothHealth.APP_CONFIG_UNREGISTRATION_SUCCESS ?
                        RESULT_OK : RESULT_FAIL);
            }
		}
		
		public void onHealthChannelStateChange(BluetoothHealthAppConfiguration config,
				BluetoothDevice device,int prevState,int newState, ParcelFileDescriptor fd,
				int channelId){
		        if (Log.isLoggable(TAG, Log.DEBUG))
	                Log.d(TAG, String.format("prevState\t%d ----------> newState\t%d",
	                        prevState, newState));
	            if (prevState == BluetoothHealth.STATE_CHANNEL_DISCONNECTED &&
	                    newState == BluetoothHealth.STATE_CHANNEL_CONNECTED) {
	                if (config.equals(mHealthAppConfig)) {
	                    mChannelId = channelId;
	                    Log.d(DTAG,"service:onHealthChannelStateChange -- " +
	                    		"�̿���==>����� ��ȯ�ù߻�, ReadThread�� ȣ��");
	                    
	                    sendMessage(STATUS_CREATE_CHANNEL, RESULT_OK);
	                    (new ReadThread(fd)).start();
	                } else {
	                    sendMessage(STATUS_CREATE_CHANNEL, RESULT_FAIL);
	                }
	            } else if (prevState == BluetoothHealth.STATE_CHANNEL_CONNECTING &&
	                       newState == BluetoothHealth.STATE_CHANNEL_DISCONNECTED) {
	            	Log.d(DTAG,"service:onhealthChannelStateChange --"
	            			+ "���� ==> �̿���� ��ȯ�� �߻�,STATUS_CREATE_CHANNEL,RESULT_FAIL");
	                sendMessage(STATUS_CREATE_CHANNEL, RESULT_FAIL);
	            } else if (newState == BluetoothHealth.STATE_CHANNEL_DISCONNECTED) {
	            	Log.d(TAG, "I'm in State Channel Disconnected.");
	                if (config.equals(mHealthAppConfig)) {
	                    sendMessage(STATUS_DESTROY_CHANNEL, RESULT_OK);
	                } else {
	                    sendMessage(STATUS_DESTROY_CHANNEL, RESULT_FAIL);
	                }
	            }
		}
		
		
			
	};
	
	private void sendMessage(int what,int value){
		if(mClient == null){
			Log.d(TAG,"No client registered.");
			return;
		}
		
		try{
			mClient.send(Message.obtain(null,what,value,0));
		}catch(RemoteException e){
			e.printStackTrace();
		}
	}
	private void sendMessageWithString(int what,String str){
		if(mClient == null){
			Log.d(TAG,"No client registered.");
			return;
		}
		
		try{
			mClient.send(Message.obtain(null,what,str));
		}catch(RemoteException e){
			e.printStackTrace();
		}
	}
	
	
	@Override
	public int onStartCommand(Intent intent,int flags,int startId){
		Log.d(DTAG, "service:onStartCommand()");
		return START_STICKY;
	}
	

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		Log.d(DTAG,"service:onBind()");
		return mMessenger.getBinder();
	}
	
	// �ｺ��(HDPtestService)�� BluetoothHealth �� API�Լ��� �̿��Ͽ� ����Ѵ�. 
	// mHealthCallback �� ���� ��������� ä���� ��������� �����ϴ� callback �Լ�
	private void registerApp(int dataType){
		Log.d(DTAG,"service:registerApp():BluetoothHealth.registerSinkAppConfiguration()");
		mBluetoothHealth.registerSinkAppConfiguration(TAG, dataType, mHealthCallback);
	}
	
	// �ｺ��(HDPtestService)�� ��������Ѵ�.
	private void unregisterApp(){
		Log.d(DTAG,"service:unregisterApp():Bluetoothhealth.unregisterAppConfiguration()");
		mBluetoothHealth.unregisterAppConfiguration(mHealthAppConfig);
	}
	
	// BluetoothDevice(���� ������� ��ġ, �� �ｺ����̽���ġ)�� BluetoothHealth�� API
	// �� �̿��Ͽ� ����Ѵ�.
	private void connectChannel(){
		Log.d(DTAG,"service:connectChannel()");
		mBluetoothHealth.connectChannelToSource(mDevice, mHealthAppConfig);
	}
	
	// ����� BluetoothDevice�� ������� �Ѵ�. 
	private void disconnectChannel(){
		Log.d(DTAG,"service:disconnectChannel()");
		mBluetoothHealth.disconnectChannel(mDevice, mHealthAppConfig, mChannelId);
	}
	
	
	//========================================================================//
	// ���ŵ� �����͸� �б����� ���� �Լ����� �����Ѵ�.
	//=========================================================================//
	
	public String byte2hex(byte[] b)
	{
		// String Buffer can be used instead
		String hs = "";
		String stmp = "";
		
		for(int n = 0;n<b.length;n++)
		{
			stmp = (java.lang.Integer.toHexString(b[n] & 0xFF));
			
			if(stmp.length() == 1)
			{
				hs = hs + "0" + stmp;
			}
			else
			{
				hs = hs + stmp;
			}
			if(n < b.length - 1)
			{
				hs = hs + "";
			}
		}
		return hs;
	}
	
	public static int byteToUnsignedInt(byte b){
		return 0x00 << 24 | b & 0xff;
	}
	
	// Thread to read incoming data received from the HDP device. This simple application merely
	// reads the raw byte from the incoming file descriptor. The data should be interpreted using
	// a health manager which implements the IEEE 11073-xxxxx specifications.
	private class ReadThread extends Thread{
		private ParcelFileDescriptor mFd;
		
		public ReadThread(ParcelFileDescriptor fd){
			super();
			mFd=fd;
		}
		
        @Override
        public void run() {
            FileInputStream fis = new FileInputStream(mFd.getFileDescriptor());
            byte data[] = new byte[300];
            
            try {
                while(fis.read(data) > -1) {
                    // At this point, the application can pass the raw data to a parser that
                    // has implemented the IEEE 11073-xxxxx specifications.  Instead, this sample
                    // simply indicates that some data has been received.                	
                	if (data[0] != (byte) 0x00)
                	{
                		String test = byte2hex(data);
                		Log.i(TAG, test);
	                	if(data[0] == (byte) 0xE2){
	                		Log.i(TAG, "E2");
	                		
	                		//data_AR
	                		count = 1;
	                		
	                		sendMessageWithString(RECEIVED_PACK1,
	                				test.substring(0,data[3]));
	                		Log.i(DTAG,data[3]);
	                		(new WriteThread(mFd)).start();;
	                		try {
								sleep(100);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
	                		count = 2;
	                		(new WriteThread(mFd)).start();
	                	}
	                	else if (data[0] == (byte)0xE7){
	                		Log.i(TAG, "E7");
	                		
	                		//work for legacy device...
	                		if (data[18] == (byte) 0x0d && data[19] == (byte) 0x1d)  //fixed report
	                		{
	                			sendMessageWithString(RECEIVED_PACK2,
	                					test.substring(0, data[3]));
	                			count = 3; 
	                			//set invoke id so get correct response
	                			invoke = new byte[] { data[6], data[7] };
	                			//write back response
	                			(new WriteThread(mFd)).start();		
	                			//parse data!!
	                			int length = data[21];
	                			Log.i(TAG, "length is " + length);
	                			// check data-req-id 
//                				int report_no = data[22+3];
                				int number_of_data_packets = data[22+5];
                				//packet_start starts from handle 0 byte
	                			int packet_start = 30;
	                			final int SYS_DIA_MAP_DATA = 1;
	                			final int PULSE_DATA = 2;
	                			final int ERROR_CODE_DATA = 3;
                				for (int i = 0; i < number_of_data_packets; i++)
	                			{
                					int obj_handle = data[packet_start+1];
                					switch (obj_handle)
                					{
                					case SYS_DIA_MAP_DATA:
                						int sys = byteToUnsignedInt(data[packet_start+9]);
                						int dia = byteToUnsignedInt(data[packet_start+11]);
                						int map = byteToUnsignedInt(data[packet_start+13]);
	                					//create team string... 9+13~9+20	
	                					Log.i(TAG, "sys is "+ sys);
	                					sendMessage(RECEIVED_SYS, sys);
	                					Log.i(TAG, "dia is "+ dia);
	                					sendMessage(RECEIVED_DIA, dia);
	                					Log.i(TAG, "map is "+ map);
	                					//test
//	                					sendMessage(RECEIVED_MAP, map);
                						break;
                					case PULSE_DATA:
                						//parse
                						int pulse = byteToUnsignedInt(data[packet_start+5]);
                						Log.i(TAG, "pulse is " + pulse);
                						sendMessage(RECEIVED_PUL, pulse);
                						break;
                					case ERROR_CODE_DATA:
                						//need more signal
                						break;
                					}
                					packet_start += 4 + data[packet_start+3];	//4 = ignore beginning four bytes
	                			}	                			
	                		}
	                		else
	                		{
	                			count = 2;
	                		}
	                	}
	                	else if (data[0] == (byte) 0xE4)
	                	{
	                		sendMessageWithString(RECEIVED_PACK3,
	                				test.substring(0, data[3]));
	                		count = 4;
	                		(new WriteThread(mFd)).start();
//	                		sendMessage();
	                	}
	                	//zero out the data
	                    for (int i = 0; i < data.length; i++){
	                    	data[i] = (byte) 0x00;
	                    }
                	}
                    sendMessage(STATUS_READ_DATA, 0);
                }//while
            } catch(IOException ioe) {}
            if (mFd != null) {
                try {
                    mFd.close();
                } catch (IOException e) { /* Do nothing. */ }
            }//try-catch	
            sendMessage(STATUS_READ_DATA_DONE, 0);
        }//run
    }//thread
    
    
    private class WriteThread extends Thread {
        private ParcelFileDescriptor mFd;

        public WriteThread(ParcelFileDescriptor fd) {
            super();
            mFd = fd;
        }

        @Override
        public void run() {
        	FileOutputStream fos = new FileOutputStream(mFd.getFileDescriptor());
//            FileInputStream fis = new FileInputStream(mFd.getFileDescriptor());
//        	Association Response [0xE300]
            final byte data_AR[] = new byte[] {			(byte) 0xE3, (byte) 0x00,
            											(byte) 0x00, (byte) 0x2C, 
			            								(byte) 0x00, (byte) 0x00,
			            								(byte) 0x50, (byte) 0x79,
			            								(byte) 0x00, (byte) 0x26,
			            								(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			            								(byte) 0x80, (byte) 0x00,
			            								(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			            								(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			            								(byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			            								(byte) 0x00, (byte) 0x08,  //bt add for phone, can be automate in the future
			            								(byte) 0x3C, (byte) 0x5A, (byte) 0x37, (byte) 0xFF, 
			            								(byte) 0xFE, (byte) 0x95, (byte) 0xEE, (byte) 0xE3,
			            								(byte) 0x00, (byte) 0x00,
			            								(byte) 0x00, (byte) 0x00,
			            								(byte) 0x00, (byte) 0x00, 
			            								(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
//          Presentation APDU [0xE700]
            final byte data_DR[] = new byte[] { 		(byte) 0xE7, (byte) 0x00,
            											(byte) 0x00, (byte) 0x12,
            											(byte) 0x00, (byte) 0x10,
            											(byte) invoke[0], (byte) invoke[1],
            											(byte) 0x02, (byte) 0x01,
            											(byte) 0x00, (byte) 0x0A,
            											(byte) 0x00, (byte) 0x00,
            											(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            											(byte) 0x0D, (byte) 0x1D,
            											(byte) 0x00, (byte) 0x00 };
            
            final byte get_MDS[] = new byte[] { 	    (byte) 0xE7, (byte) 0x00,
														(byte) 0x00, (byte) 0x0E,
														(byte) 0x00, (byte) 0x0C,
														(byte) 0x00, (byte) 0x24,
														(byte) 0x01, (byte) 0x03,
														(byte) 0x00, (byte) 0x06,
														(byte) 0x00, (byte) 0x00,
														(byte) 0x00, (byte) 0x00,
														(byte) 0x00, (byte) 0x00 };
									            
            final byte data_RR[] = new byte[] {			(byte) 0xE5, (byte) 0x00,
            											(byte) 0x00, (byte) 0x02,
            											(byte) 0x00, (byte) 0x00 };
            
            final byte data_RRQ[] = new byte[] {		(byte) 0xE4, (byte) 0x00,
            											(byte) 0x00, (byte) 0x02,
            											(byte) 0x00, (byte) 0x00 };
            
            final byte data_ABORT[] = new byte[] {		(byte) 0xE6, (byte) 0x00,
														(byte) 0x00, (byte) 0x02,
														(byte) 0x00, (byte) 0x00 };
            try {
            	Log.i(TAG, String.valueOf(count));
            	if (count == 1)
            	{
            		fos.write(data_AR);
                    Log.i(TAG, "Association Responsed!");
            	}  
            	else if (count == 2)
            	{
            		fos.write(get_MDS);
            		Log.i(TAG, "Get MDS object attributes!");
//            		fos.write(data_ABORT);
            	}
            	else if (count == 3) 
            	{
            		fos.write(data_DR);
                    Log.i(TAG, "Data Responsed!");
            	}
            	else if (count == 4)
            	{
            		fos.write(data_RR);
            		Log.i(TAG, "Data Released!");
            	}
            } catch(IOException ioe) {}
        }
    }
	

}

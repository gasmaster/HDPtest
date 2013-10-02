package com.koroke.tistory.hdptest;




import com.koroke.tistory.hdptest.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class HDPtestActivity extends Activity {
	private static final String TAG = "HDPtestActivity";
	private static final String AND_HEART = "A&D BP UA-767PBT-C";
	//0x1007 - blood pressure meter
	//0x1008 - body thermometer
	//0x100f - body weight scale
	
	private static final int HEALTH_PROFILE_SOURCE_DATA_TYPE = 0x1007;
	private static final int REQUEST_ENABLE_BT = 1;
	
	// widget ���
	private TextView mConnectIndicator; // �������ǥ��  view(connect_ind)
	private TextView mStatusMessage; // �������ǥ�� view(status_msg)
	
	
	
	// Bluetooth ���� ��������
	private BluetoothAdapter mBluetoothAdapter; 
	private BluetoothDevice[] mAllBondedDevices;  // �̹� ��ϵǾ� �ִ°��
	private BluetoothDevice[] mSearchedDevices;	  // ã�ƾ� �ϴ� ���
	private BluetoothDevice mDevice;   //���ݺ���������(�Ƿ��������)�� ǥ���ϴ�Ŭ����
	private Resources mRes;  // ������� ���̴��� üũ
	private Messenger mHealthService; // HDPtestService�� ����ϱ����� �޽�����������
	private boolean mHealthServiceBound; // HDPtestService �� ���ε�����
	private int mDeviceIndex = 0;
	
	// Dialog ���� ��������
	ProgressDialog scanDeviceDialog;
	boolean scan_finished = false;
	
	// ��ġ �˻��� ���� ���� ��ġ �̸����庯��
	String[] DeviceList = new String[10];
	private int found_num = 0;
	
	
	
	private Handler mIncomingHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
			// ���ø����̼��� ���񽺿� ���
			case HDPtestService.STATUS_HEALTH_APP_REG:
				mStatusMessage.setText(String.format(
						mRes.getString(R.string.status_reg), msg.arg1));
				
				//mBluetoothAdapter.startDiscovery();
				//���ù��� ���� ��� �������� ���
				//connectChannel();
				break;
			// ���ø����̼��� �������
			case HDPtestService.STATUS_HEALTH_APP_UNREG:
				mStatusMessage.setText(String.format(
						mRes.getString(R.string.status_unreg), msg.arg1));
				break;
			// Helath Device�κ��� ������ ����	
			case HDPtestService.STATUS_READ_DATA:
				mStatusMessage.setText(mRes.getString(R.string.read_data));
				break;
			// Health Device�κ��� ���� ������ ���� �Ϸ�
			case HDPtestService.STATUS_READ_DATA_DONE:
				mStatusMessage.setText(mRes.getString(R.string.read_data_done));
				break;
			// Health Device���� ä�ΰ� ����Ϸ�
			case HDPtestService.STATUS_CREATE_CHANNEL:
				Log.d(TAG,"STATUS_CREATE_CHANNEL enabled");
				mStatusMessage.setText(String.format(mRes.getString(
						R.string.status_create_channel), 
						msg.arg1));
				mConnectIndicator.setText(R.string.connected);
				break;
				
			case HDPtestService.STATUS_DESTROY_CHANNEL:
				mStatusMessage.setText(String.format(mRes.getString(
						R.string.status_destroy_channel), 
						msg.arg1));
				mConnectIndicator.setText(R.string.disconnected);
				break;
				
			case HDPtestService.RECEIVED_SYS:
				int sys = msg.arg1;
				Log.i(TAG,"msg.arg1 @ sys is "+ sys);
				// sys���� view ��Ҹ� ���ؼ� �����ֵ����ϴ� �����߰�
				break;
			case HDPtestService.RECEIVED_DIA:
				int dia = msg.arg1;
				Log.i(TAG,"msg.arg1 @ dia is "+ dia);
				// dia ���� view ��Ҹ� ���ؼ� �����ֵ����ϴ� �����߰�
				break;
			case HDPtestService.RECEIVED_PUL:
				int pul = msg.arg1;
				Log.i(TAG,"msg.arg1 @ pul is "+ pul);
				// pub ���� view ��Ҹ� ���ؼ� �����ֵ����ϴ� �����߰�
				break;
			default:
				super.handleMessage(msg);
				
			}
		}
	};
	
	private final Messenger mMessenger = new Messenger(mIncomingHandler);
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		// ��������� ���������� ������� Ȯ���Ѵ�. BluetoothAdapterŬ�����̿�
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(mBluetoothAdapter == null){
			Toast.makeText(this, 
					R.string.bluetooth_not_available, 
					Toast.LENGTH_SHORT).show();
			finish(); 
			return;
		}
		
		// widget ���� �ν��Ͻ������� �� ���º��� �ʱ�ȭ
		setContentView(R.layout.hdp_activity);
		mConnectIndicator = (TextView)findViewById(R.id.connect_ind);
		mStatusMessage = (TextView)findViewById(R.id.status_msg);
		mRes = getResources();
		mHealthServiceBound = false;
		
		// ��Ϲ�ư �̺�Ʈ ����
		Button registerAppButton = (Button)findViewById(R.id.button_register_app);
		registerAppButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				// ��������ϰ� source data type(���а�)�� �����Ѵ�.
				sendMessage(HDPtestService.MSG_REG_HEALTH_APP,
						HEALTH_PROFILE_SOURCE_DATA_TYPE); 
				mBluetoothAdapter.startDiscovery();
				
				
			}
		}); // registerAppButton
		
		// ������ư �̺�Ʈ ����
		Button unregisterAppButton = (Button) findViewById(R.id.button_unregister_app);
		unregisterAppButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				sendMessage(HDPtestService.MSG_UNREG_HEALTH_APP,0);
				
			}
		});
		
		registerReceiver(mReceiver,initIntentFilter());
	}//onCreate()
	
	private ServiceConnection mConnection = new ServiceConnection(){
		public void onServiceConnected(ComponentName name,IBinder service){
			mHealthServiceBound = true;
			Message msg = Message.obtain(null, 
					HDPtestService.MSG_REG_CLIENT);
			msg.replyTo = mMessenger; // HDPtestService ==> HDPtestActivity
			mHealthService = new Messenger(service); //HDPtestActivity ==> HDPtestService
			try{
				mHealthService.send(msg);
			}catch(RemoteException e){
				Log.w(TAG, "Unable to register client to service.");
				e.printStackTrace();
			}
		}
		
		public void onServiceDisconnected(ComponentName name){
			mHealthService = null;
			mHealthServiceBound = false;
		}

	
	};
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		if(mHealthServiceBound)
			unbindService(mConnection);
		unregisterReceiver(mReceiver); //��ε�ĳ��Ʈ���ù�����
	}
	
	@Override
	protected void onStart(){
		super.onStart();
		// ���������ġ�� Enable�Ǿ� �����ʴٸ� 
		// ����Ʈ�� ���� ����������񽺿��� ����û��������.
		if(!mBluetoothAdapter.isEnabled()){
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
		}else{
			initialize();
		}
	}
	
	/*
	 * startActivityForResult �Լ� ȣ���� ��ȯ�� ����Ʈ�� �̿��Ͽ� 
	 * ������ intializeȣ�� (��������� enable�Ǿ����Ƿ�)
	 * ���н� ��Ƽ��Ƽ����
	 */
	@Override
	protected void onActivityResult(int requestCode,int resultCode,Intent data){
		switch(requestCode){
		case REQUEST_ENABLE_BT:
			if(resultCode == Activity.RESULT_OK){
				initialize();
			} else{
				finish();
				return;
			}
		}
	}
	
	/*
	 * SelectDeviceDialogFragment ���� ���̴� �Լ� , bonded Bluetooth device 
	 * @param position
	 * 			position of the bonded Bluetooth device in the array.
	 */
	public void setDevice(int position){
		mDevice = this.mAllBondedDevices[position];
		mDeviceIndex = position;
	}
	
	private void connectChannel(){
		
		
		sendMessageWithDevice(HDPtestService.MSG_CONNECT_CHANNEL);
	}
	
	private void disconnectChannel(){
		sendMessageWithDevice(HDPtestService.MSG_DISCONNECT_CHANNEL);
	}
	
	private void initialize(){
		Intent intent = new Intent(this,HDPtestService.class);
		startService(intent);
		bindService(intent,mConnection,Context.BIND_AUTO_CREATE);
	}
	
	// ����Ʈ ���� �� ������� adapter �̺�Ʈ�� �ٷ������ broadcast receiver
	private IntentFilter initIntentFilter(){
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		return filter;
		
	}
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			// TODO Auto-generated method stub
			final String action = intent.getAction();
			if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
				if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
						BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON){
					Log.i(TAG,"Action state changed");
					initialize();
				}
						
			} else if(BluetoothDevice.ACTION_FOUND.equals(action)){
				//����Ʈ���� BluetoothDevice ��ü ����
				BluetoothDevice Device = 
						intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// �߰ߵ� ����̽��� ����?? Dialog?
				mStatusMessage.setText(Device.getName());
				if(Device.getName().equals("A&D BP UA-767PBT-C"))
				{ 	
					Toast.makeText(getApplicationContext(), 
							Device.getName(), Toast.LENGTH_SHORT).show();
					mDevice = Device;
					
				}
				Log.i(TAG,"Action found");
				
			}else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
				scan_finished = true;
				Toast.makeText(getApplicationContext(), 
						"Discovery is finished", Toast.LENGTH_SHORT).show();
			
				Log.i(TAG,"Discovery finished");
			}
			
		}
		
	};
	
	// HDPtestService �� �޽��� ����
	private void sendMessage(int what,int value){
		if(mHealthService == null){
			Log.d(TAG,"Health Service not connected");
			return;
		}
		
		try{
			mHealthService.send(Message.obtain(null,what,value,0));
			//obtain(Handler h,int what,arg1,arg2) arg1,arg2�� ����
		}catch(RemoteException e){
			Log.w(TAG,"Unable to reach service.");
			e.printStackTrace();
		}
	}
	
	// BluetoothDevice 
	private void sendMessageWithDevice(int what){
		if(mHealthService == null){
			Log.d(TAG,"Health Service not connected.");
			return;
		}
		
		try{
			mHealthService.send(Message.obtain(null,what,mDevice));
			//obtain(Handler h,int what,Object obj) ��ü�� ����
		}catch(RemoteException e){
			Log.w(TAG,"Unable to reach service");
			e.printStackTrace();
		}
	}
	
	
	/*
	 * Dialog to display a list of bonded Bluetooth devices for user to select 
	 * from. This is needed only for channel connection initiated from the
	 * application
	 */
	
	public static class SelectDeviceDialogFragment extends DialogFragment{
		public static SelectDeviceDialogFragment newInstance(
				String[] names,int position)
		{
			SelectDeviceDialogFragment frag = new SelectDeviceDialogFragment();
			Bundle args = new Bundle();
			args.putStringArray("names", names);
			args.putInt("position", position);
			return frag;
		}
		
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState){
			String[] deviceNames = getArguments().getStringArray("names");
			int position = getArguments().getInt("position",-1);
			if(position == -1) position = 0;
			return new AlertDialog.Builder(getActivity())
					.setTitle(R.string.select_device)
					.setPositiveButton(R.string.ok,
							new DialogInterface.OnClickListener() {
								
								@Override
								public void onClick(DialogInterface dialog, int which) {
									// TODO Auto-generated method stub
									((HDPtestActivity)getActivity())
									.connectChannel();
								}
							})
					.setSingleChoiceItems(deviceNames, position, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							// TODO Auto-generated method stub
							((HDPtestActivity)getActivity())
							.setDevice(which);
						}
					}).create();	
		} // onCreateDialog
	} // SelectDeviceDialogFragment
	
	
}

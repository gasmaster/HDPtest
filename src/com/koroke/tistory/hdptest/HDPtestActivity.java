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
	
	// widget 요소
	private TextView mConnectIndicator; // 연결상태표시  view(connect_ind)
	private TextView mStatusMessage; // 현재상태표시 view(status_msg)
	
	
	
	// Bluetooth 관련 참조변수
	private BluetoothAdapter mBluetoothAdapter; 
	private BluetoothDevice[] mAllBondedDevices;  // 이미 등록되어 있는경우
	private BluetoothDevice[] mSearchedDevices;	  // 찾아야 하는 경우
	private BluetoothDevice mDevice;   //원격블루투스장비(의료측정장비)를 표현하는클래스
	private Resources mRes;  // 어떤식으로 쓰이는지 체크
	private Messenger mHealthService; // HDPtestService와 통신하기위한 메신저참조변수
	private boolean mHealthServiceBound; // HDPtestService 의 바인딩상태
	private int mDeviceIndex = 0;
	
	// Dialog 관련 참조변수
	ProgressDialog scanDeviceDialog;
	boolean scan_finished = false;
	
	// 장치 검색을 통해 얻어온 장치 이름저장변수
	String[] DeviceList = new String[10];
	private int found_num = 0;
	
	
	
	private Handler mIncomingHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			switch(msg.what){
			// 애플리케이션이 서비스에 등록
			case HDPtestService.STATUS_HEALTH_APP_REG:
				mStatusMessage.setText(String.format(
						mRes.getString(R.string.status_reg), msg.arg1));
				
				//mBluetoothAdapter.startDiscovery();
				//리시버가 받은 결과 값을통해 등록
				//connectChannel();
				break;
			// 애플리케이션을 등록해제
			case HDPtestService.STATUS_HEALTH_APP_UNREG:
				mStatusMessage.setText(String.format(
						mRes.getString(R.string.status_unreg), msg.arg1));
				break;
			// Helath Device로부터 데이터 리딩	
			case HDPtestService.STATUS_READ_DATA:
				mStatusMessage.setText(mRes.getString(R.string.read_data));
				break;
			// Health Device로부터 방은 데이터 리딩 완료
			case HDPtestService.STATUS_READ_DATA_DONE:
				mStatusMessage.setText(mRes.getString(R.string.read_data_done));
				break;
			// Health Device와의 채널간 연결완료
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
				// sys값을 view 요소를 통해서 보여주도록하는 구문추가
				break;
			case HDPtestService.RECEIVED_DIA:
				int dia = msg.arg1;
				Log.i(TAG,"msg.arg1 @ dia is "+ dia);
				// dia 값을 view 요소를 통해서 보여주도록하는 구문추가
				break;
			case HDPtestService.RECEIVED_PUL:
				int pul = msg.arg1;
				Log.i(TAG,"msg.arg1 @ pul is "+ pul);
				// pub 값을 view 요소를 통해서 보여주도록하는 구문추가
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
		
		// 블루투스가 지원가능한 장비인지 확인한다. BluetoothAdapter클래스이용
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(mBluetoothAdapter == null){
			Toast.makeText(this, 
					R.string.bluetooth_not_available, 
					Toast.LENGTH_SHORT).show();
			finish(); 
			return;
		}
		
		// widget 들의 인스턴스얻어오기 및 상태변수 초기화
		setContentView(R.layout.hdp_activity);
		mConnectIndicator = (TextView)findViewById(R.id.connect_ind);
		mStatusMessage = (TextView)findViewById(R.id.status_msg);
		mRes = getResources();
		mHealthServiceBound = false;
		
		// 등록버튼 이벤트 정의
		Button registerAppButton = (Button)findViewById(R.id.button_register_app);
		registerAppButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				// 앱을등록하고 source data type(혈압계)를 지정한다.
				sendMessage(HDPtestService.MSG_REG_HEALTH_APP,
						HEALTH_PROFILE_SOURCE_DATA_TYPE); 
				mBluetoothAdapter.startDiscovery();
				
				
			}
		}); // registerAppButton
		
		// 해제버튼 이벤트 정의
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
		unregisterReceiver(mReceiver); //브로드캐스트리시버해제
	}
	
	@Override
	protected void onStart(){
		super.onStart();
		// 블루투스장치가 Enable되어 있지않다면 
		// 인텐트를 통해 블루투스서비스에게 허용요청을보낸다.
		if(!mBluetoothAdapter.isEnabled()){
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
		}else{
			initialize();
		}
	}
	
	/*
	 * startActivityForResult 함수 호출후 바환된 인텐트를 이용하여 
	 * 성공시 intialize호출 (블루투스가 enable되었으므로)
	 * 실패시 액티비티종료
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
	 * SelectDeviceDialogFragment 에서 쓰이는 함수 , bonded Bluetooth device 
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
	
	// 인텐트 필터 와 블루투스 adapter 이벤트를 다루기위한 broadcast receiver
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
				//인텐트에서 BluetoothDevice 객체 추출
				BluetoothDevice Device = 
						intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// 발견된 디바이스후 연결?? Dialog?
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
	
	// HDPtestService 로 메시지 전송
	private void sendMessage(int what,int value){
		if(mHealthService == null){
			Log.d(TAG,"Health Service not connected");
			return;
		}
		
		try{
			mHealthService.send(Message.obtain(null,what,value,0));
			//obtain(Handler h,int what,arg1,arg2) arg1,arg2를 전송
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
			//obtain(Handler h,int what,Object obj) 객체를 전송
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

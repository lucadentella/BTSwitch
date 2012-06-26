package it.lucadentella.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

public class BTSwitchActivity extends Activity implements OnClickListener {

	// Debug variables
	private static final boolean D = false;
	private static final String TAG = "BTSwitch";

	// I/O timeout (in ms)
	private static final long TIMEOUT = 10000;

	// UUID for SPP service
	private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	
	// Message codes
	private static final int CONNECT_THREAD_RESULT = 1;

	// Member fields
	private BluetoothAdapter bluetoothAdapter = null;
	private BluetoothSocket btSocket = null;
	private Button btConnect = null;
	private ToggleButton btSwitch = null;
	private ToggleButton btLeds = null;

	// Variables
	private BluetoothDevice device = null;
	private boolean connected = false;
	private int defaultColor;

	// Handler for thread communication
	private Handler handler = new Handler() {

		public void handleMessage(Message msg) {

			if(D) Log.d(TAG, "handleMessage()");
			Bundle bundle = msg.getData();		
			
			switch(msg.what) {
			
			// Message from connect thread
			case CONNECT_THREAD_RESULT:
				
				if(D) Log.d(TAG, "CONNECT_THREAD_RESULT");
				
				// Connection wasn't successful
				if(!bundle.getBoolean("connected")) {
					
					if(D) Log.d(TAG, "Connected = false");
					Toast.makeText(getApplicationContext(), R.string.toast_unable_to_connect, Toast.LENGTH_SHORT).show();					
				
				// Connection was successful
				} else {
					
					if(D) Log.d(TAG, "Connected = true");
					
					// Get status (OUT|LED) and split it
					String status = bundle.getString("status");
					String[] statuses = status.split("\\|");
					
					// Set buttons' states and enable them
					btSwitch.setChecked(statuses[0].equals("ON"));
					btLeds.setChecked(statuses[1].equals("ON"));					
					btSwitch.setEnabled(true);
					btLeds.setEnabled(true);
					
					// Change connect button
					Button btConnect = (Button)findViewById(R.id.bt_connect);
					btConnect.setCompoundDrawablesWithIntrinsicBounds(R.drawable.bt_connected, 0, 0, 0);
					btConnect.setText(bundle.getString("about"));
					btConnect.setTextColor(Color.BLUE);					
					
					// We're online!
					connected = true;
				}
				break;
			
			// Message code not listed
			default:
				if(D)Log.d(TAG, "Unknown message code: " + msg.what);			
			}
		}
	};

	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);        
		if(D) Log.d(TAG, "onCreate()");

		// Set layout
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		// Check if bluetoothAdapter is available
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(bluetoothAdapter == null) {
			if(D) Log.d(TAG, "no Bluetooth adapter available, exiting...");
			Toast.makeText(this, R.string.toast_bluetooth_not_available, Toast.LENGTH_LONG).show(); 
			finish();        	
		}         
		else if(D) Log.d(TAG, "Bluetooth adapter found");
	}

	public void onStart() {

		super.onStart();
		if(D) Log.d(TAG, "onStart()");

		// Check if Bluetooth is enabled
		// if not, create an Intent to enable it
		if (!bluetoothAdapter.isEnabled()) {
			if (D) Log.d(TAG, "Bluetooth not enabled, creating Intent...");
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			if (D) Log.d(TAG, "Enable BT activity started");

		// Bluetooth already enabled, create GUI
		} else {
			if(D) Log.d(TAG, "Bluetooth is enabled");
			setup();
		}
	}

    @Override
	protected void onDestroy() {

		super.onDestroy();
		disconnect();
	}
	
	private void setup() {

		if(D) Log.d(TAG, "enter setup()"); 
		
		// Retrieve GUI views from R
		btConnect = (Button)findViewById(R.id.bt_connect);
		btSwitch = (ToggleButton)findViewById(R.id.bt_switch);					
		btLeds = (ToggleButton)findViewById(R.id.bt_leds);
		
		// Disable buttons and store default text color
		btSwitch.setEnabled(false);
		btLeds.setEnabled(false);
		defaultColor = btConnect.getTextColors().getDefaultColor();
		
		// This activity will respond to click events
		btConnect.setOnClickListener(this);
		btSwitch.setOnClickListener(this);
		btLeds.setOnClickListener(this);
		
		if(D) Log.d(TAG, "exit setup()");
	}

	private void connect() {

		if(D) Log.d(TAG, "enter connect()");

		// Show a progress dialog
		ProgressDialog progressDialog = ProgressDialog.show(this, "", getString(R.string.dialog_connecting), true, false);
		
		// Start the connect thread
		ConnectThread connectThread = new ConnectThread(progressDialog, handler);
		connectThread.start();
		if(D) Log.d(TAG, "connect thread started");

		// Start the wait thread linked to connect thread
		new WaitThread(connectThread, TIMEOUT).start();
		if(D) Log.d(TAG, "wait thread started");
		
		if(D) Log.d(TAG, "exit connect()");
	}

	private void disconnect() {
		
		if(D) Log.d(TAG, "enter disconnect()");
		
		// Close bluetooth socket
		if(btSocket != null)
		try {
			btSocket.close();
		} catch (IOException e) {}

		// Reset buttons and disable them
		btSwitch.setChecked(false);
		btLeds.setChecked(false);		
		btSwitch.setEnabled(false);
		btLeds.setEnabled(false);
		
		// Change btConnect 
		btConnect.setCompoundDrawablesWithIntrinsicBounds(R.drawable.bt_disconnected, 0, 0, 0);
		btConnect.setTextColor(defaultColor);
		btConnect.setText(R.string.bt_connect);
		
		connected = false;
		
		if(D) Log.d(TAG, "exit disconnect()");
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		if(D) Log.d(TAG, "enter onActivityResult()");

		switch(requestCode) {

		// After having requested to enable bluetooth
		case REQUEST_ENABLE_BT:

			// Request was successful, setup GUI
			if(resultCode == Activity.RESULT_OK) {
				if(D) Log.d(TAG, "RESULT_OK");
				setup();
			}
			
			// Request wasn't successful, close app
			else {
				if(D) Log.d(TAG, "RESULT_NOT_OK");
				Toast.makeText(this, R.string.toast_bluetooth_not_enabled, Toast.LENGTH_SHORT).show();
				finish();
			}
			break;

		// After having requested to choose bluetooth device
		case REQUEST_CONNECT_DEVICE:

			// User chose a device, get device name/address and perform connection
			if(resultCode == Activity.RESULT_OK) {
				String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				device = bluetoothAdapter.getRemoteDevice(address);
				connect();
			}
			break;

		// Unknown request
		default: if(D) Log.d(TAG, "Unknown requestCode = " + requestCode);
		}
		
		if(D) Log.d(TAG, "exit onActivityResult()");
	}

	public void onClick(View v) {

		if(D) Log.d(TAG, "enter onClick()");

		switch(v.getId()) {

		// Click on btConnect
		case R.id.bt_connect:

			// if we are not connected, ask user to choose bluetooth device
			if(!connected) {
				Intent serverIntent = new Intent(this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			}
			
			// if we are connected, disconnect
			else disconnect();

			break;

		// Click on btSwitch
		case R.id.bt_switch:

			// Send the command to bluetooth device
			ToggleButton btSwitch = (ToggleButton)v;
			try {
				OutputStream outputStream = btSocket.getOutputStream();
				if(btSwitch.isChecked()) outputStream.write("OUT_ON\n".getBytes());
				else outputStream.write("OUT_OFF\n".getBytes());
			} catch (Exception ex) {}

			break;

		// Click on btLeds
		case R.id.bt_leds:

			// Send the command to bluetooth device
			ToggleButton btLeds = (ToggleButton)v;
			try {
				OutputStream outputStream = btSocket.getOutputStream();
				if(btLeds.isChecked()) outputStream.write("LEDS_ON\n".getBytes());
				else outputStream.write("LEDS_OFF\n".getBytes());
			} catch (Exception ex) {}
		}
		
		if(D) Log.d(TAG, "exit onClick()");
	}

	private class ConnectThread extends Thread {

		private ProgressDialog progressDialog;
		private Handler handler;

		// Constructor, save progressDialog and handler references
		private ConnectThread(ProgressDialog progressDialog, Handler handler) {

			this.progressDialog = progressDialog;
			this.handler = handler;
		}

		public void run() {

			if(D) Log.d(TAG, "ConnectThread enter run()");
			Message message = handler.obtainMessage(CONNECT_THREAD_RESULT);
			Bundle bundle = new Bundle();

			try {

				// Create RFCOMM socket and open connection
				btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
				btSocket.connect();
				if(D) Log.d(TAG, "connected");

				// Get IN/OUT streams
				BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(btSocket.getInputStream()));
				OutputStream outputStream = btSocket.getOutputStream();

				// Send ? and wait for !
				outputStream.write("?\n".getBytes());
				String in = bufferedReader.readLine();
				if(!in.equals("!")) {					
					throw(new IOException());
				}

				// get ABOUT and STATUS strings
				outputStream.write("ABOUT\n".getBytes());
				bundle.putString("about", bufferedReader.readLine());
				outputStream.write("STATUS\n".getBytes());
				bundle.putString("status", bufferedReader.readLine());
				
				// We are connected!
				bundle.putBoolean("connected", true);

			// An error occurred, we are NOT connected
			} catch (IOException e) {

				e.printStackTrace();
				bundle.putBoolean("connected", false);

			// At the end, close progressDialog and send message to handler
			} finally {

				progressDialog.dismiss();					
				message.setData(bundle);
				handler.sendMessage(message);
			}
			
			if(D) Log.d(TAG, "ConnectThread exit run()");
		}
	}

	private class WaitThread extends Thread {

		private Thread workingThread;
		private long timeout;

		public WaitThread(Thread workingThread, long timeout) {

			this.workingThread = workingThread;
			this.timeout = timeout;
		}

		public void run() {

			if(D) Log.d(TAG, "WaitThread enter run()");
			
			// Save start time
			long startTime = System.currentTimeMillis();	
			
			while(workingThread.isAlive()) {

				try {
					workingThread.join(500);
					
					// If more than TIMEOUT seconds passed, close socket to unblock working thread
					if(((System.currentTimeMillis() - startTime) > timeout) && workingThread.isAlive()) {
						if(D) Log.d(TAG, "WaitThread timeout");
						try {
							btSocket.close();
						} catch (IOException e) {}
						
						if(D) Log.d(TAG, "WaitThread interrupted WorkingThread");
						workingThread.join();
					}
				} catch (InterruptedException e) {}				
			}
			if(D) Log.d(TAG, "WaitThread exit run()");
		}		
	}
}

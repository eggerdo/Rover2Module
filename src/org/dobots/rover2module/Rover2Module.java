package org.dobots.rover2module;


import org.dobots.communication.msg.RoboCommands;
import org.dobots.communication.msg.RoboCommands.ControlCommand;
import org.dobots.communication.zmq.ZmqHandler;

import robots.RobotType;
import robots.rover.rover2.ctrl.Rover2;
import robots.rover.rover2.gui.Rover2Robot;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class Rover2Module extends Rover2Robot {
	
	private static final String TAG = "MainActivity";
	
	private boolean mBound;
	
	Messenger mService = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		if (ZmqHandler.getInstance() == null) {
			ZmqHandler.initialize(this);
		} else {
			ZmqHandler.getInstance().udpate(this);
		}
		m_eRobot = RobotType.RBT_ROVER2;
		
		super.onCreate(savedInstanceState);

		Intent intent = new Intent(this, Rover2Service.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}
	
	class IncomingHandler extends Handler {
		
		@Override
		public void handleMessage(Message msg) {
			
			switch(msg.what) {
			
			default:
				m_oUiHandler.dispatchMessage(msg);
			}
			
		}
	}
	
	final Messenger mIncomingMessenger = new Messenger(new IncomingHandler());
	

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			Log.i(TAG, "disconnected from service...");
			setRobot(null);
			mBound = false;
		}
		
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.i(TAG, "connected to service... " + service.toString());
			mBound = true;
			
			mService = new Messenger(service);
			
			setRobot(new Rover2() {
				
				private void sendRPC(String command) {
					sendRPC(command, (Object[])null);
				}
				
				private void sendRPC(String command, Object... parameters) {
					if (!mBound) return;
					
					ControlCommand cmd = RoboCommands.createControlCommand("", command, parameters);
					Message msg = Message.obtain(null, Rover2Service.RPC);
					Bundle bundle = new Bundle();
					bundle.putString("data", cmd.toJSONString());
					msg.setData(bundle);
					try {
						Log.d(TAG, "send rpc: " + cmd.toJSONString());
						mService.send(msg);
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				
				@Override
				public void setHandler(Handler handler) {
					// TODO Auto-generated method stub
				}
				
				@Override
				public void setConnection(String address, int port) {
					sendRPC("setConnection", address, port);
				}
				
				@Override
				public boolean isConnected() {
					// TODO Auto-generated method stub
					return false;
				}
				
				@Override
				public String getID() {
					// TODO Auto-generated method stub
					return "";
				}
				
				@Override
				public void disconnect() {
					sendRPC("disconnect");
				}
				
				@Override
				public void connect() {
					sendRPC("connect");
				}
				
				public void toggleLight() {
					sendRPC("toggleLight");
				}
				
				@Override
				public void toggleInfrared() {
					sendRPC("toggleInfrared");
				}
				
			});
			initRemoteConnection();
			
			onRobotReady();
		}
	};
	
	private void initRemoteConnection() {
		Message msg = Message.obtain(null, Rover2Service.INIT_REMOTECONNECTION);
		msg.replyTo = mIncomingMessenger;
		try {
			mService.send(msg);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void closeRemoteConnection() {
		Message msg = Message.obtain(null, Rover2Service.CLOSE_REMOTECONNECTION);
		try {
			mService.send(msg);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.i(TAG,"onStart");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.i(TAG,"onResume");
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.i(TAG,"onPause");
	}

	@Override
	public void onStop() {
		super.onStop();
		Log.i(TAG,"onStop");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		closeRemoteConnection();
		
		if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
		Log.i(TAG, "onDestroy");
	}

	private void startService() {
		Intent intent = new Intent();
		intent.setClassName("org.dobots.rover2module", "org.dobots.rover2module.Rover2Service");
		ComponentName name = startService(intent);
		Log.i(TAG, "Starting: " + intent.toString());
	}

    private void stopService() {
		Intent intent = new Intent();
		intent.setClassName("org.dobots.rover2module", "org.dobots.rover2module.Rover2Service");
		stopService(intent);
		Log.i(TAG, "Stopping service: " + intent.toString());
//		finish();
	}
    
}

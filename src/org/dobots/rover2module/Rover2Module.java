package org.dobots.rover2module;


import org.dobots.communication.zmq.ZmqHandler;
import org.dobots.rover2module.Rover2Service.LocalBinder;

import robots.RobotType;
import robots.rover.rover2.gui.Rover2Robot;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class Rover2Module extends Rover2Robot {
	
	private static final String TAG = "MainActivity";
	
	private boolean mBound;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		if (ZmqHandler.getInstance() == null) {
			ZmqHandler.initialize(this);
		} else {
			ZmqHandler.getInstance().udpate(this);
		}
		m_eRobot = RobotType.RBT_ROVER2;
		
		super.onCreate(savedInstanceState);
	}

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			Log.i(TAG, "disconnected from service...");
			m_oRobot = null;
			mBound = false;
		}
		
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.i(TAG, "connected to service... " + service.toString());
			LocalBinder binder = (LocalBinder) service;
			m_oRobot = binder.getService();
			onRobotReady();
			mBound = true;
		}
	};

	@Override
	public void onStart() {
		super.onStart();
		Log.i(TAG,"onStart");

		Intent intent = new Intent(this, Rover2Service.class);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
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
		m_oRobot.setHandler(null);
		
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

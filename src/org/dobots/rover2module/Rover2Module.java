package org.dobots.rover2module;


import org.dobots.communication.zmq.ZmqHandler;

import robots.RobotType;
import robots.ctrl.RemoteRobot;
import robots.rover.rover2.ctrl.remote.Rover2Remote;
import robots.rover.rover2.gui.Rover2Robot;
import android.os.Bundle;
import android.util.Log;

public class Rover2Module extends Rover2Robot  {
	
	private static final String TAG = "Rover2Module";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		ZmqHandler.initialize(this);
		
		m_eRobot = RobotType.RBT_ROVER2;
		m_bOwnsRobot = true;

		RemoteRobot robot = new Rover2Remote(this, RobotType.RBT_ROVER2, Rover2Service.class);
		robot.setHandler(m_oUiHandler);
		setRobot(robot);
		
		// zmq handler and robot type have to be assigned before calling the parent's
		// onCreate
		super.onCreate(savedInstanceState);
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
		
//		getRobot().destroy();
		Log.i(TAG, "onDestroy");
	}

//	private void startService() {
//		Intent intent = new Intent();
//		intent.setClassName("org.dobots.rover2module", "org.dobots.rover2module.Rover2Service");
//		ComponentName name = startService(intent);
//		Log.i(TAG, "Starting: " + intent.toString());
//	}
//
//    private void stopService() {
//		Intent intent = new Intent();
//		intent.setClassName("org.dobots.rover2module", "org.dobots.rover2module.Rover2Service");
//		stopService(intent);
//		Log.i(TAG, "Stopping service: " + intent.toString());
////		finish();
//	}
    
}

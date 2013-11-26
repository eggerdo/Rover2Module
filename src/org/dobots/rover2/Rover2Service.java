package org.dobots.rover2;

import org.dobots.aimrobotlibrary.AimRobotService;
import org.dobots.communication.zmq.ZmqHandler;

import robots.remote.RobotServiceBinder;
import robots.rover.rover2.ctrl.Rover2;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class Rover2Service extends AimRobotService {

	private static final String MODULE_NAME = "Rover2Module";
	
	@Override
	public void onCreate() {
		super.onCreate();
		mRobot = new RobotServiceBinder(new Rover2());
	}

	@Override
	public String getModuleName() {
		return MODULE_NAME;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		mRobot.destroy();
	}

}

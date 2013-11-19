package org.dobots.rover2module;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import org.dobots.aim.AimProtocol;
import org.dobots.aim.AimService;
import org.dobots.communication.msg.RoboCommands;
import org.dobots.communication.msg.RoboCommands.BaseCommand;
import org.dobots.communication.msg.RoboCommands.ControlCommand;
import org.dobots.communication.zmq.ZmqHandler;
import org.dobots.communication.zmq.ZmqUtils;
import org.zeromq.ZMQ;

import robots.ctrl.IRemoteRobot;
import robots.rover.rover2.ctrl.Rover2;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class Rover2Service extends AimService implements IRemoteRobot {

	private static final String TAG = "Rover2Service";
	private static final String MODULE_NAME = "Rover2Module";
	
	private final IBinder mBinder = new LocalBinder();
	
	public class LocalBinder extends Binder {
		Rover2Service getService() {
			return Rover2Service.this;
		}
	}

	private Rover2 mRover2;
	
	private Messenger mPortCmdInMessenger = new Messenger(new PortCmdMessengerHandler());
	private Messenger mPortVideoOutMessenger = null;
	
	// socket to forward commands coming in over the messenger to the zmq handler
	ZMQ.Socket m_oZmqForwarder;

	@Override
	protected String getModuleName() {
		return MODULE_NAME;
	}

	@Override
	protected String getTag() {
		return TAG;
	}
	
	protected void defineInMessenger(HashMap<String, Messenger> list) {
		list.put("cmd", mPortCmdInMessenger);
	}

	protected void defineOutMessenger(HashMap<String, Messenger> list) {
		list.put("video", mPortVideoOutMessenger);
	}

	public class PortCmdMessengerHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case AimProtocol.MSG_PORT_DATA: {
				String data = msg.getData().getString("data");
				
				BaseCommand cmd = RoboCommands.decodeCommand(data);
				if (cmd instanceof ControlCommand) {
					// control commands are used as RPC, the command is the
					// name of the method to be called, and the parameters are
					// given to the method in the order that they are defined.
					// works only if the type and order of the parameters is correct
					// and only for primitive types, not for objects!!
					RoboCommands.handleControlCommand((ControlCommand)cmd, mRover2);
				} else {
					if (cmd != null) {
						// Assumption: messages that arrive on this port are for this robot
						// if that is not the case, the robot id has to be added to the header
						cmd.strRobotID = mRover2.getID();
						ZmqUtils.sendCommand(cmd, m_oZmqForwarder);
					}
				}

				break;
			}
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind: " + intent.toString() + ", service: " + mBinder.toString());
		return mBinder;
	}

	public void onCreate() {
		super.onCreate();
		
		if (ZmqHandler.getInstance() == null) {
			ZmqHandler.initialize(this);
		}
		
		m_oZmqForwarder = ZmqHandler.getInstance().obtainCommandSendSocket();
		
		mRover2 = new Rover2();
	}

	public void onDestroy() {
		super.onDestroy();
		
		mRover2.destroy();

		if (ZmqHandler.getInstance() != null) {
			ZmqHandler.getInstance().onDestroy();
		}
	}

	@Override
	public String getID() {
		// TODO Auto-generated method stub
		return mRover2.getID();
	}
	
	@Override
	public void setConnection(String address, int port) {
		Log.i(TAG, "setConnection...");
		mRover2.setConnection(address, port);
	}

	@Override
	public boolean isConnected() {
		Log.i(TAG, "is Connected " + mRover2.isConnected());
		return mRover2.isConnected();
	}

	@Override
	public void connect() {
		Log.i(TAG, "connect...");
		mRover2.connect();
	}

	@Override
	public void disconnect() {
		Log.i(TAG, "disconnect...");
		mRover2.disconnect();
	}

	@Override
	public void setHandler(Handler handler) {
		mRover2.setHandler(handler);
	}

}

package org.dobots.rover2;

import java.util.HashMap;
import java.util.concurrent.Semaphore;

import org.dobots.aim.AimProtocol;
import org.dobots.aim.AimService;
import org.dobots.communication.msg.RoboCommands;
import org.dobots.communication.msg.RoboCommands.BaseCommand;
import org.dobots.communication.msg.RoboCommands.ControlCommand;
import org.dobots.communication.video.IRawVideoListener;
import org.dobots.communication.video.ZmqVideoReceiver;
import org.dobots.communication.zmq.ZmqHandler;
import org.dobots.utilities.DoBotsThread;
import org.dobots.utilities.ThreadMessenger;
import org.dobots.utilities.Utils;
import org.zeromq.ZMQ;

import robots.ctrl.RemoteWrapperRobot;
import robots.rover.rover2.ctrl.Rover2;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

public class RobotService extends AimService {
	
	private static final String TAG = "RobotService";
	private static final String MODULE_NAME = "Rover2Module";
	
	private RemoteWrapperRobot mRobot;
	
	// a ThreadMessenger is a thread with a messenger. messages are
	// handled by that thread
	// a normal Messenger uses the thread that creates the messenger
	// to handle messages (which in this case would be the main (UI)
	// thread
	private ThreadMessenger mPortCmdInReceiver = new ThreadMessenger("PortCmdInMessenger") {
		
		@Override
		public boolean handleIncomingMessage(Message msg) {
			switch (msg.what) {
			case AimProtocol.MSG_PORT_DATA:
				// do we need to check datatype to make sure it is string?
				String data = msg.getData().getString("data");
				BaseCommand cmd = RoboCommands.decodeCommand(data);
				
				if ((cmd instanceof ControlCommand) && ((ControlCommand)cmd).mCommand.equals("setFrameRate")) {
					mVideoForwarder.setFrameRate((Double)((ControlCommand)cmd).getParameter(0));
				} else {
					mRobot.handleCommand(cmd);
				}
				break;
			default:
				return false;
			}
			return true;
		}
	};
	
//	private Messenger mPortVideoOutMessenger = null;
	private VideoForwarder mVideoForwarder;
	
	@Override
	public String getModuleName() {
		return MODULE_NAME;
	}

	@Override
	public String getTag() {
		return TAG;
	}
	
	public void defineInMessenger(HashMap<String, Messenger> list) {
		list.put("cmd", mPortCmdInReceiver.getMessenger());
	}

	public void defineOutMessenger(HashMap<String, Messenger> list) {
		list.put("video", null);
	}

	@Override
	public int getModuleId() {
		return mModuleId;
	}

	@Override
	public void onAimStop() {
		stopSelf();
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind: " + intent.toString());
		return mRobot.getInMessenger().getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		
		ZmqHandler.initialize(this);
		
		mRobot = new RemoteWrapperRobot(new Rover2());
		mVideoForwarder = new VideoForwarder("VideoForwarder");
		mVideoForwarder.start();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		mRobot.destroy();
		mPortCmdInReceiver.destroy();
		mVideoForwarder.stopThread();

		ZmqHandler.destroyInstance();
	}
	
	private class VideoForwarder extends DoBotsThread implements IRawVideoListener {

		private ZmqVideoReceiver mVideoReceiver;
		private ZMQ.Socket mVideoSocket;
		
		private byte[] mFrame;
		
		private Semaphore mSemaphore;

		// xmpp is slooooooow ....
		private double mFrameRate = 0.01;
		
		public VideoForwarder(String threadName) {
			super(threadName);
			
			mSemaphore = new Semaphore(1);
			
			mVideoSocket = ZmqHandler.getInstance().obtainVideoRecvSocket();
			mVideoSocket.subscribe("".getBytes());
			
			mVideoReceiver = new ZmqVideoReceiver(mVideoSocket);
			mVideoReceiver.setRawVideoListner(this);
			mVideoReceiver.start();
		}

		@Override
		public void shutDown() {
			mVideoReceiver.close();
		}

		@Override
		protected void execute() {
			if (getOutMessenger("video") == null) return;
			if (mFrame == null) return;
			
			long start = System.currentTimeMillis();
			
			try {
				mSemaphore.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}
			
			String msgData = android.util.Base64.encodeToString(mFrame, android.util.Base64.NO_WRAP);
			mAimConnectionHelper.sendData(getOutMessenger("video"), "1234");
			
			mFrame = null;
			
			mSemaphore.release();
			
			long end = System.currentTimeMillis();
			
			if (mFrameRate > 0) {
				int sleep = (int) ((1000 / mFrameRate) - (end - start));
				if (sleep > 0) {
					Utils.waitSomeTime(sleep);
				}
			}
		}

		@Override
		public void onFrame(byte[] rgb, int rotation) {
			// if semaphore is available, we assign the frame
			// otherwise if a frame is being processed we
			// drop this frame
			if (mSemaphore.tryAcquire()) {
				mFrame = rgb;
				mSemaphore.release();
			}
		}
		
		public void setFrameRate(double rate) {
			mFrameRate = rate;
		}
	}

}

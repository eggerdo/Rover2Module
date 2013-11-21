package org.dobots.rover2module;

import java.util.ArrayList;
import java.util.HashMap;

import org.dobots.aim.AimProtocol;
import org.dobots.aim.AimService;
import org.dobots.communication.msg.RoboCommands;
import org.dobots.communication.msg.RoboCommands.BaseCommand;
import org.dobots.communication.msg.RoboCommands.ControlCommand;
import org.dobots.communication.zmq.ZmqHandler;
import org.dobots.communication.zmq.ZmqUtils;
import org.dobots.utilities.Utils;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeromq.ZMQ;

import robots.ctrl.RemoteRobot;
import robots.rover.rover2.ctrl.Rover2;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class Rover2Service extends AimService {
	
	private static final String TAG = "Rover2Service";
	private static final String MODULE_NAME = "Rover2Module";
	
//	private final IBinder mBinder = new LocalBinder();
//	
//	public class LocalBinder extends Binder {
//		Rover2Service getService() {
//			return Rover2Service.this;
//		}
//	}

	private Rover2 mRobot;
	
	private Messenger mPortCmdInMessenger = new Messenger(new PortCmdMessengerHandler());
	private Messenger mPortVideoOutMessenger = null;
	
	// socket to forward commands coming in over the messenger to the zmq handler
	private ZMQ.Socket m_oZmqForwarder;

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

	class PortCmdMessengerHandler extends Handler {
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
					RoboCommands.handleControlCommand((ControlCommand)cmd, mRobot);
				} else {
					if (cmd != null) {
						// Assumption: messages that arrive on this port are for this robot
						// if that is not the case, the robot id has to be added to the header
						cmd.strRobotID = mRobot.getID();
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
	
	private Receiver mReceiver;
	class Receiver extends Thread {
		
		private Messenger mMessenger = null;
		
		public Messenger getMessenger() {
			int n = 0;
			while (mMessenger == null) {
				Utils.waitSomeTime(1);
				n++;
			}
			return mMessenger;
		}
		
		public void run() {
			
			Looper.prepare();
			mMessenger = new Messenger(new Handler() {
				
				@Override
				public void handleMessage(Message msg) {
					
					switch(msg.what) {
					case RemoteRobot.RPC:
						String data = msg.getData().getString("data");

						Log.d(TAG, "recv rpc: " + data);
						
						BaseCommand cmd = RoboCommands.decodeCommand(data);
						if (cmd instanceof ControlCommand) {
							Object result = RoboCommands.handleControlCommand((ControlCommand)cmd, mRobot);
							if (result != null) {
								sendReply(msg.replyTo, (ControlCommand)cmd, result);
							}
						}
						break;
					case RemoteRobot.INIT:
						mServiceOutMessengers.add(msg.replyTo);
						
						Message reply = Message.obtain(null, RemoteRobot.INIT);
						Bundle bundle = new Bundle();
						bundle.putString("robot_id", mRobot.getID());
						reply.setData(bundle);
						try {
							msg.replyTo.send(reply);
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						break;
					case RemoteRobot.CLOSE:
						mServiceOutMessengers.remove(msg.replyTo);
						break;
//					case RemoteRobot.REGISTER:
//						mServiceOutMessengers
//						break;
					default:
						super.handleMessage(msg);
					}
					
				}

			});
			Looper.loop();
			
		};
	};
	
	private Messenger getInMessenger() {
		return mReceiver.getMessenger();
	}
	
	private void sendReply(Messenger replyTo, ControlCommand cmd, Object result) {
		
		JSONObject replyJson = new JSONObject();
		try {
			replyJson.put(cmd.mCommand, result);

			Message reply = Message.obtain(null, RemoteRobot.REPLY);
			
			Bundle bundle = new Bundle();
			bundle.putString("data", replyJson.toString());
			reply.setData(bundle);

			Log.d(TAG, "send reply: " + replyJson.toString());
			
			replyTo.send(reply);
			
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
//	private Messenger mServiceInMessenger = new Messenger(new ServiceInMessageHandler());
	private ArrayList<Messenger> mServiceOutMessengers = new ArrayList<Messenger>();
	
	private Handler robotHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {

			Message newMessage = Message.obtain();
			newMessage.copyFrom(msg);
			
			for (Messenger messenger : mServiceOutMessengers) {
				try {
					messenger.send(newMessage);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	};
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "onBind: " + intent.toString());
		return getInMessenger().getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		
		ZmqHandler.initialize(this);
		
		m_oZmqForwarder = ZmqHandler.getInstance().obtainCommandSendSocket();
		
		mReceiver = new Receiver();
		mReceiver.start();
		
		mRobot = new Rover2();
		mRobot.setHandler(robotHandler);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		mRobot.destroy();

		ZmqHandler.destroyInstance();
	}

}

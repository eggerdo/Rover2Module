package org.dobots.aim;

import java.util.HashMap;
import java.util.Map;


import android.app.Service;
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

public abstract class AimService extends Service {

	// TO BE DEFINED IN THE SUB-CLASSES
	//	private static final String MODULE_NAME = "";
	protected abstract String getModuleName();

	// TO BE DEFINED IN THE SUB-CLASSES
	//	private static final String TAG = ""
	protected abstract String getTag();
	
	/*
	 * TO BE DEFINED IN THE SUB-CLASSES
	 * 
	 * E.g.
	 * protected void defineInMessenger(HashMap<String, Messenger> list) {
	 * 		list.put("bmp", mPortBmpInMessenger);
	 * }
	 */
	protected abstract void defineInMessenger(HashMap<String, Messenger> list);

	/*
	 * TO BE DEFINED IN THE SUB-CLASSES
	 * 
	 * E.g.
	 * protected void defineOutMessenger(HashMap<String, Messenger> list) {
	 * 		list.put("jpg", mPortJpgOutMessenger);
	 * }
	 */
	protected abstract void defineOutMessenger(HashMap<String, Messenger> list);

	// TODO: adjustable id, multiple modules
	protected int mModuleId = 0; 

	protected Messenger mToMsgService = null;
	protected final Messenger mFromMsgService = new Messenger(new IncomingMsgHandler());
	protected boolean mMsgServiceIsBound;
	
	// key is the name of the port, value is the messenger assigned to that port
	protected HashMap<String, Messenger> mInMessenger = new HashMap<String, Messenger>();
	protected HashMap<String, Messenger> mOutMessenger = new HashMap<String, Messenger>();

	private ServiceConnection mMsgServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been established, giving us the service object
			// we can use to interact with the service.  We are communicating with our service through an IDL
			// interface, so get a client-side representation of that from the raw service object.
			mToMsgService = new Messenger(service);
			Message msg = Message.obtain(null, AimProtocol.MSG_REGISTER);
			Bundle bundle = new Bundle();
			bundle.putString("module", getModuleName());
			bundle.putInt("id", 0); // TODO: adjustable id, multiple modules
			msg.setData(bundle);
			msgSend(msg);
			
			for (Map.Entry<String, Messenger> entry : mInMessenger.entrySet())
			{
				Message msgPort = Message.obtain(null, AimProtocol.MSG_SET_MESSENGER);
				msgPort.replyTo = entry.getValue();
				Bundle bundlePort = new Bundle();
				bundlePort.putString("module", getModuleName());
				bundlePort.putInt("id", mModuleId); 
				bundlePort.putString("port", entry.getKey());
				msgPort.setData(bundlePort);
				msgSend(mToMsgService, msgPort);
			}


			Log.i(getTag(), "Connected to MsgService: " + mToMsgService.toString());
		}
		
		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected: its process crashed.
			mToMsgService = null;
			Log.i(getTag(), "Disconnected from MsgService");
		}
	};

	// Handle messages from MsgService
	class IncomingMsgHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case AimProtocol.MSG_SET_MESSENGER:
				Log.i(getTag(), "set messenger");
				String port = msg.getData().getString("port");
				if (mOutMessenger.containsKey(port)) {
					mOutMessenger.put(port, msg.replyTo);
				}
				break;
			case AimProtocol.MSG_STOP:
				Log.i(getTag(), "stopping");
				stopSelf();
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		Log.i(getTag(), "onBind: " + intent.toString());
		return null;
	}

	public void onCreate() {
		super.onCreate();
		
		defineInMessenger(mInMessenger);
		defineOutMessenger(mOutMessenger);
		
		bindToMsgService();
		
		Log.i(getTag(), "onCreate");
	}

	public void onDestroy() {
		super.onDestroy();
		unbindFromMsgService();
		Log.i(getTag(), "onDestroy");
	}

	// Called when all clients have disconnected from a particular interface of this service.
	@Override
	public boolean onUnbind(final Intent intent) {
		return super.onUnbind(intent);
	}

	// Deprecated since API level 5 (android 2.0)
	@Override
	public void onStart(Intent intent, int startId) {
		//		handleStartCommand(intent);
	}

	// Called each time a client uses startService()
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//	    handleStartCommand(intent);
		// We want this service to continue running until it is explicitly stopped, so return sticky.
		return START_STICKY;
	}

	void bindToMsgService() {
		// Establish a connection with the service.  We use an explicit class name because there is no reason to be 
		// able to let other applications replace our component.
		Intent intent = new Intent();
		intent.setClassName("org.dobots.dodedodo", "org.dobots.dodedodo.MsgService");
		bindService(intent, mMsgServiceConnection, Context.BIND_AUTO_CREATE);
		mMsgServiceIsBound = true;
		Log.i(getTag(), "Binding to msgService");
	}

	void unbindFromMsgService() {
		if (mMsgServiceIsBound) {
			// If we have received the service, and registered with it, then now is the time to unregister.
			if (mToMsgService != null) {
				Message msg = Message.obtain(null, AimProtocol.MSG_UNREGISTER);
				Bundle bundle = new Bundle();
				bundle.putString("module", getModuleName());
				bundle.putInt("id", mModuleId);
				msg.setData(bundle);
				msgSend(msg);
			}
			// Detach our existing connection.
			unbindService(mMsgServiceConnection);
			mMsgServiceIsBound = false;
			Log.i(getTag(), "Unbinding from msgService");
		}
	}

	// Send a msg to the msgService
	protected void msgSend(Message msg) {
		if (!mMsgServiceIsBound) {
			Log.i(getTag(), "Can't send message to service: not bound");
			return;
		}
		try {
			msg.replyTo = mFromMsgService;
			mToMsgService.send(msg);
		} catch (RemoteException e) {
			Log.i(getTag(), "Failed to send msg to service. " + e);
			// There is nothing special we need to do if the service has crashed.
		}
	}

	// Send a msg to some messenger
	protected void msgSend(Messenger messenger, Message msg) {
		if (messenger == null || msg == null)
			return;
		try {
			//msg.replyTo = mFromMsgService;
			messenger.send(msg);
		} catch (RemoteException e) {
			Log.i(getTag(), "failed to send msg to service. " + e);
			// There is nothing special we need to do if the service has crashed.
		}
	}

}

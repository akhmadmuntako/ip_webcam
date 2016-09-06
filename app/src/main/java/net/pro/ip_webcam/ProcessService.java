package net.pro.ip_webcam;

import android.app.Service;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class ProcessService extends Service {

  private static final String LOG_TAG = "ProcessService";

  /**
   * Messages that sent from activity.
   */
  private final static int MSG_BASE_FROM_ACTIVITY = 0;
  public final static int MSG_START_OBSERVE_PREVIEW = MSG_BASE_FROM_ACTIVITY + 1;
  public final static int MSG_START_RECORD = MSG_BASE_FROM_ACTIVITY + 2;
  public final static int MSG_STOP_RECORD = MSG_BASE_FROM_ACTIVITY + 3;
  public final static int MSG_RELEASE_ALL = MSG_BASE_FROM_ACTIVITY + 4;
  
  /**
   * Messages that sent to Activities, who are responsible to define the handling procedure.
   */
  private final static int MSG_BASE_TO_ACTIVITY = 50;
  public final static int MSG_SHOW_MOTION_RECT = MSG_BASE_TO_ACTIVITY + 1;

  /**
   * Messages that sent from other parts of the core.
   */
  private final static int MSG_BASE_CORE = 100;
  public final static int MSG_MOTION_DETECTED = MSG_BASE_CORE + 1;

  private Handler mHandler = null;
  private Messenger mMessengerFromActivityToService = null;
  private Messenger mMessengerFromServiceToActivity = null;
  private MotionDetector mMotionDetector = null;
  private VideoRecorder mVideoRecorder = null;
  
  // For the convenience of communicating with other classes from core, let them visit ProcessService through this singleton.
  private static ProcessService mInstance = null;

  @Override
  public IBinder onBind(Intent intent) {
    // TODO Auto-generated method stub
    init();
    mInstance = this;
    return mMessengerFromActivityToService.getBinder();
  }
  
  public static ProcessService getInstance() {
    return mInstance;
  }

  private void init() {
    if (mHandler != null) {
      return;
    }
    HandlerThread handlerThread = new HandlerThread("ServiceHandler");
    handlerThread.start();
    mHandler = new Handler(handlerThread.getLooper()) {
      @Override
      public void handleMessage(Message msg) {
        switch (msg.what) {
          case MSG_START_OBSERVE_PREVIEW:
            mMessengerFromServiceToActivity = (Messenger) msg.replyTo;
            mMotionDetector =
                new MotionDetector(msg.arg1, msg.arg2, mMessengerFromActivityToService);
            break;

          case MSG_START_RECORD:
            mMessengerFromServiceToActivity = (Messenger) msg.replyTo;
            if (Controller.logEnabled()) {
              Log.d(LOG_TAG, "start to record camera-id: " + msg.arg1);
            }
            mVideoRecorder = new VideoRecorder(msg.arg1);
            mVideoRecorder.startRecord();
            break;
            
          case MSG_STOP_RECORD:
            if (mVideoRecorder != null) {
              if (Controller.logEnabled()) {
                Log.d(LOG_TAG, "stop recording.");
              }
              mVideoRecorder.stopRecord();
              mVideoRecorder = null;
            }
            break;

          case MSG_RELEASE_ALL:
            if (mMotionDetector != null) {
              mMotionDetector.destroy();
              mMotionDetector = null;
            }
            if (mVideoRecorder != null) {
              if (Controller.logEnabled()) {
                Log.d(LOG_TAG, "stop recording.");
              }
              mVideoRecorder.stopRecord();
              mVideoRecorder = null;
            }
            mHandler = null;
            mMessengerFromServiceToActivity = null;
            mMessengerFromActivityToService = null;
            break;

          case MSG_MOTION_DETECTED:
            Rect rect = (Rect) msg.obj;
            if (Controller.logEnabled()) {
              Log.d(LOG_TAG, "motion detected, path:" + rect);
            }
            motionCallback(rect);
            if (mVideoRecorder != null) {
              mVideoRecorder.notifyMotionDetected();
            }
            break;

        }
      }
    };
    mMessengerFromActivityToService = new Messenger(mHandler);
  }

  private void motionCallback(Rect rect) {
    if (mMessengerFromServiceToActivity != null) {
      try {
        mMessengerFromServiceToActivity.send(Message.obtain(null, MSG_SHOW_MOTION_RECT, rect));
      } catch (RemoteException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }
  
  public void previewMaybeChanged() {
    if (mMotionDetector != null) {
      mMotionDetector.restartWhenPreviewSettingChanged();
    }
  }

}

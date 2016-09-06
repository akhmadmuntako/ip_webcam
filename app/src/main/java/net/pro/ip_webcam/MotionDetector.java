package net.pro.ip_webcam;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.LinkedList;

public class MotionDetector {
  private static final String LOG_TAG = "MotionDetector";

  private Handler mHandler = new PrivateHandler();
  private final static int INVALID_IMAGE_HANDLE = -1;
  /**
   * Used to store the history frames to detect motion.
   *    Currently it has two frames, frame_0 is more latest, and frame_1 is more elder.
   */
  private int[] mNeighborFrames = new int[2];
  private int mSensitivity = 0;
  private Messenger mMessengerToService = null;
  private int mCameraId = -1;
  private final int TIME_MILLIS_DETECT_SPAN = 1 * 1000;
  private long mLastDetectTime = timeMillis();
  private boolean mStoped = false;
  // Buffers stuff that are used for Camera.PreviewCallback.
  private LinkedList<byte[]> mPreviewBuffers = null;
  private final static int sPreviewBufferNum = 2;
  private final static int sPreviewBufferSize = 2 * 1024 * 1024;

  public MotionDetector(int cameraId, int sensitivity, Messenger callbackMessenger) {
    mCameraId = cameraId;
    mSensitivity = sensitivity;
    mMessengerToService = callbackMessenger;
    for (int i = 0, count = mNeighborFrames.length; i < count; i ++) {
      mNeighborFrames[i] = INVALID_IMAGE_HANDLE;
    }
    registerCallbacks();
  }

  private void registerCallbacks() {
    for (int i = 0; i < sPreviewBufferNum; i ++) {
      try {
        byte[] buffer = new byte[sPreviewBufferSize];
        if (mPreviewBuffers == null) {
          mPreviewBuffers = new LinkedList<byte[]>();
        }
        mPreviewBuffers.add(buffer);
      } catch (OutOfMemoryError e) {
        break;
      }
    }
    CameraManager.getInstance().registerPreviewCallback(mCameraId, mPreviewCallback, mPreviewBuffers);
  }
  
  public void restartWhenPreviewSettingChanged() {
    registerCallbacks();
  }

  @SuppressLint("HandlerLeak")
  private class PrivateHandler extends Handler {
    private final static int MSG_CAPTURE_DONE = 1;

    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_CAPTURE_DONE:
          int capturedImageNativeHandle = msg.arg1;
          if ((mNeighborFrames[0] == INVALID_IMAGE_HANDLE || mNeighborFrames[1] == INVALID_IMAGE_HANDLE) && capturedImageNativeHandle != INVALID_IMAGE_HANDLE) {
            mNeighborFrames[1] = mNeighborFrames[0];
            mNeighborFrames[0] = capturedImageNativeHandle;
          } else if (mNeighborFrames[0] != INVALID_IMAGE_HANDLE && mNeighborFrames[1] != INVALID_IMAGE_HANDLE && capturedImageNativeHandle != INVALID_IMAGE_HANDLE){
            Rect result = ImageComparor.getInstance().compare(mNeighborFrames[1], mNeighborFrames[0], capturedImageNativeHandle, mSensitivity);
            ImageComparor.getInstance().releaseLocalImage(mNeighborFrames[1]);
            mNeighborFrames[1] = mNeighborFrames[0];
            mNeighborFrames[0] = capturedImageNativeHandle;
            if (result != null) {
              if (Controller.logEnabled()) {
                Log.d(LOG_TAG, "motion detected by Motion Detector, send message to service.");
              }
              Message msgToService =
                  Message.obtain(null, ProcessService.MSG_MOTION_DETECTED, result);
              try {
                mMessengerToService.send(msgToService);
              } catch (RemoteException e) {
                if (Controller.logEnabled()) {
                  Log.d(LOG_TAG,
                      "error occured when sending message to service from motion-detector.");
                }
                e.printStackTrace();
              }
            }
          }
          break;
      }
    }
  }

  private PreviewCallback mPreviewCallback = new PreviewCallback() {

    @Override
    public void onPreviewFrame(byte[] frameData, Camera cameraObj) {
      if (mStoped) {
        cameraObj.setPreviewCallbackWithBuffer(null);
        return;
      }
      long currentTime = timeMillis();
      if (currentTime - mLastDetectTime < TIME_MILLIS_DETECT_SPAN) {
        return;
      } else {
        mLastDetectTime = currentTime;
      }
      Size previewSize = CameraManager.getInstance().getPreviewSize(mCameraId);
      if (previewSize == null) {
          return;
      }
      int newHandle = ImageComparor.getInstance().convertYUVImageToLocalImage(frameData, frameData.length, previewSize.width, previewSize.height);
      // Make sure that the messages and decoded images will not pile.
      mHandler.removeMessages(PrivateHandler.MSG_CAPTURE_DONE);
      mHandler.sendMessage(Message.obtain(null, PrivateHandler.MSG_CAPTURE_DONE, newHandle, 0));
    }
    
  };

  public void destroy() {
    mStoped = true;
    mHandler = null;
    for (int i = 0, count = mNeighborFrames.length; i < count; i ++) {
      if (mNeighborFrames[i] != INVALID_IMAGE_HANDLE) {
        ImageComparor.getInstance().releaseLocalImage(mNeighborFrames[i]);
        mNeighborFrames[i] = INVALID_IMAGE_HANDLE;
      }
    }
    mMessengerToService = null;
  }

  @SuppressWarnings("unused")
  private boolean saveBitmapToDisk(Bitmap bitmap, String path) {
    try {
      bitmap.compress(CompressFormat.PNG, 90, new FileOutputStream(new File(path)));
      return true;
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return false;
    }
  }
  
  /**
   * This function get the time since boot, and it's NOT GMT time.
   * @return relative time in milli-seconds.
   */
  private static long timeMillis() {
    return (long) (System.nanoTime() * 0.000001);
  }

}

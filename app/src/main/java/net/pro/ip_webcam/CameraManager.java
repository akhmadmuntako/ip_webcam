package net.pro.ip_webcam;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

public class CameraManager {
  private final static String LOG_TAG = "CameraManager";
  private static CameraManager mInstance = null;
  private static final int mCameraQuality = 90;

  private Hashtable<Integer, Camera> mCameras = new Hashtable<Integer, Camera>(2);
  private Hashtable<Integer, MediaRecorder> mRecorders = new Hashtable<Integer, MediaRecorder>(2);
  private Hashtable<Integer, SurfaceHolder> mPreviews = new Hashtable<Integer, SurfaceHolder>(2);
  private Hashtable<Integer, SoftReference<Camera.Parameters>> mCamerasParams = new Hashtable<Integer, SoftReference<Camera.Parameters>>();

  public static CameraManager getInstance() {
    if (mInstance == null) {
      return mInstance = new CameraManager();
    } else {
      return mInstance;
    }
  }

  public void startPreview(SurfaceHolder holder, int cameraId, int rotation, int width, int height) {
    startCamera(cameraId);
    Camera camera = mCameras.get(cameraId);
    try {
      // Stop the previous preview first.
      camera.stopPreview();
      assert (holder != null);
      camera.setPreviewDisplay(holder);
      mPreviews.put(cameraId, holder);
      // Set Width and Height.
      Camera.Size previewSize = getOptimalPreviewSize(cameraId, width, height);
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "optimal preview and picture size: " + previewSize.width + ", "
            + previewSize.height);
      }
      assert (previewSize != null);
      Camera.Parameters parameters = camera.getParameters();
      parameters.setPreviewSize(previewSize.width, previewSize.height);
      parameters.setPreviewFormat(ImageFormat.NV21);
      // Set parameters for snapshot.
      parameters.setPictureFormat(ImageFormat.JPEG);
      parameters.setPictureSize(previewSize.width, previewSize.height);
      parameters.setJpegQuality(mCameraQuality);
      // Set Orientation.
      int cameraRotation = getOptimalRotation(rotation, cameraId);
      parameters.setRotation(cameraRotation);
      camera.setDisplayOrientation(cameraRotation);
      // Set auto-focus
      parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      // Set into the parameters.
      camera.setParameters(parameters);
      // Start it.
      camera.startPreview();
      // Store the parameters for latter use.
      mCamerasParams.put(cameraId, new SoftReference<Camera.Parameters>(parameters));
    } catch (IOException e) {
      e.printStackTrace();
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
    if (ProcessService.getInstance() != null) {
      ProcessService.getInstance().previewMaybeChanged();
    }
  }

  private class PreviewCallbackGlue implements Camera.PreviewCallback {
    private Camera.PreviewCallback mRealCallback = null;
    WeakReference<LinkedList<byte[]>> mCallbackBuffers = null;

    public PreviewCallbackGlue(Camera camera, Camera.PreviewCallback cb, LinkedList<byte[]> buffers) {
      mRealCallback = cb;
      if (buffers == null || buffers.size() == 0) {
        camera.setPreviewCallback(cb);
      } else {
        mCallbackBuffers = new WeakReference<LinkedList<byte[]>>(buffers);
        for (int i = 0, count = buffers.size(); i < count; i ++) {
          camera.addCallbackBuffer(buffers.get(i));
        }
        camera.setPreviewCallbackWithBuffer(this);
      }
    }

    @Override
    public void onPreviewFrame(byte[] frameData, Camera cameraObj) {
      mRealCallback.onPreviewFrame(frameData, cameraObj);
      if (mCallbackBuffers != null) {
        cameraObj.addCallbackBuffer(frameData);
      }
    }
  }

  public boolean registerPreviewCallback(int cameraId, Camera.PreviewCallback cb, LinkedList<byte[]> buffers) {
    Camera camera = mCameras.get(cameraId);
    if (camera == null) {
      if (Controller.logEnabled()) {
        Log.e(LOG_TAG, "camera " + cameraId + " not started yet, can not register Preview Callback.");
      }
      return false;
    }
    new PreviewCallbackGlue(camera, cb, buffers);
    return true;
  }
  
  private Camera.Parameters getCameraParameters(int cameraId) {
    Camera camera = mCameras.get(cameraId);
    if (camera == null) {
      if (Controller.logEnabled()) {
        Log.e(LOG_TAG, "camera " + cameraId + " is not started, can not get its preview-size.");
      }
      return null;
    } else {
      SoftReference<Camera.Parameters> paramsRef = mCamerasParams.get(cameraId);
      Camera.Parameters params = paramsRef.get();
      if (paramsRef == null || params == null) {
        params = camera.getParameters();
        mCamerasParams.put(cameraId, new SoftReference<Camera.Parameters>(params));
      }
      return params;
    }
  }
  
  public Camera.Size getPreviewSize(int cameraId) {
    Camera.Parameters params = getCameraParameters(cameraId);
    if (params != null) {
      return getCameraParameters(cameraId).getPreviewSize();
    } else {
      return null;
    }
  }
  
  /**
   * Record video to file
   * 
   * @param cameraId The camera to use.
   * @param savePath The file path to save the video.
   * @param isAppend Whether use append mode when manipulating the video file.
   * @return true for success, or return false.
   */
  // TODO: append functionality not completed.
  public boolean startRecord(int cameraId, String savePath, boolean isAppend) {
    Camera camera = mCameras.get(cameraId);
    if (camera == null) {
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "camera instance not initialized, can not record video.");
        return false;
      }
    }
    MediaRecorder recorder = mRecorders.get(cameraId);
    if (recorder != null) {
      if (isAppend) {
        if (Controller.logEnabled()) {
          Log.d(LOG_TAG, "already recording, don't need to resume recording.");
        }
        return true;
      }
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "there is an existing media-recorder, and then want to record again, illeagal, but anyway go on with it.");
      }
      destroyMediaRecorder(recorder);
      recorder = null;
    }
    // Step 1: Unlock and set camera to MediaRecorder
    camera.unlock();
    recorder = new MediaRecorder();
    recorder.setCamera(camera);

      // 2: Set source of video and audio
      recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
      recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);

      // 3: Set parameters, Following code does the same as getting a CamcorderProfile (but customizable)
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    // 3.1 Video Settings
      Camera.Size videoSize = mCamerasParams.get(cameraId).get().getPreviewSize();
      recorder.setVideoSize(videoSize.width, videoSize.height);
      recorder.setVideoFrameRate(10);
      recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
      recorder.setVideoEncodingBitRate(500000);
    // 3.2 Audio Settings
      recorder.setAudioChannels(1);
      recorder.setAudioSamplingRate(44100);
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
      recorder.setAudioEncodingBitRate(16);
    
    // Step 4: Set output file
    //TODO: want to append to a previous video file after pause.
    recorder.setOutputFile(savePath);

    // Step 5: Set the preview output
//    recorder.setPreviewDisplay(mPreviews.get(cameraId).getSurface());

    // Step 6: Rotate the output video
    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, cameraInfo);
    recorder.setOrientationHint(cameraInfo.orientation);
    // Step 7: Set recording error callback
    recorder.setOnErrorListener(new RecordingErrorHandler());
    // Step 8: Prepare configured MediaRecorder
    try {
      recorder.prepare();
    } catch (IllegalStateException e) {
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
      }
      destroyMediaRecorder(recorder);
      // Camera.unlock() may let the previously set preview-call-back out of function, notify ProcessSerivce to redo the register.
      if (ProcessService.getInstance() != null) {
        ProcessService.getInstance().previewMaybeChanged();
      }
      return false;
    } catch (IOException e) {
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "IOException preparing MediaRecorder: " + e.getMessage());
      }
      destroyMediaRecorder(recorder);
      // Camera.unlock() may let the previously set preview-call-back out of function, notify ProcessSerivce to redo the register.
      if (ProcessService.getInstance() != null) {
        ProcessService.getInstance().previewMaybeChanged();
      }
      return false;
    }
    
    // Step 9: Start recording
    try {
      recorder.start();
    } catch (IllegalStateException e) {
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "IllegalStateException starting MediaRecorder: " + e.getMessage());
      }
      destroyMediaRecorder(recorder);
      // Camera.unlock() may let the previously set preview-call-back out of function, notify ProcessSerivce to redo the register.
      if (ProcessService.getInstance() != null) {
        ProcessService.getInstance().previewMaybeChanged();
      }
      return false;
    }
    
    // Add the new recorder to table.
    mRecorders.put(cameraId, recorder);
    // Camera.unlock() may let the previously set preview-call-back out of function, notify ProcessSerivce to redo the register.
    if (ProcessService.getInstance() != null) {
      ProcessService.getInstance().previewMaybeChanged();
    }
    return true;
  }
  
  private class RecordingErrorHandler implements MediaRecorder.OnErrorListener {

    @Override
    public void onError(MediaRecorder arg0, int arg1, int arg2) {
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "Error occured when recording.");
      }
      destroyMediaRecorder(arg0);
      // Camera.unlock() may let the previously set preview-call-back out of function, notify ProcessSerivce to redo the register.
      if (ProcessService.getInstance() != null) {
        ProcessService.getInstance().previewMaybeChanged();
      }
    }
    
  }
  
  public boolean stopRecord(int cameraId) {
    MediaRecorder recorder = mRecorders.get(cameraId);
    if (recorder != null) {
      try {
        recorder.stop();
      } catch (IllegalStateException e) {
        if (Controller.logEnabled()) {
          Log.d(LOG_TAG, "IllegalStateException stopping MediaRecorder: " + e.getMessage());
        }
        destroyMediaRecorder(recorder);
        return false;
      } catch (Throwable t) {
        if (Controller.logEnabled()) {
          Log.d(LOG_TAG, "Runtime error stopping MediaRecorder: " + t.getMessage());
        }
        destroyMediaRecorder(recorder);
        return false;
      }
      destroyMediaRecorder(recorder);
      mRecorders.remove(cameraId);
      mCameras.get(cameraId).lock();
    }
    return true;
  }
  
  private void destroyMediaRecorder(MediaRecorder recorder) {
    if (recorder != null) {
      recorder.reset();
      recorder.release();
    }
  }

//  public boolean takePicture(int cameraId, Camera.PictureCallback callback) {
//    Camera camera = mCameras.get(cameraId);
//    if (camera != null) {
//      // Just in case that the camera was released.
//      try {
//        camera.takePicture(null, null, callback);
//      } catch (Throwable t) {
//        return false;
//      }
//      return true;
//    } else {
//      if (Controller.logEnabled()) {
//        Log.e(LOG_TAG, "camera not started, cannot take picture.");
//      }
//      return false;
//    }
//  }
//
//  public boolean resumePreviewAfterSnapshot(int cameraId) {
//    Camera camera = mCameras.get(cameraId);
//    if (camera != null) {
//      camera.startPreview();
//      return true;
//    } else {
//      return false;
//    }
//  }

  public void stopPreview(int cameraId) {
    Camera camera = mCameras.get(cameraId);
    if (camera != null) {
      destroyMediaRecorder(mRecorders.get(cameraId));
      mRecorders.remove(cameraId);
      mPreviews.remove(cameraId);
      camera.stopPreview();
    } else {
      if (Controller.logEnabled()) {
        Log.e(LOG_TAG, "camera " + cameraId + " is already stoped, don't stop twice.");
      }
    }
  }

  public void releaseCarema(int cameraId) {
    Camera camera = mCameras.get(cameraId);
    if (camera != null) {
      destroyMediaRecorder(mRecorders.get(cameraId));
      mRecorders.remove(cameraId);
      mPreviews.remove(cameraId);
      camera.release();
      mCameras.remove(cameraId);
      camera = null;
    } else {
      if (Controller.logEnabled()) {
        Log.e(LOG_TAG, "you are releasing camera " + cameraId + " which is already released or was not initialized at all.");
      }
    }
  }

  private boolean startCamera(int cameraId) {
    Camera camera = mCameras.get(cameraId);
    if (camera != null) {
      return true;
    }
    try {
      camera = Camera.open();
    } catch (RuntimeException ignore) {
    }
    if (camera == null) {
      if (Controller.logEnabled()) {
        Log.e(LOG_TAG, "open camera failed...");
      }
      return false;
    } else {
      mCameras.put(cameraId, camera);
      return true;
    }
  }

  private int getOptimalRotation(int rotation, int cameraId) {
    Camera camera = mCameras.get(cameraId);
    if (camera == null) {
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "camera not initialized, can not get camera Display Orientation.");
      }
      return 0;
    }
    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    int degrees = 0;
    switch (rotation) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
    }
    int result;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      result = (info.orientation + degrees) % 360;
      result = (360 - result) % 360; // compensate the mirror
    } else { // back-facing
      result = (info.orientation - degrees + 360) % 360;
    }
    if (Controller.logEnabled()) {
      Log.d(LOG_TAG, "optimal rotation, camera's rotation is " + info.orientation
          + ", view's degree is " + degrees + ", result is " + result);
    }
    return result;
  }

  /**
   * Because the Preview Size cannot be an arbitrary tuple of value, it has to be selected from the
   * supported options.
   * 
   * @param w
   * @param h
   * @return
   */
  private Camera.Size getOptimalPreviewSize(int cameraId, int w, int h) {
    Camera camera = mCameras.get(cameraId);
    if (camera == null) {
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "camera not initialized, can not get Preview size.");
      }
      return null;
    }

    final double ASPECT_TOLERANCE = 0.1;
    double targetRatio = (double) w / h;
    List<Camera.Size> sizes = camera.getParameters().getSupportedPreviewSizes();
    if (Controller.logEnabled()) {
      String log = "";
      for (int i = 0, listSize = sizes.size(); i < listSize; i++) {
        log += "(" + sizes.get(i).width + ", " + sizes.get(i).height + "), ";
      }
      Log.d(LOG_TAG, log);
    }
    if (sizes == null)
      return null;

    Camera.Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;

    int targetHeight = h;

    for (Camera.Size size : sizes) {
      double ratio = (double) size.width / size.height;
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
        continue;
      if (Math.abs(size.height - targetHeight) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - targetHeight);
      }
    }

    if (optimalSize == null) {
      minDiff = Double.MAX_VALUE;
      for (Camera.Size size : sizes) {
        if (Math.abs(size.height - targetHeight) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.height - targetHeight);
        }
      }
    }
    return optimalSize;
  }

}

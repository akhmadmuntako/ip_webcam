package net.pro.ip_webcam;

import android.graphics.Rect;
import android.util.Log;

import org.opencv.android.OpenCVLoader;

public class ImageComparor {
  private final static String LOG_TAG = "ImageComparor";
  private static ImageComparor mInstance = null;

  public static ImageComparor getInstance() {
    if (mInstance == null) {
      loadLibraries();
      return mInstance = new ImageComparor();
    } else {
      return mInstance;
    }
  }
  
  private static boolean loadLibraries() {
    System.loadLibrary("motion_detector");
    return true;
  }
  
  static {
      if (!OpenCVLoader.initDebug()) {
        Log.e(LOG_TAG, "OpenCV load not successfully");
    }
  }

  public Rect compare(int prePre, int pre, int current, int sensitivity) {
    int[] rect = nativeDetectMotion(prePre, pre, current, sensitivity);
    if (rect != null && rect.length == 4) {
      Rect ret = new Rect(rect[0], rect[1], rect[2], rect[3]);
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "compare got result: " + ret.toString());
      }
      return ret;
    } else {
      if (Controller.logEnabled()) {
        Log.d(LOG_TAG, "native compare return null");
      }
      return null;
    }
  }
  
  public int convertYUVImageToLocalImage(byte[] imageData, int len, int imageWidth, int imageHeight) {
    return nativeConvertYUVImageToLocalImage(imageData, len, imageWidth, imageHeight);
  }
  
  public boolean releaseLocalImage(int handle) {
    return nativeReleaseLocalImage(handle);
  }
  
  private native int nativeConvertYUVImageToLocalImage(byte[] imageData, int len, int imageWidth, int imageHeight);
  private native boolean nativeReleaseLocalImage(int localImageHandle);
  private native int[] nativeDetectMotion(int prePre, int pre, int current, int sensitivity);
}

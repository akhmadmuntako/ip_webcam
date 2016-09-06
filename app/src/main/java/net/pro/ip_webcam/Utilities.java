package net.pro.ip_webcam;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class Utilities {
  public static String getExternalFSRoot() {
    String path = Environment.getExternalStorageDirectory().toString();
    return path;
  }
  
  public static File[] listFiles(String dir, String[] fileExtentions, final boolean ascend) {
    String path = dir;
    File f = new File(path);
    File ret[] = f.listFiles();
    // order
    Arrays.sort(ret, new Comparator<File>() {
      public int compare(File f1, File f2) {
        if (ascend) {
          return - Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
        } else {
          return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
        }
      }
    });
    // extentions
    Set<String> extSet = null;
    if (fileExtentions != null && fileExtentions.length != 0) {
      extSet = new HashSet<String>();
      for (int i = 0, count = fileExtentions.length; i < count; i ++) {
        extSet.add(fileExtentions[i]);
      }
    } else {
      return ret;
    }
    ArrayList<File> listRet = new ArrayList<File>(ret.length);
    for (int i=0; i < ret.length; i++)
    {
        if (extSet.contains(getFileExtention(ret[i].getName()))) {
          listRet.add(ret[i]);
        }
    }
    return (File[]) listRet.toArray(new File[0]);
  }
  
  private static String getFileExtention(String filePath) {
    int dotIndex = filePath.lastIndexOf('.');
    if (dotIndex == -1 || dotIndex < filePath.lastIndexOf('/')) {
      return null;
    } else {
      return filePath.substring(dotIndex);
    }
  }
  
  public static String getBestExpreOfFileSize(long fileSize) {
    if (fileSize <= 0) {
      return "0";
    } else {
      String[] levels = {"B", "KB", "MB", "GB", "TB"};
      int levelIndex = 0;
      long ret = fileSize;
      final long KILLO = 1024;
      for (int i = 0, count = levels.length; i < count; i ++) {
        if (ret > 0 && ret / KILLO > 0) {
          levelIndex ++;
          ret /= KILLO;
        } else {
          break;
        }
      }
      return ret + " " + levels[levelIndex];
    }
  }

}

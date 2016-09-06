package net.pro.ip_webcam;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by Akhmad Muntako on 5/6/2016.
 */
public class SmsService extends Service {
    private String phoneNo,message;
    public void onCreate() {
        try {
            phoneNo = new SettingsActivity().getNumberPhone();
            message = new SettingsActivity().getMessage();
        }catch (Exception ex){
            ex.printStackTrace();
        }

        sendSMSMessage();
    }
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//
//        return START_STICKY;
//    }

//    @Override
//    public void onDestroy() {
//        System.exit(0);
//    }
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void sendSMSMessage() {
        Log.i("Send SMS", "");
        boolean send = false;
        try {

            do {
                try {Toast.makeText(getApplicationContext(), phoneNo , Toast.LENGTH_LONG).show();
                    SmsManager smsManager = SmsManager.getDefault();
                    smsManager.sendTextMessage(phoneNo, null, message, null, null);
                    Toast.makeText(getApplicationContext(), "SMS sent.", Toast.LENGTH_LONG).show();
                    send = true;
                }

                catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "SMS failed, please try again.", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }while (!send);

        }catch (NullPointerException nul){
            Toast.makeText(getApplicationContext(), "Phone number and message not found", Toast.LENGTH_LONG).show();
            nul.printStackTrace();
        }

    }
}


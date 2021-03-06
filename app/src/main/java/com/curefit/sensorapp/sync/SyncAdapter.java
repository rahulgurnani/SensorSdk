package com.curefit.sensorapp.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.curefit.sensorapp.FirebaseStoreHelper;
import com.curefit.sensorapp.GlobalVariable;
import com.curefit.sensorapp.PayLoad;
import com.curefit.sensorapp.R;
import com.curefit.sensorapp.SensorData;
import com.curefit.sensorapp.data.AccDataContracted;
import com.curefit.sensorapp.data.LightData;
import com.curefit.sensorapp.data.LightDataContracted;
import com.curefit.sensorapp.data.PayLoadJson;
import com.curefit.sensorapp.data.ScreenData;
import com.curefit.sensorapp.data.User;
import com.curefit.sensorapp.data.AccelerometerData;
import com.curefit.sensorapp.db.DataStoreHelper;
import com.curefit.sensorapp.network.SensorClient;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.Transaction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.ByteArrayEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.protocol.HTTP;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by rahul on 23/08/17.
 */

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    ContentResolver contentResolver;
    Context mContext;
    String TAG = "SensorApp";
    public final int WINDOW = 60000;
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        contentResolver = context.getContentResolver();
        mContext = context;
    }


    @Override
    public void onPerformSync(Account account, Bundle bundle, String s,
                              ContentProviderClient contentProviderClient, SyncResult syncResult) {
        // Data transfer code here.
        Log.d(TAG, "onPerformSync");

        try {
            syncSensorData(syncResult);
        }
        catch (IOException ex) {
            // io exception
        }
        catch (RemoteException | OperationApplicationException ex) {
            // auth exception
        }
    }

    /**
     *
     * @param selectionArgs : the time upto  which we will aggregate the readings of the accelerometer.
     * @return : the aggregarted accelerometer readings
     */
    private List<AccDataContracted> getAggAccelerometerData(String selectionArgs[]) {
        Log.d(TAG, "Get acc data aggregated");
        String projectionAcc[] = { SensorDataContract.AccReadings.TIMESTAMP, SensorDataContract.AccReadings.ACCX,
                SensorDataContract.AccReadings.ACCY, SensorDataContract.AccReadings.ACCZ};      // The columns that we need in our query.
        long currentMinuteEnd = -1;         // Keeps the value of the second at which the currentMinute ends

        // Querying accelerometer readings and contracting them per WINDOW.
        Cursor c = contentResolver.query(SensorDataContract.AccReadings.CONTENT_URI, projectionAcc,
                SensorDataContract.AccReadings.TIMESTAMP + "<= ?", selectionArgs, null);    // Querying using the cursor

        List<AccelerometerData> accReadings = new ArrayList<>();        // the original accReadings, this array can be used for debugging purposes.
        List<AccDataContracted> accAggReadings = new ArrayList<>();     // the Aggregated accReadings, this array is the one that we send over the server.
        List<AccelerometerData> accReadingsWindow = new ArrayList<>();      // stores readings current window


        if (c.moveToFirst()) {
            // traverse through c
            do {
                float accValues[] = new float[3];
                accValues[0] = Float.parseFloat(c.getString(1));
                accValues[1] = Float.parseFloat(c.getString(2));
                accValues[2] = Float.parseFloat(c.getString(3));
                long timestamp = Long.parseLong(c.getString(0));

                if(currentMinuteEnd == -1)
                    currentMinuteEnd = timestamp - (timestamp % WINDOW) + WINDOW;       // gives the upper limit of the current minute

                AccelerometerData data = new AccelerometerData(accValues, timestamp);
                accReadings.add(data);

                if (timestamp < currentMinuteEnd) {
                    accReadingsWindow.add(data);
                }
                else {
                    if(accReadingsWindow.size() > 0)
                        accAggReadings.add(new AccDataContracted(accReadingsWindow, currentMinuteEnd - WINDOW));
                    accReadingsWindow = new ArrayList<>();
                    currentMinuteEnd = timestamp - (timestamp % WINDOW) + WINDOW;
                }
            } while (c.moveToNext());
        }

        // The last window needs to be added explicitly
        if (accReadingsWindow.size() > 0) {
            accAggReadings.add(new AccDataContracted(accReadingsWindow, currentMinuteEnd - WINDOW));
        }

        return accAggReadings;
    }

    /**
     *
     * @param selectionArgs : The time until which the readings need to be queried.
     * @return : returns the aggregated array of the readings.
     */
    private List<LightDataContracted> getAggLightData(String selectionArgs[]) {
        String projectionLight[] = { SensorDataContract.LightReadings.TIMESTAMP, SensorDataContract.LightReadings.LIGHT};
        long currentMinuteEnd = -1;         // Keeps the value of the second at which the currentMinute ends

        // Querying Light readings and contracting (aggregating) them over WINDOW.
        Cursor c = contentResolver.query(SensorDataContract.LightReadings.CONTENT_URI, projectionLight, SensorDataContract.LightReadings.TIMESTAMP + "<= ?", selectionArgs, null);
        List<LightData> lightReadings = new ArrayList<>();
        List<LightDataContracted> lightContractedReadings = new ArrayList<>();
        List<LightData> lightReadingsWindow = new ArrayList<>();
        currentMinuteEnd = -1;
        if (c.moveToFirst()) {
            // traverse through c
            do {
                long timestamp = Long.parseLong(c.getString(0));
                LightData data = new LightData(Float.parseFloat(c.getString(1)), Long.parseLong(c.getString(0)));
                lightReadings.add(data);

                if (currentMinuteEnd == -1)
                    currentMinuteEnd = timestamp - (timestamp % WINDOW) + WINDOW;

                if (timestamp < currentMinuteEnd) {
                    lightReadingsWindow.add(data);
                }
                else {
                    if (lightReadingsWindow.size() > 0)
                        lightContractedReadings.add(new LightDataContracted(lightReadingsWindow, currentMinuteEnd - WINDOW));
                    lightReadingsWindow = new ArrayList<>();
                    lightReadings.add(data);
                    currentMinuteEnd = timestamp - (timestamp % WINDOW) + WINDOW;
                }
            }while (c.moveToNext());
        }

        if(lightReadingsWindow.size() > 0)
            lightContractedReadings.add(new LightDataContracted(lightReadingsWindow, currentMinuteEnd - WINDOW));

        return lightContractedReadings;
    }

    private List<ScreenData> getScreenData(String selectionArgs[]) {
        // Querying Screen readings and putting them in the hashmap
        String projectionScreen[] = { SensorDataContract.ScreenReadings.TIMESTAMP, SensorDataContract.ScreenReadings.SCREEN };
        Cursor c = contentResolver.query(SensorDataContract.ScreenReadings.CONTENT_URI, projectionScreen, SensorDataContract.ScreenReadings.TIMESTAMP + "<= ?" , selectionArgs, null);
        List<ScreenData> screenReadings = new ArrayList<>();

        if (c.moveToFirst()) {
            // traverse through c
            do {
                ScreenData data = new ScreenData(Integer.parseInt(c.getString(1)), Long.parseLong(c.getString(0)));
                screenReadings.add(data);
            }while (c.moveToNext());
        }
        return screenReadings;
    }


    private void syncSensorData(SyncResult syncResult) throws IOException, RemoteException, OperationApplicationException {

        HashMap<String, List> alldata = new HashMap<>();      // This hasmap holds all the data that needs to be sent
        boolean flag_acc_absent = false;
        boolean flag_light_absent = false;
        boolean flag_screen_absent = false;


        long currentTime = System.currentTimeMillis();
        currentTime = currentTime - currentTime % WINDOW;      // rounded to last minute
        String selectionArgs[] = { String.valueOf(currentTime) };

        // Accelerometer
        List<AccDataContracted> accAggReadings = getAggAccelerometerData(selectionArgs);

        if(accAggReadings.size() > 0)
            alldata.put("acc_contracted", accAggReadings);
        else
            flag_acc_absent = true;

        // Light
        List<LightDataContracted> lightAggReadings = getAggLightData(selectionArgs);
        if (lightAggReadings.size() > 0)
            alldata.put("light_contracted", lightAggReadings);
        else
            flag_light_absent = true;

        // Screen
        List<ScreenData> screenReadings = getScreenData(selectionArgs);
        if (screenReadings.size() > 0)
            alldata.put("screen", screenReadings);
        else
            flag_screen_absent = true;

        // in case none of the readings are present.
        if (flag_acc_absent && flag_light_absent && flag_screen_absent)
            return;

        // User
        String projectionUser[] = {SensorDataContract.UserData.NAME, SensorDataContract.UserData.EMAIL };
        Cursor user_c = contentResolver.query(SensorDataContract.UserData.CONTENT_URI, projectionUser, null, null, null);
        String username = null;
        String email= null;
        if (user_c.moveToFirst()) {
            // traverse and take the last one
            do {
                username = user_c.getString(0);
                email = user_c.getString(1);
            }while (user_c.moveToNext());
        }
        else {
            // user not present
        }

        User user = new User(username, email);
        // send it to firebase database
        FirebaseApp.initializeApp(mContext);
        FirebaseStoreHelper f = new FirebaseStoreHelper(mContext.getString(R.string.FIREBASE_URL));
        f.sendData(alldata, user, currentTime);

        // Delete the entries
        contentResolver.delete(SensorDataContract.AccReadings.CONTENT_URI, SensorDataContract.AccReadings.TIMESTAMP + " <= ?", selectionArgs);
        contentResolver.delete(SensorDataContract.LightReadings.CONTENT_URI, SensorDataContract.LightReadings.TIMESTAMP + " <= ?", selectionArgs);
        contentResolver.delete(SensorDataContract.ScreenReadings.CONTENT_URI, SensorDataContract.ScreenReadings.TIMESTAMP + " <= ?", selectionArgs);

//        GsonBuilder builder = new GsonBuilder();
//        Gson gson = builder.create();
//        String jsonObject = gson.toJson(h);
//        System.out.println(jsonObject);
        // code to post the data
//        PayLoadJson alldata = new PayLoadJson(email, DataStoreHelper.getDateTime().split("\\s")[0], h);
//        postDataToServer(alldata);
//        Handler mainHandler = new Handler(Looper.getMainLooper());
//        Runnable myRunnable = new MyRunnable(alldata, currentTime);
//        mainHandler.post(myRunnable);

    }
    class MyRunnable implements Runnable {
        PayLoadJson alldata;
        long currentTime;
        public MyRunnable(PayLoadJson alldata, long currentTime) {
            this.alldata = alldata;
            this.currentTime = currentTime;
        }
        @Override
        public void run() {
            postDataToServer(alldata);
        }
    }
    /**
     * Manual force Android to perform a sync with our SyncAdapter.
     */
    public static void performSync() {
        Log.e("SensorApp", "PerformSync called");
        Bundle b = new Bundle();
        b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(AccountGeneral.getAccount(),
                SensorDataContract.CONTENT_AUTHORITY, b);

    }

    public void postDataToServer(PayLoadJson alldata) {
        SensorClient.getSensorService(mContext).postData(alldata).enqueue(new Callback<Object>() {
            @Override
            public void onResponse(Call<Object> call, Response<Object> response) {
                Log.i("sendReceivedEvent", "SUCCESS");
            }

            @Override
            public void onFailure(Call<Object> call, Throwable t) {
                Log.i("sendReceivedEvent", "onFailure");
            }
        });
    }
}


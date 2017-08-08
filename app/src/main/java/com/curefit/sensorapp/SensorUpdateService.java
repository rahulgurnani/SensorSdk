package com.curefit.sensorapp;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.firebase.FirebaseApp;

public class SensorUpdateService extends Service implements SensorEventListener {
    private DataStoreHelper dsh;
    private SensorManager sensorManager;
    private Sensor mAccelerometer;
    private Sensor mLight;
    private long startTime;
    private long startTimeLightSensor;
    private float lastValues[];

    public SensorUpdateService() {
        System.out.println("Sensor update service activated");
        dsh = new DataStoreHelper(this);
        lastValues = new float[3];
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mLight = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
        System.out.println("Registered listener");
        FirebaseApp.initializeApp(this);
        // Dealing with broadcast receiver for sensor update
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        BroadcastReceiver mReceiver = new ScreenReceiver();
        registerReceiver(mReceiver, filter);
        startTime = startTimeLightSensor = 0;
//        startForeground(1, );
        return START_STICKY;

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    private double vectorialDistance(float values1[], float values2[]) {
        return Math.sqrt(Math.pow( (values1[0] - values2[0]), 2) + Math.pow( (values1[0] - values2[0]), 2) + Math.pow( (values1[0] - values2[0]), 2));
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if ((System.currentTimeMillis() - startTime) > 1000 && vectorialDistance(sensorEvent.values, lastValues) > 0.3) {
                dsh.addEntry(sensorEvent.values);
                lastValues[0] = sensorEvent.values[0];
                lastValues[1]= sensorEvent.values[1];
                lastValues[2]= sensorEvent.values[2];
                startTime = System.currentTimeMillis();

                System.out.println("Accelerometer Sensor changed");
            }
        }
        else if(sensorEvent.sensor.getType() == Sensor.TYPE_LIGHT) {
            if ((System.currentTimeMillis() - startTimeLightSensor) > 1000) {
                dsh.addEntry(sensorEvent.values[0]);
                startTimeLightSensor = System.currentTimeMillis();
                System.out.println("Light sensor changed");
            }
        }
    }
}

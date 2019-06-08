package com.prashanth.facedetection;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import timber.log.Timber;

public class FaceDetectionApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        Timber.plant(new Timber.DebugTree());
    }
}
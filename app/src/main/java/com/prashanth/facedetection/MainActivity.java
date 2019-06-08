package com.prashanth.facedetection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;
import butterknife.BindView;
import com.google.android.gms.tasks.OnSuccessListener;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Facing;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements FrameProcessor, LifecycleOwner {

    private CameraView view;

    private View overlayView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        view = findViewById(R.id.face_detection_camera_view);
        overlayView = findViewById(R.id.overlay);

        Button snapButton = findViewById(R.id.snap_button);
        snapButton.setOnClickListener(v -> {
            //TODO
            //Snap the photo  and crop the face and send it to the next screen
        });

        if (hasCameraPermission()) {
            Timber.d("Permissions granted");
            view.setFacing(Facing.FRONT);
            view.setLifecycleOwner(this);
            view.addFrameProcessor(this);

        }

        overlayView.post(() -> {
            Rect rect = new Rect();
            overlayView.getGlobalVisibleRect(rect);
            Timber.d("Visible rect %s ", rect.toString());
        });
    }

    private boolean hasCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
                return false;
            }
        }
        return true;
    }

    private void hasExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
            }
        }
    }

    private void hasReadExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 13);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.d("Camera permission granted");
                view.setFacing(Facing.FRONT);
                view.setLifecycleOwner(this);
                view.addFrameProcessor(this);
                hasExternalStoragePermission();
            }
        }

        if (requestCode == 12) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Timber.d("External storage permission granted");
                hasReadExternalStoragePermission();
            }
        }
    }

    @Override
    public void process(@NonNull Frame frame) {

        int height = frame.getSize().getHeight();
        int width = frame.getSize().getWidth();

        FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata.Builder()
                .setWidth(width)
                .setHeight(height)
                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
                .setRotation(FirebaseVisionImageMetadata.ROTATION_270)
                .build();

        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromByteArray(frame.getData(), metadata);

        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                .build();

        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);

        faceDetector.detectInImage(firebaseVisionImage)
                .addOnSuccessListener(firebaseVisionFaces -> {
                    Timber.d("Face detection successful  number %s", firebaseVisionFaces.size());
//                    TODO identify if the photo is in the bounds of the overlay if yes enable the button and on click; store the bitmap and show it in the next screen

                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                    for (FirebaseVisionFace face : firebaseVisionFaces) {
                        List<FirebaseVisionPoint> faceContours = face.getContour(FirebaseVisionFaceContour.FACE).getPoints();

                        for (int i = 0; i < faceContours.size(); i++) {
                            Timber.d("Rect for the face found: %s", face.getBoundingBox().toString());
                            //TODO - Crop the photo and handle it accordingly

                            int x = face.getBoundingBox().centerX();
                            int y = face.getBoundingBox().centerY();

                            Bitmap bm = Bitmap.createBitmap(bitmap, 0, 0,
                                    face.getBoundingBox().height(),
                                    face.getBoundingBox().width());

                            //TODO - Doesn't seem to work yet, blank photo is displayed
                            String filename = "face.png";
                            File sd = Environment.getExternalStorageDirectory();
                            File dest = new File(sd, filename);
                            try {
                                //TODO once file is saved - finish this and start ShowFaceActivity through an intent
                                //Maybe encrypt the file for security purposes or keep the file in memory?
                                Timber.d("Saving file");
                                FileOutputStream out = new FileOutputStream(dest);
                                bm.compress(Bitmap.CompressFormat.PNG, 90, out);
                                out.flush();
                                out.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                })
                .addOnFailureListener(e -> {
                    //TODO handle error - show an alert box
                    Timber.d("Face detection unsuccessful");
                });

    }
}
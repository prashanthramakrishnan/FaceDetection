package com.prashanth.facedetection;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LifecycleOwner;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Facing;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements FrameProcessor, LifecycleOwner, Closeable {

    private CameraView view;

    private View overlayView;

    private Button snapButton;

    private Rect overlayRect;

    private FirebaseVisionFaceDetector faceDetector;

    private static String IMG_NAME = "face.png";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        snapButton = findViewById(R.id.snap_button);
        view = findViewById(R.id.face_detection_camera_view);
        overlayView = findViewById(R.id.overlay);
        disableButton();
        snapButton.setOnClickListener(v -> {
            view.capturePicture();
        });

        if (hasCameraPermission()) {
            Timber.d("Permissions granted");
            view.setFacing(Facing.FRONT);
            view.setLifecycleOwner(this);
            view.addFrameProcessor(this);
            view.addCameraListener(new CameraClickOnPictureTakenListener());
        }

        overlayView.post(() -> {
            overlayRect = new Rect();
            overlayView.getGlobalVisibleRect(overlayRect);
            Timber.d("Visible rect %s ", overlayRect.toString());
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
        if (frame.getSize() != null) {
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

            faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);

            faceDetector.detectInImage(firebaseVisionImage)
                    .addOnSuccessListener(firebaseVisionFaces -> {
                        if (firebaseVisionFaces.isEmpty()) {
                            disableButton();
                        }

                        for (FirebaseVisionFace face : firebaseVisionFaces) {
                            Rect rect = new Rect();
                            rect.set(face.getBoundingBox().left, (int) (face.getBoundingBox().top * 1.5), face.getBoundingBox().right,
                                    face.getBoundingBox().bottom);

                            if (overlayRect.contains(rect)) {
                                enableButton();
                            } else {
                                disableButton();
                            }
                        }

                    })
                    .addOnFailureListener(e -> {
                        Timber.d("Face detection unsuccessful");
                        disableButton();
                    });
        }

    }

    @Override
    public void close() throws IOException {
        faceDetector.close();
        view.removeFrameProcessor(this);
    }

    private class CameraClickOnPictureTakenListener extends CameraListener {

        @SuppressLint("WrongThread")
        @Override
        public void onPictureTaken(byte[] jpeg) {
            super.onPictureTaken(jpeg);
            try {
                faceDetector.close();
            } catch (IOException e) {
                Timber.e(e, "Error closing facedetector");
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, overlayView.getTop(), overlayView.getRight(), (int) (overlayView.getHeight() * 1.5),
                    (int) (overlayView.getWidth() * 1.5));
            FileOutputStream fos;
            try {
                fos = MainActivity.this.openFileOutput(IMG_NAME, Context.MODE_PRIVATE);
                croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
            } catch (FileNotFoundException e) {
                Timber.e(e, "file not found");
            } catch (IOException e) {
                Timber.e(e, "Can't save file");
            }

            Intent intent = new Intent(MainActivity.this, ShowFaceActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("IMAGE", IMG_NAME);
            startActivity(intent);
            finish();

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetector != null) {
            try {
                Timber.d("Face detector closed");
                view.removeFrameProcessor(this);
                faceDetector.close();
            } catch (IOException e) {
                Timber.e(e, "Exception closing detector");
            }
        }
    }

    private void enableButton() {
        if (!snapButton.isEnabled()) {
            snapButton.setEnabled(true);
        }
    }

    private void disableButton() {
        if (snapButton.isEnabled()) {
            snapButton.setEnabled(false);
        }
    }
}
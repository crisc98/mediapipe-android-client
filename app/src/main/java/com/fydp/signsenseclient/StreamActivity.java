package com.fydp.signsenseclient;

import android.graphics.SurfaceTexture;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketCallback;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.framework.ProtoUtil;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Arrays;
import java.util.HashMap;

public class StreamActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String BINARY_GRAPH_NAME = "holistic_tracking_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LHAND_LANDMARKS_STREAM_NAME = "left_hand_landmarks";
    private static final String OUTPUT_RHAND_LANDMARKS_STREAM_NAME = "right_hand_landmarks";
    private static final String OUTPUT_POSE_LANDMARKS_STREAM_NAME = "pose_landmarks";
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    private static final int MAX_LABEL_LENGTH = 15;
    private HashMap<String, NormalizedLandmarkList> landmarkLists;
    private ClientNetwork clientSocket;
    // Flips the camera-preview frames vertically before sending them into FrameProcessor to be
    // processed in a MediaPipe graph, and flips the processed frames back when they are displayed.
    // This is needed because OpenGL represents images assuming the image origin is at the bottom-left
    // corner, whereas MediaPipe in general assumes the image origin is at top-left.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;
    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }
    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    private CameraXPreviewHelper cameraHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN); //enable full screen
        setContentView(R.layout.activity_stream);
        ProtoUtil.registerTypeName(NormalizedLandmarkList.class, "mediapipe.NormalizedLandmarkList");
        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();
        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);

        clientSocket = new ClientNetwork(this);
        new Thread(clientSocket).start();

        landmarkLists = new HashMap<>();
        PacketCallback onLeftHandDataReceived = (packet) -> {
            onPacketReceived(packet, "left");
        };
        PacketCallback onRightHandDataReceived = (packet) -> {
            onPacketReceived(packet, "right");
        };
        PacketCallback onPoseDataReceived = (packet) -> {
            //if(!landmarkLists.isEmpty()) {
            clientSocket.addToQueue(getNetworkFormattedData(landmarkLists));
            //}
            landmarkLists.clear();
        };
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);
        processor.addPacketCallback(OUTPUT_LHAND_LANDMARKS_STREAM_NAME, onLeftHandDataReceived);
        processor.addPacketCallback(OUTPUT_RHAND_LANDMARKS_STREAM_NAME, onRightHandDataReceived);
        processor.addPacketCallback(OUTPUT_POSE_LANDMARKS_STREAM_NAME, onPoseDataReceived);
        PermissionHelper.checkAndRequestCameraPermissions(this);
    }

    private void onPacketReceived(Packet packet, String key) {
        try {
            NormalizedLandmarkList landmarks =
                    PacketGetter.getProto(packet, NormalizedLandmarkList.class);
            landmarkLists.put(key, landmarks);
        }
        catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Failed to get proto.", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter = new ExternalTextureConverter(eglManager.getContext());
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        clientSocket.setExitFlag(true);
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        /*
         (Re-)Compute the ideal size of the camera-preview display (the area that the
         camera-preview frames get rendered onto, potentially with scaling and rotation)
         based on the size of the SurfaceView that contains the display.
        */
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        /*
         Connect the converter to the camera-preview frames as its input (via
         previewFrameTexture), and configure the output width and height as the computed
         display size.
        */
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.stream);
        viewGroup.addView(previewDisplayView);
        View child = getLayoutInflater().inflate(R.layout.prediction_label, null);
        viewGroup.addView(child);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }


    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    private void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                this::onCameraStarted);
        cameraHelper.startCamera(
                this, CAMERA_FACING, /*unusedSurfaceTexture=*/ null, null);
    }

    private String getNetworkFormattedData(HashMap<String, NormalizedLandmarkList> landmarkMap) {
        StringBuilder retStr = new StringBuilder();
        StringBuilder leftStr = new StringBuilder();
        StringBuilder rightStr = new StringBuilder();

        int count = 0;
        NormalizedLandmarkList leftLandmarks = landmarkMap.get("left");
        NormalizedLandmarkList rightLandmarks = landmarkMap.get("right");
        if(leftLandmarks == null){
            for (int i = 0; i < 63; i++) {
                if ( i > 0 ) {
                    leftStr.append(", ");
                }
                leftStr.append("0.0");
            }
        }
        else {
            for (NormalizedLandmark landmark : leftLandmarks.getLandmarkList()) {
                if (count > 0) {
                    leftStr.append(", ");
                }
                leftStr.append(landmark.getX()).append(", ").append(landmark.getY()).append(", ").append(landmark.getZ());
                count++;
            }
        }

        if(rightLandmarks == null){
            for (int i = 0; i < 63; i++) {
                if ( i > 0 ) {
                    rightStr.append(", ");
                }
                rightStr.append("0.0");
            }

        }
        else {
            for (NormalizedLandmark landmark : rightLandmarks.getLandmarkList()) {
                if (count > 0) {
                    rightStr.append(", ");
                }
                rightStr.append(landmark.getX()).append(", ").append(landmark.getY()).append(", ").append(landmark.getZ());
                count++;
            }
        }
        retStr.append(rightStr);
        retStr.append(", ");
        retStr.append(leftStr);
        return retStr.toString();
    }

    public void addToLabelBuffer(String text, boolean clear) {
        TextView label = findViewById(R.id.prediction_label);
        String currText = label.getText().toString();
        String[] words = currText.split(" ");
        String lastWord = words[words.length-1];
        if (lastWord.equals(text)) {
            return;
        }
        if(text.length() > MAX_LABEL_LENGTH || clear) {
            label.setText(text);
            return;
        }
        else {
            while (currText.length() + text.length() > MAX_LABEL_LENGTH) {
                //Strip first word
                currText = (currText.split(" ", 2))[1];
            }
        }
        label.setText(currText + " " + text);

    }

}
package cn.leo.ocrdemo.ocr;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Process;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;

/**
 * create by : Jarry Leo
 * date : 2018/7/20 9:15
 */
public class CameraView extends TextureView implements LifecycleObserver, TextureView.SurfaceTextureListener, View.OnClickListener {
    private String TAG = "CameraView";
    private Camera mCamera;
    private Camera.Parameters mParameters;
    private RenderScript rs;
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
    private Type.Builder yuvType;
    private Allocation in;
    private Allocation out;
    private OnBitmapCreateListener mBitmapCreateListener;
    private Rect mBitmapRect;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        if (!checkCameraHardware(context)) {
            Toast.makeText(context, "没有检测到摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        if (context instanceof LifecycleOwner) {
            ((LifecycleOwner) context).getLifecycle().addObserver(this);
            setSurfaceTextureListener(this);
            setOnClickListener(this);
            rs = RenderScript.create(context);
            yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
        }
    }

    //检查是否存在摄像头
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;
        } else {
            return false;
        }
    }

    private void requestCamera(int mCameraId) {
        openCamera(mCameraId);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        requestCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        initPreViewSize();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        stopPreview();
        closeCamera();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {

    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        setPreviewTexture(surface);
        setDisplayOrientation(90);
        startPreview();
        Log.i(TAG, "onSurfaceTextureAvailable: size:" + width + "," + height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private void setDisplayOrientation(int degree) {
        if (mCamera != null) {
            mCamera.setDisplayOrientation(degree);
        }
        Log.i(TAG, "Set display orientation is : " + degree);
    }

    private void setPreviewTexture(SurfaceTexture surfaceTexture) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 开启摄像头
     *
     * @param cameraId 摄像头id
     */
    private void openCamera(int cameraId) {
        try {
            if (mCamera == null) {
                mCamera = Camera.open(cameraId);
                Log.i(TAG, "Camera has opened, cameraId is " + cameraId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Open Camera has exception!");
        }
    }

    private void startPreview() {
        if (mCamera != null) {
            mCamera.setErrorCallback(mErrorCallback);
            mCamera.startPreview();
            mCamera.autoFocus(null);
            Log.i(TAG, "Camera Preview has started!");
        }
    }

    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            Log.i(TAG, "Camera Preview has stopped!");
        }
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.setErrorCallback(null);
            mCamera.release();
            mCamera = null;
            Log.i(TAG, "Camera has closed!");
        }
    }

    public void takePicture() {
        if (mCamera != null) {
            mCamera.takePicture(null, null, mPictureCallback);
        }
    }

    private Camera.ErrorCallback mErrorCallback = new Camera.ErrorCallback() {
        @Override
        public void onError(int error, Camera camera) {
            Log.e(TAG, "onError: got camera error callback: " + error);
            if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                android.os.Process.killProcess(Process.myPid());
            }
        }
    };
    private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            createBitmap(data.length, size.width, size.height, data);
            startPreview();
        }
    };

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            createBitmap(data.length, size.width, size.height, data);
        }
    };

    private void initPreViewSize() {
        mParameters = getParameters();
        initPreViewSize(mParameters);
    }

    private Camera.Parameters getParameters() {
        if (mCamera != null) {
            return mCamera.getParameters();
        }
        return null;
    }

    private void setParameters() {
        if (mCamera != null && mParameters != null) {
            mCamera.setParameters(mParameters);
        }
    }

    private void initPreViewSize(Camera.Parameters parameters) {
        if (parameters == null) {
            return;
        }
        mParameters.setPictureSize(1920, 1080);
//        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
        for (Camera.Size size : parameters.getSupportedPictureSizes()) {
            Log.i(TAG, "initPreViewSize: " + size.height + " x " + size.width);
            if (equalsRate(size, 1.777f)) {

            }
        }
        setParameters();
    }

    private boolean equalsRate(Camera.Size size, float rate) {
        float f = (float) size.width / (float) size.height;
        if (Math.abs(f - rate) <= 0.1f) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onClick(View v) {
        autoFocus();
    }

    private void autoFocus() {
        mCamera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (mBitmapCreateListener != null) {
                    takePicture();
                }
            }
        });
    }

    private void createBitmap(int dataLength, int prevSizeW, int prevSizeH, byte[] data) {
       /* if (yuvType == null) {
            yuvType = new Type.Builder(rs, Element.U8(rs)).setX(dataLength);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);
            Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(prevSizeW).setY(prevSizeH);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }
        in.copyFrom(data);
        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        Bitmap bmpOut = Bitmap.createBitmap(prevSizeW, prevSizeH, Bitmap.Config.ARGB_8888);
        out.copyTo(bmpOut);*/
        Bitmap bmpOut = BitmapFactory.decodeByteArray(data, 0, data.length);
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap bitmap;
        if (mBitmapRect != null) {
            int rw = prevSizeH * 2 / 3;
            mBitmapRect.set(prevSizeW / 2 - rw / 2, rw / 4, prevSizeW / 2 + rw / 2, rw + rw / 4);
            bitmap = Bitmap.createBitmap(bmpOut, mBitmapRect.left, mBitmapRect.top, mBitmapRect.width(), mBitmapRect.height(), matrix, true);
        } else {
            bitmap = Bitmap.createBitmap(bmpOut, 0, 0, bmpOut.getWidth(), bmpOut.getHeight(), matrix, true);
        }
        //bmpOut.recycle();
//        bitmap = Bitmap.createBitmap(bmpOut, 0, 0, bmpOut.getWidth(), bmpOut.getHeight(), matrix, true);
        if (mBitmapCreateListener != null) {
            mBitmapCreateListener.onBitmapCreate(bitmap);
        }
    }


    public void setOnBitmapCreateListener(OnBitmapCreateListener onBitmapCreateListener, Rect bitmapRect) {
        mBitmapCreateListener = onBitmapCreateListener;
        mBitmapRect = bitmapRect;
    }

    public interface OnBitmapCreateListener {
        void onBitmapCreate(Bitmap bitmap);
    }
}

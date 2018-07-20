package cn.leo.ocrdemo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.v7.app.AppCompatActivity;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.IOException;

import cn.leo.ocrdemo.ocr.FileUtil;
import cn.leo.ocrdemo.ocr.Ocr2String;
import cn.leo.permission.PermissionRequest;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextureView mTextureView;
    private TessBaseAPI mTess;
    private TextView mTxtView;
    private boolean mInit;
    private Camera mCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);
        mTextureView = findViewById(R.id.cameraPre);
        mTxtView = findViewById(R.id.sample_text);
        mTxtView.setOnClickListener(this);
        copyFile();
        if (checkCameraHardware(this)) {
            setTextureView();
        }
    }

    @PermissionRequest({Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE})
    private void copyFile() {
        String dataPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/tesseract/";
        File dir = new File(dataPath + "tessdata/");
        FileUtil.CopyAssets(this, "tessdata", dir.getAbsolutePath());
        initTessBaseData(dataPath);
    }

    private void initTessBaseData(String datapath) {
        mTess = new TessBaseAPI();
        String language = "eng";
        mTess.setDebug(BuildConfig.DEBUG);
        mInit = mTess.init(datapath, language);
        if (!mInit) {
            Toast.makeText(this, "加载语言包失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onClick(View v) {
        handler.sendEmptyMessageDelayed(1, 100);
    }

    private void ocrScan(Bitmap bitmap) {
        if (!mInit) return;
        mTess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "123456789"); // 识别白名单
        mTess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-[]}{;:'\"\\|~`,./<>?"); // 识别黑名单
        mTess.setImage(bitmap);
        String boxText = mTess.getBoxText(0);
        final String utf8Text = mTess.getUTF8Text().replace("\n", "");
        bitmap.recycle();
        final String result1 = Ocr2String.create(boxText, bitmap.getWidth(), bitmap.getHeight()).getResult();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTxtView.setText("数独题:" + result1 + "\n识别的数：" + utf8Text);
            }
        });
    }


    private void test(int dataLength, int prevSizeW, int prevSizeH, byte[] data) {
        RenderScript rs = RenderScript.create(this);
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
        Type.Builder yuvType = null;
        Allocation in = null;
        Allocation out = null;
        if (yuvType == null) {
            yuvType = new Type.Builder(rs, Element.U8(rs)).setX(dataLength);
            in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);
            Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(prevSizeW).setY(prevSizeH);
            out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }
        in.copyFrom(data);
        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap bmpout = Bitmap.createBitmap(prevSizeW, prevSizeH, Bitmap.Config.ARGB_8888);
        out.copyTo(bmpout);
        Bitmap nbmp2 = Bitmap.createBitmap(bmpout, 0, 0, bmpout.getWidth(), bmpout.getHeight(), matrix, true);
        ocrScan(nbmp2);
    }

    protected void setTextureView() {
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setPreviewTexture(surface, width, height);
                setDisplayOrientation(90);
                startPreview();
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
        });
    }


    private void getPreViewImage() {

        mCamera.setPreviewCallback(new Camera.PreviewCallback() {

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Size size = camera.getParameters().getPreviewSize();
                test(data.length, size.width, size.height, data);
            }
        });
    }

    Handler handler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case 1:
                    getPreViewImage();
                    handler.sendEmptyMessageDelayed(2, 300);
                    break;
                case 2:
                    mCamera.autoFocus(null);
                    mCamera.setPreviewCallback(null);
                    handler.sendEmptyMessageDelayed(1, 5000);
                    break;


            }

        }

        ;
    };

    private void setDisplayOrientation(int degree) {
        if (mCamera != null) {
            mCamera.setDisplayOrientation(degree);

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
    }


    //Check whether the device has a camera
    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            return true;//has Camera
        } else {
            return false;// has not Camera
        }
    }

    private void setPreviewTexture(SurfaceTexture surfaceTexture, int width, int height) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void requestCamera(int mCameraId) {
        openCamera(mCameraId);
    }

    private void openCamera(int cameraId) {
        try {
            if (mCamera == null) {
                mCamera = Camera.open(cameraId);
            }
        } catch (Exception e) {
        }
    }

    private void startPreview() {
        if (mCamera != null) {
            mCamera.startPreview();
        }
    }

    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.setErrorCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPreview();
        closeCamera();
    }

}

package cn.leo.ocrdemo;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;

import cn.leo.ocrdemo.ocr.CameraView;
import cn.leo.ocrdemo.ocr.FileUtil;
import cn.leo.ocrdemo.ocr.Ocr2String;
import cn.leo.ocrdemo.ocr.ScannerView;
import cn.leo.permission.PermissionRequest;

public class Main2Activity extends AppCompatActivity {

    private TessBaseAPI mTess;
    private boolean mInit;
    private CameraView mCameraView;
    private ScannerView mScannerView;
    private TextView mTxtView;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //全屏无状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main2);
        mCameraView = findViewById(R.id.cameraPre);
        mScannerView = findViewById(R.id.scanner);
        mTxtView = findViewById(R.id.sample_text);
        mImageView = findViewById(R.id.imageView);
        copyFile();
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
            return;
        }
        mCameraView.setOnBitmapCreateListener(new CameraView.OnBitmapCreateListener() {
            @Override
            public void onBitmapCreate(final Bitmap bitmap) {
                final Bitmap convertGreyImg = convertGreyImg(bitmap);
                ocrScan(convertGreyImg);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mImageView.setImageBitmap(convertGreyImg);
                    }
                });
            }
        }, mScannerView.getRect());
    }

    private void ocrScan(Bitmap bitmap) {
        if (!mInit) return;
        mTess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "123456789"); // 识别白名单
        mTess.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-[]}{;:'\"\\|~`,./<>?"); // 识别黑名单
        mTess.setImage(bitmap);
        String boxText = mTess.getBoxText(0);
        final String utf8Text = mTess.getUTF8Text().replace("\n", "");
        //bitmap.recycle();
        final String result = Ocr2String.create(boxText, bitmap.getWidth(), bitmap.getHeight()).getResult();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTxtView.setText("数独题:" + result + "\n识别的数：" + utf8Text);
            }
        });
    }

    /**
     * 将彩色图转换为灰度图
     *
     * @param img 位图
     * @return 返回转换好的位图
     */
    public Bitmap convertGreyImg(Bitmap img) {
        int width = img.getWidth();         //获取位图的宽
        int height = img.getHeight();       //获取位图的高

        int[] pixels = new int[width * height]; //通过位图的大小创建像素点数组

        img.getPixels(pixels, 0, width, 0, 0, width, height);
        int alpha = 0xFF << 24;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int grey = pixels[width * i + j];

                int red = ((grey & 0x00FF0000) >> 16);
                int green = ((grey & 0x0000FF00) >> 8);
                int blue = (grey & 0x000000FF);

                grey = (int) ((float) red * 0.3 + (float) green * 0.59 + (float) blue * 0.11);
                grey = alpha | (grey << 16) | (grey << 8) | grey;
                pixels[width * i + j] = grey;
            }
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }
}

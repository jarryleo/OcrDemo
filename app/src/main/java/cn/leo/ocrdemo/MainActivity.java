package cn.leo.ocrdemo;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;

import cn.leo.permission.PermissionRequest;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TessBaseAPI mTess;
    private TextView mTxtView;
    private ImageView mImgView;
    private boolean mInit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTxtView = findViewById(R.id.sample_text);
        mImgView = findViewById(R.id.imageView);
        mImgView.setOnClickListener(this);
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
        }
    }

    @Override
    public void onClick(View v) {
        if (!mInit) return;
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.textimage);
        mTess.setImage(bitmap);
        String result = mTess.getUTF8Text();
        mTxtView.setText("结果为:" + result);
    }
}

package com.xiao.nicevieoplayer.example;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.xiao.nicevieoplayer.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void tinyWindow(View view) {
        startActivity(new Intent(this, TinyWindowPlayActivity.class));
    }

    public void videoList(View view) {
        startActivity(new Intent(this, IJKSurfaceListActivity.class));
    }

    public void videoList2(View view) {
        startActivity(new Intent(this, IJKTextureListActivity.class));
    }

    public void videoList3(View view) {
        startActivity(new Intent(this, AliPlayerListActivity.class));
    }

    public void changeClarity(View view) {
        startActivity(new Intent(this, ChangeClarityActivity.class));
    }

    public void processHomeKeyInFragment(View view) {
        // 在Fragment中使用NiceVideoPlayer，如果需要处理播放时按下Home键的逻辑.
        startActivity(new Intent(this, ProcessHome2Activity.class));
    }
}

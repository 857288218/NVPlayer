package com.xiao.nicevieoplayer.example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.xiao.nicevideoplayer.Clarity;
import com.xiao.nicevideoplayer.player.IJKSurfaceVideoPlayer;
import com.xiao.nicevideoplayer.NiceVideoPlayerManager;
import com.xiao.nicevideoplayer.TxVideoPlayerController;
import com.xiao.nicevieoplayer.R;

import java.util.ArrayList;
import java.util.List;

public class ChangeClarityActivity extends AppCompatActivity {

    private IJKSurfaceVideoPlayer mNiceVideoPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_clarity);
        init();
    }

    private void init() {
        mNiceVideoPlayer = (IJKSurfaceVideoPlayer) findViewById(R.id.nice_video_player);
        mNiceVideoPlayer.setPlayerType(IJKSurfaceVideoPlayer.TYPE_IJK); // IjkPlayer or MediaPlayer
        TxVideoPlayerController controller = new TxVideoPlayerController(this);
        controller.setTitle("Beautiful China...");
        controller.setLenght(117000);
        controller.setClarity(getClarites(), 0);
        Glide.with(this)
                .load("http://imgsrc.baidu.com/image/c0%3Dshijue%2C0%2C0%2C245%2C40/sign=304dee3ab299a9012f38537575fc600e/91529822720e0cf3f8b77cd50046f21fbe09aa5f.jpg")
                .placeholder(R.drawable.img_default)
                .into(controller.imageView());
        mNiceVideoPlayer.setController(controller);
    }

    public List<Clarity> getClarites() {
        List<Clarity> clarities = new ArrayList<>();
        clarities.add(new Clarity("标清", "480P", "https://jzvd.nathen.cn/video/2b64c629-17204eaa4fa-0007-1823-c86-de200.mp4"));
        clarities.add(new Clarity("高清", "720P", "https://jzvd.nathen.cn/video/460bad24-170c5bc6956-0007-1823-c86-de200.mp4"));
        clarities.add(new Clarity("普清", "270P", "https://jzvd.nathen.cn/video/a70a7fb-17204eaa501-0007-1823-c86-de200.mp4"));
        return clarities;
    }

    @Override
    protected void onStop() {
        super.onStop();
        NiceVideoPlayerManager.instance().releaseNiceVideoPlayer();
    }

    @Override
    public void onBackPressed() {
        if (NiceVideoPlayerManager.instance().onBackPressd()) return;
        super.onBackPressed();
    }
}

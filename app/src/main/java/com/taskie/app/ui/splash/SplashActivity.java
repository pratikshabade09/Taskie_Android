package com.taskie.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.taskie.app.R;
import com.taskie.app.ui.main.MainActivity;

/**
 * Splash screen shown at launch for 1.5 seconds.
 * Fades in the logo + tagline, then transitions to MainActivity.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION = 1500L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView tvTagline = findViewById(R.id.tv_tagline);
        ImageView ivLogo   = findViewById(R.id.iv_logo);

        // Fade-in animation
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(800);
        fadeIn.setFillAfter(true);
        ivLogo.startAnimation(fadeIn);
        tvTagline.startAnimation(fadeIn);

        // Navigate after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }, SPLASH_DURATION);
    }
}

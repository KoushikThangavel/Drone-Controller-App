package se.bitcraze.crazyfliecontrol2;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.VideoView;
import android.view.GestureDetector;
import android.view.MotionEvent;


import androidx.appcompat.app.AppCompatActivity;

import com.espressif.espdrone.android.R;

public class WalkthroughActivity extends AppCompatActivity {

    private VideoView videoView;
    private Button nextButton, skipButton;
    private int currentStep = 0;
    private GestureDetector gestureDetector;

    // Add your 3 video resources here
    private int[] videoResIds = {
            R.raw.intro1,  // rename your videos accordingly
            R.raw.intro2,
            R.raw.intro3
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_walkthrough); // keep this filename same as XML

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeLeft(); // NEXT video
                        } else {
                            onSwipeRight();  // PREVIOUS video
                        }
                        return true;
                    }
                }
                return false;
            }
        });


        videoView = findViewById(R.id.walkthroughVideo);
        nextButton = findViewById(R.id.nextButton);
        skipButton = findViewById(R.id.skipButton);

        playCurrentVideo();

        nextButton.setOnClickListener(v -> {
            currentStep++;
            if (currentStep < videoResIds.length) {
                playCurrentVideo();
            } else {
                goToMainScreen();
            }
        });

        videoView.setOnCompletionListener(mp -> {
            currentStep++;
            if (currentStep < videoResIds.length) {
                playCurrentVideo(); // Play next video
            } else {
                goToMainScreen(); // All videos done → move to main screen
            }
        });
        skipButton.setOnClickListener(v -> goToMainScreen());
    }

    private void playCurrentVideo() {
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + videoResIds[currentStep]);
        videoView.setVideoURI(uri);
        videoView.start();
    }

    private void goToMainScreen() {
        startActivity(new Intent(WalkthroughActivity.this, MainActivity.class)); // Change to your main activity
        finish();
    }

    private void onSwipeRight() {
        if (currentStep < videoResIds.length - 1) {
            currentStep++;
            playCurrentVideo();
        }
    }

    private void onSwipeLeft() {
        if (currentStep > 0) {
            currentStep--;
            playCurrentVideo();
        }
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

}

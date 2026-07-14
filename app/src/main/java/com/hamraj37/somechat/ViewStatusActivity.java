package com.hamraj37.somechat;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.hamraj37.somechat.models.Status;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ViewStatusActivity extends BaseActivity {

    private Status status;
    private int currentIndex = 0;
    private long duration = 5000; // 5 seconds per status
    
    private ImageView statusImageView;
    private TextView statusTextView;
    private ImageView profileImage;
    private TextView userNameText;
    private TextView statusTimeText;
    private LinearLayout progressContainer;

    private Handler handler = new Handler();
    private Runnable nextStatusRunnable;
    private List<ProgressBar> progressBars = new ArrayList<>();

    private static final int[] BG_COLORS = {
        0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3,
        0xFF009688, 0xFF4CAF50, 0xFFFF9800, 0xFF795548, 0xFF607D8B
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_status);

        status = (Status) getIntent().getSerializableExtra("status");
        if (status == null || status.getItems() == null || status.getItems().isEmpty()) {
            finish();
            return;
        }

        statusImageView = findViewById(R.id.status_image_view);
        statusTextView = findViewById(R.id.status_text_view);
        profileImage = findViewById(R.id.status_profile_image);
        userNameText = findViewById(R.id.status_user_name);
        statusTimeText = findViewById(R.id.status_time);
        progressContainer = findViewById(R.id.progress_container);

        setupProgressBars();
        updateUI();

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        findViewById(R.id.skip).setOnClickListener(v -> nextStatus());
        findViewById(R.id.reverse).setOnClickListener(v -> previousStatus());
        
        startTimer();
    }

    private void setupProgressBars() {
        progressContainer.removeAllViews();
        progressBars.clear();
        for (int i = 0; i < status.getItems().size(); i++) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setLayoutParams(new LinearLayout.LayoutParams(0, 8, 1));
            pb.setPadding(4, 0, 4, 0);
            pb.setMax(100);
            pb.setProgress(0);
            progressContainer.addView(pb);
            progressBars.add(pb);
        }
    }

    private void updateUI() {
        Status.StatusItem item = status.getItems().get(currentIndex);
        
        userNameText.setText(status.getUserName());
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        statusTimeText.setText(sdf.format(new Date(item.getTimestamp())));

        if (status.getProfilePic() != null && !status.getProfilePic().isEmpty()) {
            Glide.with(this).load(status.getProfilePic()).circleCrop().into(profileImage);
        }

        if ("text".equals(item.getType())) {
            statusImageView.setVisibility(View.GONE);
            statusTextView.setVisibility(View.VISIBLE);
            statusTextView.setText(item.getCaption());
            
            // Generate a background color based on status id
            int color = BG_COLORS[Math.abs(item.getId().hashCode()) % BG_COLORS.length];
            statusTextView.setBackgroundColor(color);
        } else {
            statusImageView.setVisibility(View.VISIBLE);
            statusTextView.setVisibility(View.GONE);
            Glide.with(this).load(item.getMediaUrl()).into(statusImageView);
        }

        for (int i = 0; i < progressBars.size(); i++) {
            if (i < currentIndex) progressBars.get(i).setProgress(100);
            else progressBars.get(i).setProgress(0);
        }
    }

    private void startTimer() {
        handler.removeCallbacksAndMessages(null);
        nextStatusRunnable = new Runnable() {
            int progress = 0;
            @Override
            public void run() {
                if (progress <= 100) {
                    progressBars.get(currentIndex).setProgress(progress);
                    progress += 2; // Increment progress
                    handler.postDelayed(this, duration / 50);
                } else {
                    nextStatus();
                }
            }
        };
        handler.post(nextStatusRunnable);
    }

    private void nextStatus() {
        if (currentIndex < status.getItems().size() - 1) {
            currentIndex++;
            updateUI();
            startTimer();
        } else {
            finish();
        }
    }

    private void previousStatus() {
        if (currentIndex > 0) {
            currentIndex--;
            updateUI();
            startTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

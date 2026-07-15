package com.hamraj37.somechat.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hamraj37.somechat.BaseActivity;
import com.hamraj37.somechat.R;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CreateThemeActivity extends BaseActivity {

    private SharedPreferences prefs;
    private MaterialCardView cardSent, cardReceived;
    private TextView textSent, textReceived;
    private ImageView previewBg;
    
    private int sentColor, receivedColor;
    private int sentTextColor, receivedTextColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_theme);

        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        cardSent = findViewById(R.id.card_sent);
        cardReceived = findViewById(R.id.card_received);
        textSent = findViewById(R.id.text_sent);
        textReceived = findViewById(R.id.text_received);
        previewBg = findViewById(R.id.preview_bg);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        loadCurrentTheme();
        setupColorRecyclers();

        findViewById(R.id.btn_save_theme).setOnClickListener(v -> saveTheme());
        findViewById(R.id.btn_reset_theme).setOnClickListener(v -> resetToDefault());
    }

    private void resetToDefault() {
        sentColor = Color.parseColor("#E1FFC7");
        receivedColor = Color.WHITE;
        sentTextColor = Color.BLACK;
        receivedTextColor = Color.BLACK;
        updatePreview();
        Toast.makeText(this, "Reset to default colors", Toast.LENGTH_SHORT).show();
    }

    private void loadCurrentTheme() {
        // Load background
        String bgPath = prefs.getString("chat_background_path", null);
        if (bgPath != null) {
            if (bgPath.startsWith("res:")) {
                int resId = getResources().getIdentifier(bgPath.replace("res:", ""), "drawable", getPackageName());
                if (resId != 0) Glide.with(this).load(resId).into(previewBg);
            } else {
                File file = new File(bgPath);
                if (file.exists()) Glide.with(this).load(file).into(previewBg);
            }
        }

        // Default colors
        sentColor = prefs.getInt("theme_sent_color", Color.parseColor("#E1FFC7")); // WhatsApp light green
        receivedColor = prefs.getInt("theme_received_color", Color.WHITE);
        sentTextColor = prefs.getInt("theme_sent_text_color", Color.BLACK);
        receivedTextColor = prefs.getInt("theme_received_text_color", Color.BLACK);

        updatePreview();
    }

    private void updatePreview() {
        cardSent.setCardBackgroundColor(sentColor);
        cardReceived.setCardBackgroundColor(receivedColor);
        textSent.setTextColor(sentTextColor);
        textReceived.setTextColor(receivedTextColor);
    }

    private void setupColorRecyclers() {
        int[] colors = {
                Color.parseColor("#E1FFC7"), // WhatsApp
                Color.parseColor("#DCF8C6"), // WhatsApp Alternate
                Color.parseColor("#0088CC"), // Telegram Blue
                Color.parseColor("#34B7F1"), // Light Blue
                Color.parseColor("#FFFFFF"), // White
                Color.parseColor("#F0F0F0"), // Gray
                Color.parseColor("#212121"), // Dark
                Color.parseColor("#FF8A80"), // Red
                Color.parseColor("#B39DDB"), // Purple
                Color.parseColor("#81C784"), // Green
                Color.parseColor("#FFF176"), // Yellow
                Color.parseColor("#FFB74D")  // Orange
        };

        RecyclerView recyclerSent = findViewById(R.id.recycler_sent_colors);
        recyclerSent.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerSent.setAdapter(new ColorAdapter(colors, color -> {
            sentColor = color;
            sentTextColor = getContrastColor(color);
            updatePreview();
        }));

        RecyclerView recyclerReceived = findViewById(R.id.recycler_received_colors);
        recyclerReceived.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerReceived.setAdapter(new ColorAdapter(colors, color -> {
            receivedColor = color;
            receivedTextColor = getContrastColor(color);
            updatePreview();
        }));
    }

    private int getContrastColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }

    private void saveTheme() {
        prefs.edit()
                .putInt("theme_sent_color", sentColor)
                .putInt("theme_received_color", receivedColor)
                .putInt("theme_sent_text_color", sentTextColor)
                .putInt("theme_received_text_color", receivedTextColor)
                .apply();
        Toast.makeText(this, "Theme saved successfully", Toast.LENGTH_SHORT).show();
        finish();
    }

    private static class ColorAdapter extends RecyclerView.Adapter<ColorAdapter.ViewHolder> {
        private final int[] colors;
        private final OnColorClickListener listener;

        public interface OnColorClickListener {
            void onColorClick(int color);
        }

        public ColorAdapter(int[] colors, OnColorClickListener listener) {
            this.colors = colors;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_color_circle, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int color = colors[position];
            holder.colorView.setBackgroundColor(color);
            holder.itemView.setOnClickListener(v -> listener.onColorClick(color));
        }

        @Override
        public int getItemCount() { return colors.length; }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View colorView;
            ViewHolder(View v) {
                super(v);
                colorView = v.findViewById(R.id.color_view);
            }
        }
    }
}
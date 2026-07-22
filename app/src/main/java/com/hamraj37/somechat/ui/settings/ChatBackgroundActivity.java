package com.hamraj37.somechat.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.widget.Toolbar;
import com.bumptech.glide.Glide;
import com.hamraj37.somechat.BaseActivity;
import com.hamraj37.somechat.R;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ChatBackgroundActivity extends BaseActivity {

    private ImageView previewBackground;
    private SharedPreferences prefs;
    private String selectedValue = null;
    private boolean isCustom = false;
    private BackgroundAdapter adapter;
    private final List<Integer> presets = new ArrayList<>();

    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    processCustomImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_background);

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        previewBackground = findViewById(R.id.preview_background);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        setupPresets();
        loadCurrent();

        findViewById(R.id.btn_choose_custom).setOnClickListener(v -> mGetContent.launch("image/*"));
        findViewById(R.id.btn_emoji_pattern).setOnClickListener(v -> showEmojiInputDialog());
        findViewById(R.id.btn_remove).setOnClickListener(v -> {
            selectedValue = null;
            isCustom = false;
            adapter.setSelectedPos(-1);
            previewBackground.setImageResource(R.drawable.bg_glass_main);
        });

        findViewById(R.id.btn_apply).setOnClickListener(v -> apply());
    }

    private void loadCurrent() {
        String path = prefs.getString("chat_background_path", null);
        if (path != null) {
            selectedValue = path;
            if (path.startsWith("res:")) {
                isCustom = false;
                String resName = path.replace("res:", "");
                int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
                if (resId != 0) {
                    Glide.with(this).load(resId).into(previewBackground);
                    // Find and select in adapter
                    for (int i = 0; i < presets.size(); i++) {
                        if (presets.get(i) == resId) {
                            adapter.setSelectedPos(i);
                            break;
                        }
                    }
                }
            } else {
                isCustom = true;
                Glide.with(this).load(new File(path)).into(previewBackground);
                adapter.setSelectedPos(-1);
            }
        } else {
            // Default is glassy
            adapter.setSelectedPos(0);
        }

        // Load bubble colors
        int sentColor = prefs.getInt("theme_sent_color", android.graphics.Color.parseColor("#DCF8C6"));
        int receivedColor = prefs.getInt("theme_received_color", android.graphics.Color.WHITE);
        int sentTextColor = prefs.getInt("theme_sent_text_color", android.graphics.Color.BLACK);
        int receivedTextColor = prefs.getInt("theme_received_text_color", android.graphics.Color.BLACK);

        MaterialCardView cardSent = findViewById(R.id.preview_card_sent);
        MaterialCardView cardReceived = findViewById(R.id.preview_card_received);
        TextView textSent = findViewById(R.id.preview_text_sent);
        TextView textReceived = findViewById(R.id.preview_text_received);

        if (cardSent != null) cardSent.setCardBackgroundColor(sentColor);
        if (cardReceived != null) cardReceived.setCardBackgroundColor(receivedColor);
        if (textSent != null) textSent.setTextColor(sentTextColor);
        if (textReceived != null) textReceived.setTextColor(receivedTextColor);
    }


    private void setupPresets() {
        RecyclerView recyclerView = findViewById(R.id.recycler_backgrounds);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        presets.clear();
        presets.add(R.drawable.bg_glass_main);
        presets.add(R.drawable.bg_chat_whatsapp);
        presets.add(R.drawable.bg_chat_telegram);
        presets.add(R.drawable.bg_chat_instagram);
        presets.add(R.drawable.bg_chat_dark);
        presets.add(R.drawable.bg_chat_sky);

        adapter = new BackgroundAdapter(presets, resId -> {
            isCustom = false;
            selectedValue = "res:" + getResources().getResourceEntryName(resId);
            Glide.with(this).load(resId).into(previewBackground);
        });
        recyclerView.setAdapter(adapter);
    }

    private void showEmojiInputDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter emojis (e.g. 🌸✨🐱)");
        layout.addView(input);

        android.widget.CheckBox randomCheck = new android.widget.CheckBox(this);
        randomCheck.setText(R.string.random_positioning);
        layout.addView(randomCheck);

        android.widget.CheckBox useCurrentCheck = new android.widget.CheckBox(this);
        useCurrentCheck.setText(R.string.add_to_current);
        layout.addView(useCurrentCheck);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.emoji_pattern)
                .setView(layout)
                .setPositiveButton("Create", (dialog, which) -> {
                    String emojis = input.getText().toString().trim();
                    if (!emojis.isEmpty()) {
                        createEmojiBackground(emojis, randomCheck.isChecked(), useCurrentCheck.isChecked());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createEmojiBackground(String emojis, boolean isRandom, boolean useCurrent) {
        try {
            int width = 1080;
            int height = 1920;
            android.graphics.Bitmap bitmap;
            
            if (useCurrent) {
                bitmap = getCurrentBackgroundBitmap(width, height);
            } else {
                bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
            }
            
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            
            if (!useCurrent) {
                // Background color (matching theme surface)
                boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                canvas.drawColor(isNight ? android.graphics.Color.parseColor("#121212") : android.graphics.Color.parseColor("#F5F5F5"));
            }

            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setTextSize(80);
            paint.setAntiAlias(true);

            String[] emojiArray = splitEmojis(emojis);
            java.util.Random random = new java.util.Random();

            if (isRandom) {
                int count = 60; // Number of emojis to scatter
                for (int i = 0; i < count; i++) {
                    float x = random.nextFloat() * width;
                    float y = random.nextFloat() * height;
                    float size = 60 + random.nextFloat() * 80; // Random size
                    paint.setTextSize(size);
                    paint.setAlpha(150 + random.nextInt(105)); // Random transparency
                    
                    canvas.save();
                    canvas.rotate(random.nextFloat() * 360, x, y); // Random rotation
                    canvas.drawText(emojiArray[random.nextInt(emojiArray.length)], x, y, paint);
                    canvas.restore();
                }
            } else {
                float stepX = 200;
                float stepY = 250;
                int emojiIndex = 0;

                for (float y = stepY / 2; y < height + stepY; y += stepY) {
                    boolean offset = ((int) (y / stepY)) % 2 == 0;
                    for (float x = offset ? 0 : stepX / 2; x < width + stepX; x += stepX) {
                        canvas.drawText(emojiArray[emojiIndex % emojiArray.length], x, y, paint);
                        emojiIndex++;
                    }
                }
            }

            File file = new File(getFilesDir(), "chat_background_temp.jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out);
            }

            isCustom = true;
            selectedValue = file.getAbsolutePath();
            adapter.setSelectedPos(-1);
            Glide.with(this)
                    .load(file)
                    .signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
                    .into(previewBackground);

        } catch (Exception e) {
            Toast.makeText(this, "Failed to create pattern: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private android.graphics.Bitmap getCurrentBackgroundBitmap(int width, int height) {
        android.graphics.Bitmap result = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(result);
        
        android.graphics.drawable.Drawable drawable = previewBackground.getDrawable();
        if (drawable != null) {
            // Draw current drawable onto our new bitmap, scaled
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
        } else {
            // Fallback to theme color if no drawable
            boolean isNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            canvas.drawColor(isNight ? android.graphics.Color.parseColor("#121212") : android.graphics.Color.parseColor("#F5F5F5"));
        }
        return result;
    }

    private String[] splitEmojis(String emojis) {
        java.util.List<String> list = new java.util.ArrayList<>();
        int i = 0;
        while (i < emojis.length()) {
            int cp = emojis.codePointAt(i);
            int charCount = Character.charCount(cp);
            list.add(emojis.substring(i, i + charCount));
            i += charCount;
        }
        return list.toArray(new String[0]);
    }

    private void processCustomImage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Could not open image", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(getFilesDir(), "chat_background_temp.jpg");
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
            inputStream.close();
            
            isCustom = true;
            selectedValue = file.getAbsolutePath();
            adapter.setSelectedPos(-1);
            
            // Use signature to ensure Glide detects file changes
            Glide.with(this)
                    .load(file)
                    .signature(new com.bumptech.glide.signature.ObjectKey(System.currentTimeMillis()))
                    .into(previewBackground);

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void apply() {
        if (selectedValue == null) {
            prefs.edit().remove("chat_background_path").apply();
            File file = new File(getFilesDir(), "chat_background.jpg");
            if (file.exists()) file.delete();
        } else if (isCustom) {
            File temp = new File(getFilesDir(), "chat_background_temp.jpg");
            File permanent = new File(getFilesDir(), "chat_background.jpg");
            if (temp.exists()) {
                if (permanent.exists()) permanent.delete();
                temp.renameTo(permanent);
            }
            prefs.edit().putString("chat_background_path", permanent.getAbsolutePath()).apply();
        } else {
            prefs.edit().putString("chat_background_path", selectedValue).apply();
        }
        Toast.makeText(this, "Background applied", Toast.LENGTH_SHORT).show();
        finish();
    }

    private static class BackgroundAdapter extends RecyclerView.Adapter<BackgroundAdapter.ViewHolder> {
        private final List<Integer> items;
        private final OnItemClickListener listener;
        private int selectedPos = -1;

        public interface OnItemClickListener {
            void onItemClick(int resId);
        }

        public BackgroundAdapter(List<Integer> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        public void setSelectedPos(int pos) {
            int old = selectedPos;
            selectedPos = pos;
            notifyItemChanged(old);
            if (pos != -1) notifyItemChanged(pos);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_background, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int resId = items.get(position);
            holder.image.setImageResource(resId);
            ((MaterialCardView) holder.itemView).setStrokeWidth(selectedPos == position ? 4 : 0);
            ((MaterialCardView) holder.itemView).setStrokeColor(selectedPos == position ? 
                    com.google.android.material.color.MaterialColors.getColor(holder.itemView, androidx.appcompat.R.attr.colorPrimary) : 0);

            holder.itemView.setOnClickListener(v -> {
                int oldPos = selectedPos;
                selectedPos = holder.getBindingAdapterPosition();
                notifyItemChanged(oldPos);
                notifyItemChanged(selectedPos);
                listener.onItemClick(resId);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            ViewHolder(View v) {
                super(v);
                image = v.findViewById(R.id.background_thumbnail);
            }
        }
    }
}
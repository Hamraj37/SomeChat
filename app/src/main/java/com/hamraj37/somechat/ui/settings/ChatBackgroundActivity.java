package com.hamraj37.somechat.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsControllerCompat;
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

        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);

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
        findViewById(R.id.btn_remove).setOnClickListener(v -> {
            selectedValue = null;
            isCustom = false;
            previewBackground.setImageDrawable(null);
        });

        findViewById(R.id.btn_apply).setOnClickListener(v -> apply());
    }

    private void loadCurrent() {
        String path = prefs.getString("chat_background_path", null);
        if (path != null) {
            selectedValue = path;
            if (path.startsWith("res:")) {
                isCustom = false;
                int resId = getResources().getIdentifier(path.replace("res:", ""), "drawable", getPackageName());
                if (resId != 0) Glide.with(this).load(resId).into(previewBackground);
            } else {
                isCustom = true;
                Glide.with(this).load(new File(path)).into(previewBackground);
            }
        }
    }

    private void setupPresets() {
        RecyclerView recyclerView = findViewById(R.id.recycler_backgrounds);
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        
        List<Integer> presets = new ArrayList<>();
        presets.add(R.drawable.bg_chat_whatsapp);
        presets.add(R.drawable.bg_chat_telegram);
        presets.add(R.drawable.bg_chat_instagram);
        presets.add(R.drawable.bg_chat_dark);
        presets.add(R.drawable.bg_chat_sky);

        BackgroundAdapter adapter = new BackgroundAdapter(presets, resId -> {
            isCustom = false;
            selectedValue = "res:" + getResources().getResourceEntryName(resId);
            Glide.with(this).load(resId).into(previewBackground);
        });
        recyclerView.setAdapter(adapter);
    }

    private void processCustomImage(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            File file = new File(getFilesDir(), "chat_background_temp.jpg");
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
            isCustom = true;
            selectedValue = file.getAbsolutePath();
            Glide.with(this).load(file).into(previewBackground);
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

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_background, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int resId = items.get(position);
            holder.image.setImageResource(resId);
            ((MaterialCardView) holder.itemView).setStrokeColor(selectedPos == position ? 
                    holder.itemView.getContext().getColor(R.color.whatsapp_green) : 0);

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
package com.hamraj37.somechat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.hamraj37.somechat.fragments.MyQRCodeFragment;
import com.hamraj37.somechat.fragments.ScanQRCodeFragment;

import java.io.File;
import java.io.FileOutputStream;

public class QRCodeActivity extends BaseActivity {

    private String name;
    private String photoUrl;
    private String uid;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Show status bar icons correctly
        boolean isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isNightMode);

        setContentView(R.layout.activity_qr_code);

        name = getIntent().getStringExtra("name");
        photoUrl = getIntent().getStringExtra("photoUrl");
        uid = getIntent().getStringExtra("uid");

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.qr_code);
        }

        ViewPager2 viewPager = findViewById(R.id.qr_view_pager);
        this.viewPager = viewPager;
        TabLayout tabLayout = findViewById(R.id.qr_tabs);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return MyQRCodeFragment.newInstance(name, photoUrl, uid);
                } else {
                    return new ScanQRCodeFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? getString(R.string.my_code) : getString(R.string.scan_code));
        }).attach();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.qr_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_share) {
            shareProfileAsImage();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareProfileAsImage() {
        if (viewPager.getCurrentItem() != 0) {
            viewPager.setCurrentItem(0);
        }

        viewPager.postDelayed(() -> {
            View fragmentView = findViewWithTagRecursively(viewPager, "my_qr_view");
            if (fragmentView != null) {
                captureAndShare(fragmentView);
            } else {
                captureAndShare(viewPager);
            }
        }, 150);
    }

    private View findViewWithTagRecursively(android.view.ViewGroup root, Object tag) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (tag.equals(child.getTag())) {
                return child;
            }
            if (child instanceof android.view.ViewGroup) {
                View found = findViewWithTagRecursively((android.view.ViewGroup) child, tag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void captureAndShare(View view) {
        try {
            // If it's a ScrollView, we want to capture the full content, not just visible area
            Bitmap bitmap;
            if (view instanceof android.widget.ScrollView) {
                android.widget.ScrollView sv = (android.widget.ScrollView) view;
                View content = sv.getChildAt(0);
                bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                content.draw(canvas);
            } else {
                bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                view.draw(canvas);
            }

            File cachePath = new File(getExternalCacheDir(), "images");
            if (!cachePath.exists()) cachePath.mkdirs();
            File file = new File(cachePath, "profile_qr.png");
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);

            if (contentUri != null) {
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.setType("image/png");
                startActivity(Intent.createChooser(shareIntent, "Share Profile QR"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show();
        }
    }
}

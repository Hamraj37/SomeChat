package com.hamraj37.somechat.fragments;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.zxing.BarcodeFormat;
import com.hamraj37.somechat.R;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class MyQRCodeFragment extends Fragment {

    private String name;
    private String photoUrl;
    private String uid;

    public static MyQRCodeFragment newInstance(String name, String photoUrl, String uid) {
        MyQRCodeFragment fragment = new MyQRCodeFragment();
        Bundle args = new Bundle();
        args.putString("name", name);
        args.putString("photoUrl", photoUrl);
        args.putString("uid", uid);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            name = getArguments().getString("name");
            photoUrl = getArguments().getString("photoUrl");
            uid = getArguments().getString("uid");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_qr, container, false);
        view.setTag("my_qr_view");

        TextView nameText = view.findViewById(R.id.user_name);
        ImageView profileImage = view.findViewById(R.id.user_profile_image);
        ImageView qrImage = view.findViewById(R.id.qr_image);

        if (name != null) nameText.setText(name.toUpperCase());
        
        // Load the main profile picture at the top of the card
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .into(profileImage);
        }

        // Generate QR code with App Icon in the center
        generateQRCodeWithAppIcon(qrImage);

        return view;
    }

    private void generateQRCodeWithAppIcon(ImageView imageView) {
        android.graphics.drawable.Drawable drawable = androidx.core.content.res.ResourcesCompat.getDrawable(getResources(), R.mipmap.ic_launcher_round, null);
        if (drawable != null) {
            Bitmap logo;
            if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                logo = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
            } else {
                logo = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(logo);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            generateQRCodeWithLogo(imageView, logo);
        } else {
            generateQRCode(imageView);
        }
    }

    private void generateQRCode(ImageView imageView) {
        String qrData = "somechat_profile:" + uid;
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(qrData, BarcodeFormat.QR_CODE, 800, 800);
            imageView.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateQRCodeWithLogo(ImageView imageView, Bitmap logo) {
        String qrData = "somechat_profile:" + uid;
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);

            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(qrData, BarcodeFormat.QR_CODE, 800, 800, hints);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }

            // Draw logo in the center
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            int logoWidth = width / 5;
            int logoHeight = height / 5;
            int left = (width - logoWidth) / 2;
            int top = (height - logoHeight) / 2;
            
            // White background for logo area
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(android.graphics.Color.WHITE);
            canvas.drawRect(left - 10, top - 10, left + logoWidth + 10, top + logoHeight + 10, paint);
            
            Bitmap scaledLogo = Bitmap.createScaledBitmap(logo, logoWidth, logoHeight, true);
            canvas.drawBitmap(scaledLogo, left, top, null);

            imageView.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            generateQRCode(imageView);
        }
    }
}

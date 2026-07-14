package com.hamraj37.somechat.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hamraj37.somechat.ProfileInfoActivity;
import com.hamraj37.somechat.R;
import com.journeyapps.barcodescanner.CompoundBarcodeView;

public class ScanQRCodeFragment extends Fragment {

    private CompoundBarcodeView barcodeView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan_qr, container, false);
        barcodeView = view.findViewById(R.id.barcode_scanner);
        
        barcodeView.decodeContinuous(result -> {
            if (result.getText() != null) {
                String scannedUid = result.getText();
                if (scannedUid.startsWith("somechat_profile:")) {
                    barcodeView.pause();
                    String uid = scannedUid.substring("somechat_profile:".length());
                    Intent intent = new Intent(getContext(), ProfileInfoActivity.class);
                    intent.putExtra("uid", uid);
                    startActivity(intent);
                    if (getActivity() != null) getActivity().finish();
                } else {
                    Toast.makeText(getContext(), R.string.invalid_qr, Toast.LENGTH_SHORT).show();
                }
            }
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        barcodeView.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        barcodeView.pause();
    }
}

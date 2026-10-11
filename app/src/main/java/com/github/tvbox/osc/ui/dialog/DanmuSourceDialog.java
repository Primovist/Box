package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.HawkUtils;

public class DanmuSourceDialog extends BaseDialog {

    public interface OnSavedListener {
        void onSaved(String url);
    }

    public DanmuSourceDialog(@NonNull Context context, OnSavedListener listener) {
        super(context);
        if (context instanceof Activity) setOwnerActivity((Activity) context);
        setContentView(R.layout.dialog_danmu_source);
        setCanceledOnTouchOutside(true);

        EditText input = findViewById(R.id.inputDanmuApi);
        input.setText(HawkUtils.getDanmuApiUrl());
        findViewById(R.id.btnDanmuApiSave).setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            HawkUtils.setDanmuApiUrl(url);
            if (listener != null) listener.onSaved(url);
            dismiss();
        });
        findViewById(R.id.btnDanmuApiClear).setOnClickListener(v -> {
            HawkUtils.setDanmuApiUrl("");
            if (listener != null) listener.onSaved("");
            dismiss();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            View save = findViewById(R.id.btnDanmuApiSave);
            save.requestFocus();
            return false;
        });
    }
}

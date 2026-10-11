package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.Context;

import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;

import java.util.ArrayList;

import master.flame.danmaku.ui.widget.DanmakuView;

public final class DanmuMenuDialog {

    public interface DialogHolder {
        void setDialog(Dialog dialog);
        void clearDialog(Dialog dialog);
    }

    private DanmuMenuDialog() {
    }

    public static void show(Context context, VodController controller, DanmakuView danmakuView,
                            String searchWord, SearchDanmuDialog.Listener listener,
                            DialogHolder holder) {
        if (controller != null) controller.pauseBottomAutoHide();
        ArrayList<String> actions = new ArrayList<>();
        actions.add("在线搜索弹幕");
        actions.add("弹幕显示设置");
        actions.add("弹幕源设置");

        SelectDialog<String> menu = new SelectDialog<>(context);
        menu.setTip("弹幕");
        final boolean[] openingChild = {false};
        menu.setOnDismissListener(dialog -> {
            if (!openingChild[0] && holder != null) holder.clearDialog(menu);
            if (!openingChild[0] && controller != null) controller.resumeBottomAutoHide();
        });
        menu.setAdapter(null, new SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int position) {
                openingChild[0] = true;
                menu.dismiss();
                if (position == 0) {
                    SearchDanmuDialog search = new SearchDanmuDialog(context, listener);
                    if (holder != null) holder.setDialog(search);
                    search.setOnDismissListener(dialog -> {
                        if (holder != null) holder.clearDialog(search);
                        if (controller != null) controller.resumeBottomAutoHide();
                    });
                    search.show();
                    search.searchDefault(searchWord);
                } else if (position == 1) {
                    DanmuSettingDialog settings = new DanmuSettingDialog(context, danmakuView);
                    if (holder != null) holder.setDialog(settings);
                    settings.setOnDismissListener(dialog -> {
                        if (holder != null) holder.clearDialog(settings);
                        if (controller != null) controller.resumeBottomAutoHide();
                    });
                    settings.show();
                } else {
                    DanmuSourceDialog source = new DanmuSourceDialog(context, null);
                    if (holder != null) holder.setDialog(source);
                    source.setOnDismissListener(dialog -> {
                        if (holder != null) holder.clearDialog(source);
                        if (controller != null) controller.resumeBottomAutoHide();
                    });
                    source.show();
                }
            }

            @Override
            public String getDisplay(String value) {
                return value;
            }
        }, SelectDialogAdapter.stringDiff, actions, 0);
        if (holder != null) holder.setDialog(menu);
        menu.show();
    }
}

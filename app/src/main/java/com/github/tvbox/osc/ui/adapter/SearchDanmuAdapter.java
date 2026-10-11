package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

public class SearchDanmuAdapter extends BaseQuickAdapter<String, BaseViewHolder> {

    public SearchDanmuAdapter() {
        super(R.layout.item_search_danmu, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, String title) {
        holder.setText(R.id.tvDanmuItem, title);
    }
}

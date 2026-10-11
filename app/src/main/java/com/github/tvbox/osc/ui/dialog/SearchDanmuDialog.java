package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.ui.adapter.SearchDanmuAdapter;
import com.github.tvbox.osc.util.HawkUtils;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchDanmuDialog extends BaseDialog {

    public interface Listener {
        void onEpisodeLoading();
        void onDanmuLoaded(String xml, String episodeTitle);
        void onError(String message);
        void onSelectionCancelled();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<DanmakuApi.Anime> animeResults = new ArrayList<>();
    private final List<DanmakuApi.Episode> episodeResults = new ArrayList<>();
    private final SearchDanmuAdapter adapter = new SearchDanmuAdapter();
    private final EditText input;
    private final TextView status;
    private final TextView backButton;
    private final ProgressBar progress;
    private final TvRecyclerView list;
    private final Listener listener;
    private DanmuSourceDialog sourceSettingsDialog;

    private boolean showingEpisodes;
    private boolean selectionInProgress;
    private int requestGeneration;
    private String searchWord = "";

    public SearchDanmuDialog(@NonNull Context context, Listener listener) {
        super(context);
        if (context instanceof Activity) setOwnerActivity((Activity) context);
        setContentView(R.layout.dialog_search_danmu);
        setCanceledOnTouchOutside(true);
        setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (showingEpisodes) {
                        showAnimeResults();
                    } else {
                        dismiss();
                    }
                }
                return true;
            }
            return false;
        });
        this.listener = listener;

        input = findViewById(R.id.inputDanmuSearch);
        status = findViewById(R.id.tvDanmuSearchStatus);
        backButton = findViewById(R.id.btnDanmuSearchBack);
        progress = findViewById(R.id.pbDanmuSearch);
        list = findViewById(R.id.listDanmuSearch);
        list.setLayoutManager(new V7LinearLayoutManager(context, 1, false));
        list.setAdapter(adapter);
        adapter.setNewData(new ArrayList<>());
        adapter.setOnItemClickListener((baseQuickAdapter, view, position) -> onItemClick(position));

        findViewById(R.id.btnDanmuSearch).setOnClickListener(v -> search(input.getText().toString().trim()));
        findViewById(R.id.btnDanmuSearchConfig).setOnClickListener(v -> openSourceSettings());
        backButton.setOnClickListener(v -> showAnimeResults());
        input.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                search(input.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    public void searchDefault(String keyword) {
        searchWord = keyword == null ? "" : keyword.trim();
        input.setText(searchWord);
        input.setSelection(input.length());
        search(searchWord);
    }

    private void search(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            Toast.makeText(getContext(), "请输入影片名称", Toast.LENGTH_SHORT).show();
            return;
        }
        cancelSelectionIfNeeded();
        final int generation = ++requestGeneration;
        searchWord = keyword;
        input.setText(keyword);
        showingEpisodes = false;
        backButton.setVisibility(View.GONE);
        animeResults.clear();
        episodeResults.clear();
        adapter.setNewData(new ArrayList<>());
        if (TextUtils.isEmpty(HawkUtils.getDanmuApiUrl())) {
            openSourceSettings();
            return;
        }
        setLoading(true, "正在搜索作品…");
        executor.execute(() -> {
            try {
                List<DanmakuApi.Anime> result = DanmakuApi.searchAnime(keyword);
                mainHandler.post(() -> {
                    if (!isCurrent(generation)) return;
                    animeResults.clear();
                    animeResults.addAll(result);
                    List<String> titles = new ArrayList<>();
                    for (DanmakuApi.Anime anime : result) titles.add(anime.title);
                    adapter.setNewData(titles);
                    setLoading(false, result.isEmpty() ? "没有找到匹配作品" : "找到 " + result.size() + " 个作品，请选择作品后选集");
                    if (!result.isEmpty()) list.requestFocus();
                });
            } catch (Exception e) {
                postError(generation, e);
            }
        });
    }

    private void onItemClick(int position) {
        if (showingEpisodes) {
            if (position < 0 || position >= episodeResults.size()) return;
            loadEpisode(episodeResults.get(position));
            return;
        }
        if (position < 0 || position >= animeResults.size()) return;
        DanmakuApi.Anime anime = animeResults.get(position);
        final int generation = ++requestGeneration;
        setLoading(true, "正在读取《" + anime.title + "》的集数…");
        executor.execute(() -> {
            try {
                List<DanmakuApi.Episode> result = DanmakuApi.getEpisodes(anime);
                mainHandler.post(() -> {
                    if (!isCurrent(generation)) return;
                    episodeResults.clear();
                    episodeResults.addAll(result);
                    if (result.isEmpty()) {
                        setLoading(false, "此作品没有可选集数");
                        return;
                    }
                    showingEpisodes = true;
                    backButton.setVisibility(View.VISIBLE);
                    List<String> titles = new ArrayList<>();
                    for (DanmakuApi.Episode episode : result) titles.add(episode.title);
                    adapter.setNewData(titles);
                    setLoading(false, "《" + anime.title + "》：选择要加载弹幕的集数");
                    list.requestFocus();
                });
            } catch (Exception e) {
                postError(generation, e);
            }
        });
    }

    private void loadEpisode(DanmakuApi.Episode episode) {
        final int generation = ++requestGeneration;
        selectionInProgress = true;
        if (listener != null) listener.onEpisodeLoading();
        setLoading(true, "正在加载《" + episode.title + "》的弹幕…");
        executor.execute(() -> {
            try {
                String xml = DanmakuApi.getDanmakuXml(episode.id);
                mainHandler.post(() -> {
                    if (!isCurrent(generation)) return;
                    selectionInProgress = false;
                    if (listener != null) listener.onDanmuLoaded(xml, episode.title);
                    dismiss();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isCurrent(generation)) return;
                    selectionInProgress = false;
                    if (listener != null) listener.onError(errorMessage(e));
                    setLoading(false, errorMessage(e));
                });
            }
        });
    }

    private void showAnimeResults() {
        requestGeneration++;
        cancelSelectionIfNeeded();
        showingEpisodes = false;
        backButton.setVisibility(View.GONE);
        List<String> titles = new ArrayList<>();
        for (DanmakuApi.Anime anime : animeResults) titles.add(anime.title);
        adapter.setNewData(titles);
        setLoading(false, "找到 " + animeResults.size() + " 个作品");
        list.requestFocus();
    }

    private void openSourceSettings() {
        if (sourceSettingsDialog != null && sourceSettingsDialog.isShowing()) return;
        sourceSettingsDialog = new DanmuSourceDialog(getContext(), url -> {
            if (!isShowing() || executor.isShutdown()) return;
            if (!TextUtils.isEmpty(url) && !TextUtils.isEmpty(searchWord)) search(searchWord);
        });
        sourceSettingsDialog.setOnDismissListener(dialog -> sourceSettingsDialog = null);
        sourceSettingsDialog.show();
    }

    private void setLoading(boolean loading, String message) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        status.setText(message);
    }

    private void postError(int generation, Exception error) {
        mainHandler.post(() -> {
            if (!isCurrent(generation)) return;
            String message = errorMessage(error);
            setLoading(false, message);
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    private String errorMessage(Exception error) {
        return TextUtils.isEmpty(error.getMessage()) ? "弹幕请求失败" : error.getMessage();
    }

    private boolean isCurrent(int generation) {
        return isShowing() && !executor.isShutdown() && generation == requestGeneration;
    }

    private void cancelSelectionIfNeeded() {
        if (!selectionInProgress) return;
        selectionInProgress = false;
        if (listener != null) listener.onSelectionCancelled();
    }

    @Override
    public void dismiss() {
        requestGeneration++;
        cancelSelectionIfNeeded();
        if (sourceSettingsDialog != null) {
            sourceSettingsDialog.dismiss();
            sourceSettingsDialog = null;
        }
        executor.shutdownNow();
        super.dismiss();
    }
}

package com.github.tvbox.osc.api;

import android.text.TextUtils;

import com.github.tvbox.osc.util.HawkUtils;
import com.github.tvbox.osc.util.OkGoHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/** Client for danmu_api's DandanPlay-compatible v2 endpoints. */
public final class DanmakuApi {

    public static final class Anime {
        public final String id;
        public final String title;
        public final String searchTitle;

        public Anime(String id, String title, String searchTitle) {
            this.id = id;
            this.title = title;
            this.searchTitle = searchTitle;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public static final class Episode {
        public final String id;
        public final String title;

        public Episode(String id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private DanmakuApi() {
    }

    public static List<Anime> searchAnime(String keyword) throws Exception {
        String body = get("/search/anime?keyword=" + encode(keyword));
        JSONObject json = new JSONObject(body);
        ensureSuccess(json);
        JSONArray animes = json.optJSONArray("animes");
        List<Anime> result = new ArrayList<>();
        if (animes == null) return result;
        for (int i = 0; i < animes.length(); i++) {
            JSONObject item = animes.optJSONObject(i);
            if (item == null) continue;
            String id = value(item, "animeId", "bangumiId", "id");
            String title = value(item, "animeTitle", "title", "name");
            if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(title)) {
                String searchTitle = title;
                String year = item.optString("startDate", "");
                if (year.length() >= 4 && !title.contains(year.substring(0, 4))) {
                    title += " (" + year.substring(0, 4) + ")";
                }
                result.add(new Anime(id, title, searchTitle));
            }
        }
        return result;
    }

    public static List<Episode> getEpisodes(Anime anime) throws Exception {
        List<Episode> episodes = new ArrayList<>();
        try {
            JSONObject detail = new JSONObject(get("/bangumi/" + encodePath(anime.id)));
            ensureSuccess(detail);
            JSONObject bangumi = detail.optJSONObject("bangumi");
            episodes = readEpisodes(bangumi == null ? detail : bangumi);
        } catch (HttpFailure failure) {
            if (failure.code != 404) throw failure;
        }
        if (!episodes.isEmpty()) return episodes;

        // Some compatible servers expose the episodes through search/episodes only.
        JSONObject search = new JSONObject(get("/search/episodes?anime=" + encode(anime.searchTitle)));
        ensureSuccess(search);
        JSONArray animes = search.optJSONArray("animes");
        if (animes == null) return episodes;
        for (int i = 0; i < animes.length(); i++) {
            JSONObject item = animes.optJSONObject(i);
            if (item != null && titleMatches(anime.searchTitle, value(item, "animeTitle", "title", "name"))) {
                episodes.addAll(readEpisodes(item));
                break;
            }
        }
        return episodes;
    }

    public static String getDanmakuXml(String episodeId) throws Exception {
        String xml = get("/comment/" + encodePath(episodeId) + "?format=xml");
        String trimmed = xml == null ? "" : xml.trim();
        if (trimmed.startsWith("\uFEFF")) trimmed = trimmed.substring(1).trim();
        int rootIndex = trimmed.indexOf("<i");
        if (rootIndex < 0) rootIndex = trimmed.indexOf("<I");
        if (!trimmed.startsWith("<") || rootIndex < 0 || rootIndex + 2 >= trimmed.length()
                || (trimmed.charAt(rootIndex + 2) != '>' && !Character.isWhitespace(trimmed.charAt(rootIndex + 2)))) {
            throw new Exception("弹幕接口没有返回 XML 内容");
        }
        return trimmed;
    }

    private static List<Episode> readEpisodes(JSONObject parent) {
        List<Episode> result = new ArrayList<>();
        JSONArray episodes = parent.optJSONArray("episodes");
        if (episodes == null) return result;
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject item = episodes.optJSONObject(i);
            if (item == null) continue;
            String id = value(item, "episodeId", "id");
            String title = value(item, "episodeTitle", "episodeName", "title", "name");
            if (!TextUtils.isEmpty(id) && !TextUtils.isEmpty(title)) {
                result.add(new Episode(id, title));
            }
        }
        return result;
    }

    private static String get(String path) throws Exception {
        String base = HawkUtils.getDanmuApiUrl();
        if (TextUtils.isEmpty(base)) throw new Exception("请先设置弹幕 API 地址");
        base = base.trim().replaceAll("/+$", "");
        if (base.endsWith("/api/v2")) base = base.substring(0, base.length() - 7);
        HttpUrl parsed = HttpUrl.parse(base);
        if (parsed == null || !("http".equals(parsed.scheme()) || "https".equals(parsed.scheme()))) {
            throw new Exception("弹幕 API 地址格式错误");
        }
        String url = base + "/api/v2" + path;
        OkHttpClient configuredClient = OkGoHelper.getDefaultClient();
        OkHttpClient.Builder clientBuilder = configuredClient == null
                ? new OkHttpClient.Builder()
                : configuredClient.newBuilder();
        OkHttpClient client = clientBuilder
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder().url(url).get().build();
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                int code = response.code();
                throw new HttpFailure(code, code == 401 || code == 403
                        ? "弹幕 API 认证失败，请检查地址中的 Token"
                        : "弹幕 API 请求失败" + (code > 0 ? " (" + code + ")" : ""));
            }
            return response.body().string();
        }
    }

    private static void ensureSuccess(JSONObject json) throws Exception {
        if (json.has("success") && !json.optBoolean("success", true)) {
            String error = json.optString("errorMessage", "");
            throw new Exception(TextUtils.isEmpty(error) ? "弹幕 API 返回失败" : error);
        }
        int errorCode = json.optInt("errorCode", 0);
        if (errorCode != 0) {
            String error = json.optString("errorMessage", "");
            throw new Exception(TextUtils.isEmpty(error) ? "弹幕 API 错误 " + errorCode : error);
        }
    }

    private static String value(JSONObject item, String... keys) {
        for (String key : keys) {
            Object value = item.opt(key);
            if (value != null && value != JSONObject.NULL) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"0".equals(text)) return text;
            }
        }
        return "";
    }

    private static boolean titleMatches(String expected, String actual) {
        return !TextUtils.isEmpty(actual) && expected.trim().equalsIgnoreCase(actual.trim());
    }

    private static final class HttpFailure extends Exception {
        final int code;

        HttpFailure(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String encodePath(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
    }
}

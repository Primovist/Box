package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtSubscribe {

    private static final Pattern NAME_PATTERN = Pattern.compile(".*,(.+?)$");
    private static final Pattern GROUP_PATTERN = Pattern.compile("group-title=\"(.*?)\"");
    private static final Pattern CATCHUP_SOURCE_PATTERN = Pattern.compile("catchup-source=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> channels,
                             LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> catchupSources,
                             String text) {
        if (text == null) return;
        if (text.startsWith("#EXTM3U")) {
            parseM3u(channels, catchupSources, text);
        } else {
            parseTxt(channels, catchupSources, text);
        }
    }

    private static void parseM3u(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> channels,
                                 LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> catchupSources,
                                 String text) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(text));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#EXTINF")) continue;

                String name = getStrByRegex(NAME_PATTERN, line);
                String group = getStrByRegex(GROUP_PATTERN, line);
                String catchupSource = getStrByRegex(CATCHUP_SOURCE_PATTERN, line);
                String url = reader.readLine();
                if (url != null && !url.trim().isEmpty())
                    addSource(channels, catchupSources, group, name, url.trim(), catchupSource);
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getStrByRegex(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) return matcher.group(1);
        if (pattern == CATCHUP_SOURCE_PATTERN) return "";
        return pattern == GROUP_PATTERN ? "未分组" : "未命名";
    }

    private static void parseTxt(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> channels,
                                 LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> catchupSources,
                                 String text) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(text));
            String group = "未分组";
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] split = line.split(",", 2);
                if (split.length < 2) continue;
                if (line.contains("#genre#")) {
                    group = split[0].trim();
                    continue;
                }

                String name = split[0].trim();
                for (String candidate : split[1].trim().split("#")) {
                    String url = candidate.trim();
                    if (!url.isEmpty() && (url.startsWith("http") || url.startsWith("rtsp") || url.startsWith("rtmp")))
                        addSource(channels, catchupSources, group, name, url, "");
                }
            }
            reader.close();
        } catch (Throwable ignored) {
        }
    }

    private static void addSource(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> channels,
                                  LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> catchupSources,
                                  String group, String name, String url, String catchupSource) {
        LinkedHashMap<String, ArrayList<String>> channelGroup = channels.get(group);
        if (channelGroup == null) {
            channelGroup = new LinkedHashMap<>();
            channels.put(group, channelGroup);
        }
        ArrayList<String> urls = channelGroup.get(name);
        if (urls == null) {
            urls = new ArrayList<>();
            channelGroup.put(name, urls);
        }

        LinkedHashMap<String, ArrayList<String>> catchupGroup = catchupSources.get(group);
        if (catchupGroup == null) {
            catchupGroup = new LinkedHashMap<>();
            catchupSources.put(group, catchupGroup);
        }
        ArrayList<String> templates = catchupGroup.get(name);
        if (templates == null) {
            templates = new ArrayList<>();
            catchupGroup.put(name, templates);
        }

        int existingIndex = urls.indexOf(url);
        if (existingIndex >= 0) {
            while (templates.size() <= existingIndex) templates.add("");
            if (templates.get(existingIndex).isEmpty() && catchupSource != null && !catchupSource.isEmpty())
                templates.set(existingIndex, catchupSource);
            return;
        }

        urls.add(url);
        templates.add(catchupSource == null ? "" : catchupSource);
    }

    public static JsonArray live2JsonArray(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> channels,
                                           LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> catchupSources) {
        JsonArray result = new JsonArray();
        for (String groupName : channels.keySet()) {
            JsonArray channelArray = new JsonArray();
            LinkedHashMap<String, ArrayList<String>> channelGroup = channels.get(groupName);
            LinkedHashMap<String, ArrayList<String>> catchupGroup = catchupSources.get(groupName);
            if (!channelGroup.isEmpty()) {
                for (String channelName : channelGroup.keySet()) {
                    ArrayList<String> urls = channelGroup.get(channelName);
                    if (urls.isEmpty()) continue;

                    JsonArray urlArray = new JsonArray();
                    JsonArray catchupArray = new JsonArray();
                    ArrayList<String> templates = catchupGroup == null ? null : catchupGroup.get(channelName);
                    for (int i = 0; i < urls.size(); i++) {
                        urlArray.add(urls.get(i));
                        catchupArray.add(templates != null && i < templates.size() ? templates.get(i) : "");
                    }

                    JsonObject channel = new JsonObject();
                    channel.addProperty("name", channelName);
                    channel.add("urls", urlArray);
                    channel.add("catchupSources", catchupArray);
                    channelArray.add(channel);
                }

                JsonObject group = new JsonObject();
                group.addProperty("group", groupName);
                group.add("channels", channelArray);
                result.add(group);
            }
        }
        return result;
    }
}

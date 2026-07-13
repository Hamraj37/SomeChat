package com.hamraj37.somechat.utils;

import com.hamraj37.somechat.models.LinkMetadata;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkPreviewUtils {

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public interface LinkPreviewCallback {
        void onMetadataFetched(LinkMetadata metadata);
        void onError(Exception e);
    }

    public static String findFirstUrl(String text) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile("(https?://\\S+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static void fetchMetadata(String url, LinkPreviewCallback callback) {
        executor.execute(() -> {
            try {
                Document doc = Jsoup.connect(url)
                        .timeout(5000)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get();

                String title = getMetaTag(doc, "og:title");
                if (title == null) title = doc.title();

                String description = getMetaTag(doc, "og:description");
                if (description == null) description = getMetaTag(doc, "description");

                String imageUrl = getMetaTag(doc, "og:image");

                LinkMetadata metadata = new LinkMetadata(url, title, description, imageUrl);
                callback.onMetadataFetched(metadata);

            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private static String getMetaTag(Document doc, String attr) {
        Element element = doc.select("meta[property=" + attr + "]").first();
        if (element != null) return element.attr("content");
        
        element = doc.select("meta[name=" + attr + "]").first();
        if (element != null) return element.attr("content");
        
        return null;
    }
}

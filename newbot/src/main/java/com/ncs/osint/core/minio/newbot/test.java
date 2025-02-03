package com.cyai.soar.constanst;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NewsToTelegram {

  private static final String TELEGRAM_BOT_TOKEN = "7671315407:AAGzmiWu0RlZ_TaP8SN-1vaiHePxI9z45jE";
  private static final String TELEGRAM_CHAT_ID = "5882369573";
  private static final String VIETNAMNET_URL = "https://vietnamnet.vn/thoi-su";
  private static final String VNEXPRESS_URL = "https://vnexpress.net/";

  private static final int MAX_ARTICLES_STORED = 500;
  private static final LimitedHashSet<String> sentArticles = new LimitedHashSet<>(MAX_ARTICLES_STORED);

  public static void main(String[] args) {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    scheduler.scheduleAtFixedRate(NewsToTelegram::crawlAndSend, 0, 5, TimeUnit.MINUTES);
  }

  /**
   * Cào dữ liệu từ VietnamNet & VNExpress, gửi bài mới lên Telegram.
   */
  public static void crawlAndSend() {
    try {
      System.out.println("🔍 Đang cào dữ liệu từ VietnamNet & VNExpress...");

      Elements vietnamnetArticles = getArticleLinks(VIETNAMNET_URL, "div.horizontalPost");
      Elements vnexpressArticles = getArticleLinks(VNEXPRESS_URL, "article.item-news");

      sendArticlesToTelegram(vietnamnetArticles, "VietnamNet");
      sendArticlesToTelegram(vnexpressArticles, "VNExpress");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Lấy danh sách bài viết từ trang báo.
   */
  public static Elements getArticleLinks(String url, String cssSelector) throws IOException {
    Document doc = Jsoup.connect(url).get();
    return doc.select(cssSelector);
  }

  /**
   * Xử lý danh sách bài viết và gửi lên Telegram.
   */
  public static void sendArticlesToTelegram(Elements articles, String source) {
    for (Element article : articles) {
      try {
        String title = article.select("h3 a, h3.title-news > a").text();
        String link = article.select("h3 a, h3.title-news > a").attr("href");
        if (!link.startsWith("https")) {
          link = source.equals("VietnamNet") ? "https://vietnamnet.vn" + link : "https://vnexpress.net" + link;
        }

        if (sentArticles.contains(link)) {
          System.out.println("⏩ Bỏ qua bài viết đã gửi: " + title);
          continue;
        }
        sentArticles.add(link);

        String description = article.select("div.horizontalPost__main-desc p, p.description > a").text();
        String imageUrl = article.select("picture source").attr("data-srcset");

        sendToTelegram(title, link, description, imageUrl, source);

      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Gửi tin nhắn lên Telegram.
   */
  public static void sendToTelegram(String title, String link, String description, String imageUrl, String source) {
    try {
      LocalDateTime now = LocalDateTime.now();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
      String formattedTime = now.format(formatter);

      String message = "📰 *" + title + "*\n\n" +
                       "📖 " + description + "\n" +
                       "🌍 Nguồn: " + source + "\n" +
                       "⏳ Gửi lúc: " + formattedTime + "\n" +
                       "🔗 [Đọc bài viết](" + link + ")\n\n";

      if (!imageUrl.isEmpty()) {
        sendPhotoToTelegram(imageUrl, message);
      } else {
        sendTextToTelegram(message);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Gửi tin nhắn văn bản lên Telegram.
   */
  public static void sendTextToTelegram(String message) throws IOException {
    String urlString = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN +
                       "/sendMessage?chat_id=" + TELEGRAM_CHAT_ID +
                       "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8.toString()) +
                       "&parse_mode=Markdown";

    sendRequest(urlString);
  }

  /**
   * Gửi ảnh kèm nội dung lên Telegram.
   */
  public static void sendPhotoToTelegram(String imageUrl, String caption) throws IOException {
    String urlString = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN +
                       "/sendPhoto?chat_id=" + TELEGRAM_CHAT_ID +
                       "&photo=" + URLEncoder.encode(imageUrl, StandardCharsets.UTF_8.toString()) +
                       "&caption=" + URLEncoder.encode(caption, StandardCharsets.UTF_8.toString()) +
                       "&parse_mode=Markdown";

    sendRequest(urlString);
  }

  /**
   * Gửi request HTTP đến Telegram.
   */
  public static void sendRequest(String urlString) throws IOException {
    URL url = new URL(urlString);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    int responseCode = conn.getResponseCode();
    System.out.println("📡 Telegram Response Code: " + responseCode);
  }
}

/**
 * Giới hạn số lượng bài viết đã gửi để tránh tràn bộ nhớ.
 */
class LimitedHashSet<E> extends LinkedHashSet<E> {
  private final int maxSize;

  public LimitedHashSet(int maxSize) {
    this.maxSize = maxSize;
  }

  @Override
  public boolean add(E e) {
    if (size() >= maxSize) {
      this.remove(this.iterator().next()); // Xóa phần tử cũ nhất
    }
    return super.add(e);
  }
}

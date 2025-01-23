package com.ncs.osint.core.minio.newbot;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class NewbotApplication implements CommandLineRunner {

  public static void main(String[] args) {
    SpringApplication.run(NewbotApplication.class, args);
  }

  @Override
  public void run(String... args) throws Exception {
    try {
      TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
      VNExpressAutoBot bot = new VNExpressAutoBot();
      botsApi.registerBot(bot);

      System.out.println("Bot is running...");

      // Bắt đầu một nhiệm vụ định kỳ để gửi tin tức mới nhất
      ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
      scheduler.scheduleAtFixedRate(() -> {
        try {
          bot.sendLatestNews();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }, 0, 30, TimeUnit.MINUTES); // Chạy mỗi phút
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }
  }

  public static class VNExpressAutoBot extends TelegramLongPollingBot {

    private static final String BOT_TOKEN = "7759170307:AAGRfrebGT7wxi7BYxRvw-AjykerhoHWhfI";
    private static final String BOT_USERNAME = "VNExpressJavaBot";
    private static final String BASE_URL = "https://vnexpress.net/";

    private static final String CHAT_ID = "5882369573"; // Chat ID để gửi tin nhắn tự động

    private final Set<String> sentArticles = new HashSet<>(); // Lưu trữ bài viết đã gửi để tránh trùng lặp
    private int totalSentToday = 0; // Đếm số bài viết đã gửi hôm nay


    @Override
    public void onUpdateReceived(Update update) {
      if (update.hasMessage() && update.getMessage().hasText()) {
        String chatId = update.getMessage().getChatId().toString();
        String messageText = update.getMessage().getText();

        if (messageText.startsWith("/n")) {
          // Xử lý lệnh /n
          int limit = 5; // Mặc định lấy 5 bài viết
          int option = 1; // Mặc định là BASE_URL = "https://vnexpress.net/"
          String[] parts = messageText.split(" ");

          if (parts.length > 1) {
            try {
              option = Integer.parseInt(parts[1]); // Lấy danh mục từ lệnh
            } catch (NumberFormatException e) {
              sendMessage(chatId, "Vui lòng nhập option hợp lệ (ví dụ: /n 1).");
              return;
            }
          }

          if (parts.length > 2) {
            try {
              limit = Integer.parseInt(parts[2]); // Lấy số lượng bài viết từ lệnh
            } catch (NumberFormatException e) {
              sendMessage(chatId, "Vui lòng nhập số lượng bài viết hợp lệ (ví dụ: /n 1 5).");
              return;
            }
          }

          if (limit <= 0 || limit > 50) {
            sendMessage(chatId, "Vui lòng nhập số lượng bài viết từ 1 đến 50.");
            return;
          }

          // Lấy BASE_URL theo option
          String selectedUrl = getBaseUrl(option);
          if (selectedUrl == null) {
            sendMessage(chatId, "Vui lòng nhập option từ 1 đến 11 (ví dụ: /n 1 5).");
            return;
          }

          // Gọi hàm lấy tin tức và gửi về chat
          List<String> newsList = getNews(selectedUrl, limit);
          if (newsList.isEmpty() || newsList.get(0).contains("Không thể lấy dữ liệu")) {
            sendMessage(chatId, "Hiện không thể lấy tin tức từ VNExpress. Vui lòng thử lại sau.");
          } else {
            for (String news : newsList) {
              sendMessage(chatId, news);
            }
          }
        } else if (messageText.equals("/h")) {
          sendMessage(chatId, "Chào mừng bạn đến với bot VNExpress!\n"
                              + "Dùng lệnh /n <option> <số lượng> để đọc báo theo danh mục:\n"
                              + "1: Trang chủ\n"
                              + "2: Thể thao\n"
                              + "3: Góc nhìn\n"
                              + "4: Bất động sản\n"
                              + "5: Giáo dục\n"
                              + "6: Sức khỏe\n"
                              + "7: Công nghệ (AI)\n"
                              + "8: Ý kiến.");
        } else {
          sendMessage(chatId, "Lệnh không hợp lệ. Dùng /n <option> <số lượng> để đọc báo.");
        }
      }
    }

    // Hàm lấy BASE_URL theo danh mục
    private String getBaseUrl(int option) {
      switch (option) {
        case 1:
          return "https://vnexpress.net/";
        case 2:
          return "https://vnexpress.net/the-thao";
        case 3:
          return "https://vnexpress.net/goc-nhin";
        case 4:
          return "https://vnexpress.net/bat-dong-san";
        case 5:
          return "https://vnexpress.net/giao-duc";
        case 6:
          return "https://vnexpress.net/suc-khoe";
        case 7:
          return "https://vnexpress.net/cong-nghe/ai";
        case 8:
          return "https://vnexpress.net/y-kien";
        default:
          return null;
      }
    }

    // Hàm lấy tin tức từ danh mục được chọn
    private List<String> getNews(String baseUrl, int limit) {
      List<String> newsList = new ArrayList<>();
      try {
        Document doc = Jsoup.connect(baseUrl).get();
        Elements articles = doc.select("article.item-news");
        for (int i = 0; i < Math.min(limit, articles.size()); i++) {
          Element article = articles.get(i);
          String title = article.select("h3.title-news > a").text();
          String link = article.select("h3.title-news > a").attr("href");
          String description = article.select("p.description > a").text();

          newsList.add(String.format("Tiêu đề: %s\nMô tả: %s\nLink: %s", title, description, link));
        }
      } catch (IOException e) {
        newsList.add("Không thể lấy dữ liệu từ VNExpress. Vui lòng thử lại sau.");
        e.printStackTrace();
      }
      return newsList;
    }


    @Override
    public String getBotUsername() {
      return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
      return BOT_TOKEN;
    }

    // Hàm gửi tin nhắn
    private void sendMessage(String chatId, String text) {
      SendMessage message = new SendMessage();
      message.setChatId(chatId);
      message.setText(text);
      try {
        execute(message);
      } catch (TelegramApiException e) {
        e.printStackTrace();
      }
    }

    // Hàm gửi tin tức mới nhất
    private void sendLatestNews() {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      List<String> newsList = getNews(50); // Lấy 5 bài viết mới nhất
      for (String news : newsList) {
        String currentTime = LocalDateTime.now().format(formatter);
        String message = news +
                         "\nCount: " + (++totalSentToday) +
                         "\nTime Sent: " + currentTime;
        sendMessage(CHAT_ID, message);
        sentArticles.add(news); // Thêm vào danh sách bài viết đã gửi
      }
    }


    // Hàm lấy tin tức từ VNExpress
    private List<String> getNews(int limit) {
      List<String> newsList = new ArrayList<>();
      try {
        Document doc = Jsoup.connect(BASE_URL).get();
        Elements articles = doc.select("article.item-news");
        for (int i = 0; i < Math.min(limit, articles.size()); i++) {
          Element article = articles.get(i);
          String title = article.select("h3.title-news > a").text();
          String link = article.select("h3.title-news > a").attr("href");
          String description = article.select("p.description > a").text();

          newsList.add(String.format("Tiêu đề: %s\nMô tả: %s\nLink: %s", title, description, link));
        }
      } catch (IOException e) {
        newsList.add("Không thể lấy dữ liệu từ VNExpress. Vui lòng thử lại sau.");
        e.printStackTrace();
      }
      return newsList;
    }
  }
}

package iq.stock_checker;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class StockMonitorService {

    private volatile String botToken = "";
    private volatile String chatId = "";
    private volatile String defaultSelector = ".J16SB_42.cl_fff.acttext";
    private volatile String engine = "PLAYWRIGHT"; // "PLAYWRIGHT" or "JSOUP"
    private volatile int checkIntervalMs = 10000;
    private volatile Double latitude = null;
    private volatile Double longitude = null;



    private final List<String> liveLogs = new CopyOnWriteArrayList<>();
    private final List<String> buyNowLogs = new CopyOnWriteArrayList<>();
    
    // Tracks completed checks in the format: URL + "|" + Pincode
    private final List<String> completedChecks = new CopyOnWriteArrayList<>();

    private volatile boolean running = false;
    private ExecutorService executor;

    public List<String> getLiveLogs() { return liveLogs; }
    public List<String> getBuyNowLogs() { return buyNowLogs; }

    public void clearLiveLogs() { liveLogs.clear(); }
    public void clearBuyNowLogs() { buyNowLogs.clear(); }

    public synchronized void addLiveLog(String message) {
        liveLogs.add(message);
        while (liveLogs.size() > 100) {
            liveLogs.remove(0);
        }
    }

    public synchronized void addBuyNowLog(String message) {
        buyNowLogs.add(message);
        while (buyNowLogs.size() > 50) {
            buyNowLogs.remove(0);
        }
    }

    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getDefaultSelector() { return defaultSelector; }
    public void setDefaultSelector(String defaultSelector) { this.defaultSelector = defaultSelector; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public int getCheckIntervalMs() { return checkIntervalMs; }
    public void setCheckIntervalMs(int checkIntervalMs) { this.checkIntervalMs = checkIntervalMs; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }



    public boolean isRunning() { return running; }

    public synchronized void start(List<String> urls) {
        if (running) {
            addLiveLog("⚠️ Already running");
            return;
        }

        running = true;
        liveLogs.clear();
        buyNowLogs.clear();
        completedChecks.clear();
        addLiveLog("✅ Started monitoring with engine: " + engine);


        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> runMonitoringLoop(urls));
    }

    public synchronized void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        addLiveLog("🛑 Monitoring stopped");
    }

    private void runMonitoringLoop(List<String> urls) {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<MonitorTarget> targets = parseTargets(urls);
            int cycle = 1;

            while (running) {
                addLiveLog("🔄 Cycle #" + cycle + " started (Parallel execution)");

                long totalTargetChecks = targets.size();
                
                // Find targets that are not checked yet
                List<MonitorTarget> activeTargets = new java.util.ArrayList<>();
                for (MonitorTarget target : targets) {
                    String checkKey = target.url + "|" + (target.pincode != null ? target.pincode : (target.latitude != null ? (target.latitude + "_" + target.longitude) : "DEFAULT"));
                    if (!completedChecks.contains(checkKey)) {
                        activeTargets.add(target);
                    }
                }

                if (activeTargets.isEmpty()) {
                    addLiveLog("✅ All monitored products completed. Stopping service.");
                    running = false;
                    break;
                }

                // Run active targets in parallel
                java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                for (MonitorTarget target : activeTargets) {
                    futures.add(pool.submit(() -> {
                        if (!running) return;

                        String url = target.url;
                        String selector = target.selector;
                        String pincode = target.pincode;
                        Double rowLat = target.latitude;
                        Double rowLon = target.longitude;
                        String checkKey = url + "|" + (pincode != null ? pincode : (rowLat != null ? (rowLat + "_" + rowLon) : "DEFAULT"));

                        if (pincode != null) {
                            addLiveLog("🔍 Checking: " + url + " (pincode: " + pincode + ")");
                        } else if (rowLat != null && rowLon != null) {
                            addLiveLog("🔍 Checking: " + url + " (GPS: [" + rowLat + ", " + rowLon + "])");
                        } else {
                            addLiveLog("🔍 Checking: " + url);
                        }

                        boolean inStock = false;
                        try {
                            if ("PLAYWRIGHT".equalsIgnoreCase(engine)) {
                                inStock = checkStockPlaywright(url, selector, pincode, rowLat, rowLon);
                            } else {
                                if (pincode != null || rowLat != null) {
                                    addLiveLog("⚠️ Jsoup engine does not support pincode geolocation flow. Skipping pincode/GPS logic.");
                                }
                                inStock = checkStockJsoup(url, selector);
                            }

                            if (inStock) {
                                String pincodeSuffix = pincode != null ? " (Pincode: " + pincode + ")" : "";
                                addLiveLog("🟢 IN STOCK: " + url + pincodeSuffix);
                                addBuyNowLog("🎉 IN STOCK: " + url + pincodeSuffix);
                                sendTelegramMessage("🔔 *Product IN STOCK!*\n\nGrab it now!" + pincodeSuffix + "\n🔗 Link: " + url);
                                completedChecks.add(checkKey);
                                addLiveLog("🛑 Stopped monitoring (Stock Found): " + url + pincodeSuffix);
                            } else {
                                addLiveLog("🔴 OUT OF STOCK: " + url + (pincode != null ? " (Pincode: " + pincode + ")" : ""));
                            }
                        } catch (Exception e) {
                            addLiveLog("❌ ERROR checking " + url + (pincode != null ? " (Pincode: " + pincode + ")" : "") + ": " + e.getMessage());
                        }
                    }));
                }

                // Wait for all futures in this cycle to complete
                for (java.util.concurrent.Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception ignored) {}
                }

                if (completedChecks.size() >= totalTargetChecks && totalTargetChecks > 0) {
                    addLiveLog("✅ All monitored products completed. Stopping service.");
                    running = false;
                    break;
                }

                if (!running) break;
                addLiveLog("😴 Sleeping for " + checkIntervalMs + " ms before next cycle...");
                cycle++;

                long endSleepTime = System.currentTimeMillis() + checkIntervalMs;
                while (running && System.currentTimeMillis() < endSleepTime) {
                    long waitTime = Math.min(100, endSleepTime - System.currentTimeMillis());
                    if (waitTime > 0) {
                        Thread.sleep(waitTime);
                    }
                }
            }
        } catch (InterruptedException e) {
            addLiveLog("ℹ️ Monitoring loop interrupted.");
        } catch (Exception e) {
            addLiveLog("❌ Monitor loop fatal error: " + e.getMessage());
        } finally {
            pool.shutdownNow();
            addLiveLog("🧹 Resources cleaned up.");
        }
    }



    private boolean checkStockJsoup(String url, String selector) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(10000)
                .get();

        boolean found = false;
        if (selector.startsWith("text=")) {
            String targetText = selector.substring(5).replace("\"", "").replace("'", "").trim().toLowerCase();
            for (Element el : doc.select("button, a, input[type=submit], input[type=button], .btn, .button, div")) {
                if (el.text().toLowerCase().contains(targetText)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                found = doc.text().toLowerCase().contains(targetText);
            }
        } else {
            found = doc.selectFirst(selector) != null;
        }

        if (found) {
            Element match = null;
            if (selector.startsWith("text=")) {
                String targetText = selector.substring(5).replace("\"", "").replace("'", "").trim().toLowerCase();
                for (Element el : doc.select("button, a, input[type=submit], input[type=button], .btn, .button, div")) {
                    if (el.text().toLowerCase().contains(targetText)) {
                        match = el;
                        break;
                    }
                }
            } else {
                match = doc.selectFirst(selector);
            }

            if (match != null) {
                String elText = match.text().toLowerCase();
                String parentText = match.parent() != null ? match.parent().text().toLowerCase() : "";
                boolean outOfStock = elText.contains("out of stock")
                        || elText.contains("sold out")
                        || elText.contains("temporarily unavailable")
                        || elText.contains("currently unavailable")
                        || parentText.contains("out of stock")
                        || parentText.contains("sold out");
                if (outOfStock) {
                    found = false;
                }
            }
        }

        return found;
    }

    private boolean checkStockPlaywright(String url, String selector, String pincode, Double latitude, Double longitude) throws Exception {
        try (com.microsoft.playwright.Playwright playwright = com.microsoft.playwright.Playwright.create();
             com.microsoft.playwright.Browser browser = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions()
                     .setHeadless(true)
                     .setChromiumSandbox(false))) {
            
            com.microsoft.playwright.Browser.NewContextOptions options = new com.microsoft.playwright.Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            
            if (latitude != null && longitude != null) {
                options.setGeolocation(latitude, longitude);
                options.setPermissions(List.of("geolocation"));
            } else if (this.latitude != null && this.longitude != null) {
                options.setGeolocation(this.latitude, this.longitude);
                options.setPermissions(List.of("geolocation"));
            }
            
            try (com.microsoft.playwright.BrowserContext context = browser.newContext(options);
                 com.microsoft.playwright.Page page = context.newPage()) {
                
                String finalSelector = selector;
                if (url.contains("blinkit.com")) {
                    if (selector.equals(defaultSelector) || selector.contains("text=ADD") || selector.contains("text=\"ADD\"") || selector.toLowerCase().contains("add")) {
                        finalSelector = "div[class*=\"ProductDesktopBffEnabled__ProductWrapperRightSection\"] >> text=ADD";
                    }
                }

                page.navigate(url);
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                Thread.sleep(3000);

                if (pincode != null && !pincode.isEmpty() && url.contains("blinkit.com")) {
                    try {
                        // 1. Click on rounded delivery drop down
                        String dropdownXpath = "xpath=//*[@id=\"app\"]/div/div/div[1]/header/div[1]/div[2]/div/div[2]/div[1]";
                        com.microsoft.playwright.Locator dropdown = page.locator(dropdownXpath);
                        if (dropdown.count() == 0 || !dropdown.isVisible()) {
                            dropdown = page.locator("xpath=//*[contains(@class, 'LocationBar__Subtitle')]").first();
                        }
                        if (dropdown.count() == 0 || !dropdown.isVisible()) {
                            dropdown = page.locator("xpath=//*[contains(@class, 'LocationBar__Title')]").first();
                        }
                        if (dropdown.count() > 0 && dropdown.isVisible()) {
                            dropdown.click();
                        } else {
                            throw new RuntimeException("Could not find location dropdown trigger element");
                        }
                        Thread.sleep(1500);

                        // 2. Enter pincode in search delivery location text box
                        String inputXpath = "xpath=//*[@id=\"app\"]/div/div/div[1]/header/div[2]/div[2]/div/div/div[1]/div/div/div/div[2]/div[2]/div/div/div/input";
                        com.microsoft.playwright.Locator inputBox = page.locator(inputXpath);
                        if (inputBox.count() == 0 || !inputBox.isVisible()) {
                            inputBox = page.locator("input[name='select-locality']");
                        }
                        if (inputBox.count() == 0 || !inputBox.isVisible()) {
                            inputBox = page.locator("input[placeholder*='location']");
                        }
                        if (inputBox.count() > 0 && inputBox.isVisible()) {
                            inputBox.fill("");
                            inputBox.type(pincode, new com.microsoft.playwright.Locator.TypeOptions().setDelay(100));
                        } else {
                            throw new RuntimeException("Could not find search delivery location input element");
                        }
                        Thread.sleep(2000);

                        // 3. Select first option in the list
                        String optionXpath = "xpath=//*[contains(@class, 'LocationSearchList__LocationDetailContainer')]";
                        com.microsoft.playwright.Locator firstOption = page.locator(optionXpath).first();
                        firstOption.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(5000));
                        if (firstOption.count() > 0 && firstOption.isVisible()) {
                            firstOption.click();
                        } else {
                            throw new RuntimeException("Could not find location suggestion for pincode " + pincode);
                        }
                        Thread.sleep(3000); // Wait for the page/stock state to update
                    } catch (Exception e) {
                        addLiveLog("⚠️ Warning: Failed to set Blinkit pincode " + pincode + ": " + e.getMessage());
                    }
                }

                return checkStockPlaywrightNoNav(page, finalSelector);
            }
        }
    }

    private boolean checkStockPlaywrightNoNav(com.microsoft.playwright.Page page, String selector) throws Exception {
        boolean found = false;
        if (selector.startsWith("text=")) {
            com.microsoft.playwright.Locator locator = page.locator(selector);
            int count = locator.count();
            for (int i = 0; i < count; i++) {
                if (locator.nth(i).isVisible()) {
                    found = true;
                    break;
                }
            }
        } else {
            com.microsoft.playwright.Locator locator = page.locator(selector).first();
            found = locator.count() > 0 && locator.isVisible();
        }

        if (found) {
            com.microsoft.playwright.Locator matchedEl = page.locator(selector).first();
            String elText = matchedEl.innerText().toLowerCase();
            String parentText = "";
            try {
                parentText = page.locator(selector).first().locator("xpath=..").innerText().toLowerCase();
            } catch (Exception ignored) {}

            boolean outOfStock = elText.contains("out of stock")
                    || elText.contains("sold out")
                    || elText.contains("temporarily unavailable")
                    || elText.contains("currently unavailable")
                    || parentText.contains("out of stock")
                    || parentText.contains("sold out")
                    || parentText.contains("temporarily unavailable");
            if (outOfStock) {
                found = false;
            }
        }

        return found;
    }
    private void sendTelegramMessage(String message) {
        if (botToken == null || botToken.trim().isEmpty() ||
                chatId == null || chatId.trim().isEmpty() ||
                botToken.contains("YOUR_TELEGRAM_BOT_TOKEN")) {
            addLiveLog("⚠️ Telegram alert skipped (not configured)");
            return;
        }

        try {
            String urlString = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            String jsonPayload = "{"
                    + "\"chat_id\": \"" + chatId + "\","
                    + "\"text\": \"" + escapeJson(message) + "\","
                    + "\"parse_mode\": \"Markdown\""
                    + "}";

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(urlString))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                addLiveLog("❌ Failed to send Telegram alert: Status " + response.statusCode());
            }
        } catch (Exception e) {
            addLiveLog("❌ Telegram error: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    private static class MonitorTarget {
        String url;
        String selector;
        String pincode;
        Double latitude;
        Double longitude;

        public MonitorTarget(String url, String selector, String pincode, Double latitude, Double longitude) {
            this.url = url;
            this.selector = selector;
            this.pincode = pincode;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private List<MonitorTarget> parseTargets(List<String> configs) {
        List<MonitorTarget> targets = new java.util.ArrayList<>();
        for (String config : configs) {
            if (config.trim().isEmpty()) continue;

            String url = config.trim();
            String selector = defaultSelector;
            String pincode = null;
            Double lat = null;
            Double lon = null;

            if (config.contains("|")) {
                String[] parts = config.split("\\|");
                url = parts[0].trim();
                if (parts.length >= 5) {
                    selector = !parts[1].trim().isEmpty() ? parts[1].trim() : defaultSelector;
                    pincode = !parts[2].trim().isEmpty() ? parts[2].trim() : null;
                    lat = !parts[3].trim().isEmpty() ? Double.parseDouble(parts[3].trim()) : null;
                    lon = !parts[4].trim().isEmpty() ? Double.parseDouble(parts[4].trim()) : null;
                } else if (parts.length == 3) {
                    selector = !parts[1].trim().isEmpty() ? parts[1].trim() : defaultSelector;
                    pincode = !parts[2].trim().isEmpty() ? parts[2].trim() : null;
                } else if (parts.length == 2) {
                    String secondPart = parts[1].trim();
                    if (secondPart.matches("[\\d\\s,;]+")) {
                        pincode = secondPart;
                    } else {
                        selector = secondPart;
                    }
                }
            }

            if (pincode != null && (pincode.contains(",") || pincode.contains(";"))) {
                String delimiter = pincode.contains(",") ? "," : ";";
                String[] codes = pincode.split(delimiter);
                for (String code : codes) {
                    String cleanCode = code.trim();
                    if (!cleanCode.isEmpty()) {
                        targets.add(new MonitorTarget(url, selector, cleanCode, lat, lon));
                    }
                }
            } else {
                targets.add(new MonitorTarget(url, selector, pincode != null ? pincode.trim() : null, lat, lon));
            }
        }
        return targets;
    }
}

package Blinki.Stock;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class test {

    private static final String CONFIG_FILE = "config.properties";
    private static final String URLS_FILE = "urls.txt";
    private static final String SESSION_DIR = "C:/playwright/blinkit-session";

    public static void main(String[] args) {
        System.out.println("=============================================================");
        System.out.println("          BLINKIT 24/7 STOCK MONITORING SERVICE              ");
        System.out.println("=============================================================");

        // Load configuration
        Properties config = loadConfig();
        String botToken = config.getProperty("telegram.bot_token", "").trim();
        String chatId = config.getProperty("telegram.chat_id", "").trim();
        boolean headless = Boolean.parseBoolean(config.getProperty("browser.headless", "false").trim());
        
        int intervalMinutes = 5;
        try {
            intervalMinutes = Integer.parseInt(config.getProperty("check.interval_minutes", "5").trim());
        } catch (NumberFormatException e) {
            System.err.println("⚠️ Invalid value for check.interval_minutes in config. properties. Defaulting to 5 minutes.");
        }

        // Map to keep track of the last known stock state of each product
        // URL -> isPreviouslyInStock
        Map<String, Boolean> lastStockState = new HashMap<>();

        // Create Playwright browser instance
        try (Playwright playwright = Playwright.create()) {
            
            System.out.println("Initializing Playwright persistent browser...");
            BrowserContext context = playwright.chromium().launchPersistentContext(
                    Paths.get(SESSION_DIR),
                    new BrowserType.LaunchPersistentContextOptions()
                            .setHeadless(headless)
            );

            Page page = context.pages().get(0);

            // Initial startup login/address setup phase if we are in headful (visible) mode
            if (!headless) {
                System.out.println("\n=============================================================");
                System.out.println("                INITIAL LOGIN & ADDRESS SETUP                ");
                System.out.println("=============================================================");
                System.out.println("Opening Blinkit Home Page...");
                page.navigate("https://blinkit.com");
                
                String phoneNumber = config.getProperty("user.phone_number", "").trim();
                if (!phoneNumber.isEmpty()) {
                    try {
                        System.out.println("\nAttempting semi-automated login using phone number: " + phoneNumber);
                        sleep(4000);
                        
                        // Locate Login button
                        Locator loginBtn = page.locator("text=/^Login$/i").first();
                        if (loginBtn.isVisible()) {
                            loginBtn.click();
                            System.out.println("Clicked Login button. Waiting for dialog...");
                            sleep(3000);
                            
                            // Find the phone input field
                            Locator phoneInput = page.locator("input[placeholder*='number'], input[type='tel'], input[placeholder*='Phone'], input[placeholder*='mobile']").first();
                            if (phoneInput.isVisible()) {
                                phoneInput.fill(phoneNumber);
                                sleep(1000);
                                
                                // Click Continue / Send OTP
                                Locator continueBtn = page.locator("button:has-text('Continue'), button:has-text('Next'), button:has-text('Get OTP'), button:has-text('Send OTP')").first();
                                if (continueBtn.isVisible()) {
                                    continueBtn.click();
                                    System.out.println("✅ Phone number entered. OTP requested!");
                                    
                                    // Prompt in terminal
                                    System.out.println("\n-------------------------------------------------------------");
                                    System.out.print("📱 ENTER OTP: An OTP has been sent to " + phoneNumber + ".\nType the OTP here in the console and press Enter: ");
                                    System.out.println("\n-------------------------------------------------------------");
                                    
                                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                                    String otp = br.readLine();
                                    
                                    if (otp != null && !otp.trim().isEmpty()) {
                                        otp = otp.trim();
                                        // Look for OTP input field
                                        Locator otpInput = page.locator("input[placeholder*='OTP'], input[placeholder*='code'], input[type='number']").first();
                                        if (otpInput.isVisible()) {
                                            otpInput.fill(otp);
                                            sleep(1000);
                                            
                                            // Click login/verify button
                                            Locator verifyBtn = page.locator("button:has-text('Submit'), button:has-text('Verify'), button:has-text('Login'), button:has-text('Done')").first();
                                            if (verifyBtn.isVisible()) {
                                                verifyBtn.click();
                                                System.out.println("✅ OTP submitted! Waiting for login to complete...");
                                                sleep(5000);
                                            }
                                        } else {
                                            // Try digit-by-digit multi-input fields
                                            Locator inputs = page.locator("input[type='text'], input[type='tel']");
                                            if (inputs.count() >= otp.length()) {
                                                for (int i = 0; i < otp.length(); i++) {
                                                    inputs.nth(i).fill(String.valueOf(otp.charAt(i)));
                                                }
                                                sleep(1000);
                                                Locator verifyBtn = page.locator("button:has-text('Submit'), button:has-text('Verify'), button:has-text('Login'), button:has-text('Done'), button:has-text('Continue')").first();
                                                if (verifyBtn.isVisible()) {
                                                    verifyBtn.click();
                                                    System.out.println("✅ OTP submitted! Waiting for login to complete...");
                                                    sleep(5000);
                                                }
                                            } else {
                                                System.out.println("⚠️ Could not locate OTP inputs automatically. Please complete login inside the browser window.");
                                            }
                                        }
                                    }
                                } else {
                                    System.out.println("⚠️ Could not find 'Continue' or 'Get OTP' button.");
                                }
                            } else {
                                System.out.println("⚠️ Could not locate phone number input field.");
                            }
                        } else {
                            System.out.println("ℹ️ 'Login' button not found. You are likely already logged in!");
                        }
                    } catch (Exception e) {
                        System.err.println("⚠️ Automated login attempt encountered an issue: " + e.getMessage());
                        System.out.println("Falling back to manual login...");
                    }
                }

                System.out.println("\n👉 ACTIONS REQUIRED:");
                System.out.println("1. Ensure you are logged in inside the opened browser window.");
                System.out.println("2. Select your delivery address/location in the browser.");
                System.out.println("3. Ensure the location dashboard has fully loaded.");
                System.out.println("\nThe service will pause for 45 seconds to let you finalize this...");
                System.out.println("=============================================================\n");
                
                sleep(45000);
                System.out.println("Initial setup phase completed. Starting background monitoring loop...\n");
            } else {
                System.out.println("Starting in HEADLESS mode. Using saved session profile from: " + SESSION_DIR);
            }

            // Main 24/7 background tracking loop
            int loopCounter = 1;
            while (true) {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                System.out.println("-------------------------------------------------------------");
                System.out.println("[" + timestamp + "] STARTING RUN #" + loopCounter);
                System.out.println("-------------------------------------------------------------");

                // Load URLs from file (allows adding/removing URLs on-the-fly without restarting)
                List<String> productUrls = loadUrls();
                if (productUrls.isEmpty()) {
                    System.out.println("⚠️ No URLs found in " + URLS_FILE + ". Add some product links to monitor!");
                } else {
                    System.out.println("Monitoring " + productUrls.size() + " product(s):");
                    for (String url : productUrls) {
                        try {
                            System.out.println("\nChecking: " + url);
                            
                            // Navigate to the product details page
                            page.navigate(url);
                            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                            
                            // Let the dynamic React contents settle
                            sleep(3000);

                            // Locate the main product details container specifically to ignore recommended items at the bottom
                            Locator productSection = page.locator("div[class*='ProductWrapperRightSection']").first();
                            boolean hasSection = productSection.count() > 0;
                            
                            // Locate any ADD buttons inside the main product details section
                            boolean hasAddButton = false;
                            try {
                                Locator sectionToSearch = hasSection ? productSection : page.locator("body");
                                Locator addButtons = sectionToSearch.locator("text=/^ADD$/i");
                                int count = addButtons.count();
                                for (int i = 0; i < count; i++) {
                                    if (addButtons.nth(i).isVisible()) {
                                        hasAddButton = true;
                                        break;
                                    }
                                }
                                
                                // Also search for generic "Add to Cart" or "Add" if "ADD" isn't found
                                if (!hasAddButton) {
                                    Locator genericAdd = sectionToSearch.locator("text=/^Add to Cart$/i");
                                    if (genericAdd.count() > 0 && genericAdd.first().isVisible()) {
                                        hasAddButton = true;
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("⚠️ Error checking Add button locator: " + e.getMessage());
                            }

                            // Check common out of stock indicators inside the main product details section
                            boolean containsOutOfStockText = false;
                            try {
                                String searchAreaText = hasSection ? productSection.innerText() : page.innerText("body");
                                String searchAreaTextLower = searchAreaText.toLowerCase();
                                containsOutOfStockText = searchAreaTextLower.contains("out of stock")
                                        || searchAreaTextLower.contains("outofstock")
                                        || searchAreaTextLower.contains("unavailable in your area")
                                        || searchAreaTextLower.contains("temporarily out of stock")
                                        || searchAreaTextLower.contains("get notified");
                            } catch (Exception e) {
                                System.err.println("⚠️ Error reading search area text: " + e.getMessage());
                            }

                            // Resolve stock status
                            boolean isCurrentlyInStock = hasAddButton && !containsOutOfStockText;

                            System.out.println(" -> Scoped Main Section Found: " + hasSection);
                            System.out.println(" -> Has Scoped 'ADD' Button: " + hasAddButton);
                            System.out.println(" -> Out-Of-Stock Indicators Present: " + containsOutOfStockText);
                            System.out.println(" -> Resolved Status: " + (isCurrentlyInStock ? "🟢 IN STOCK" : "🔴 OUT OF STOCK"));

                            // Handle state transitions and alerts
                            Boolean previousState = lastStockState.get(url);
                            if (previousState == null) {
                                // First check
                                lastStockState.put(url, isCurrentlyInStock);
                                if (isCurrentlyInStock) {
                                    System.out.println("📢 [INITIAL STATE] Product is IN STOCK. Sending Telegram alert!");
                                    sendTelegramAlert(botToken, chatId, "🔔 *[INITIAL CHECK] Product IN STOCK!*\n\nProduct is available now!\n🔗 [Blinkit Link](" + url + ")", url);
                                } else {
                                    System.out.println("📢 [INITIAL STATE] Product is OUT OF STOCK. Will monitor for restock...");
                                }
                            } else if (isCurrentlyInStock && !previousState) {
                                // Restock transition (Out of Stock -> In Stock)
                                lastStockState.put(url, true);
                                System.out.println("🚨 [RESTOCK ALERT] Product is BACK IN STOCK! Sending Telegram notification!");
                                sendTelegramAlert(botToken, chatId, "🚀 *[RESTOCK ALERT] Product is BACK IN STOCK!*\n\nGrab it before it sells out!\n🔗 [Blinkit Link](" + url + ")", url);
                            } else if (!isCurrentlyInStock && previousState) {
                                // Sold out transition (In Stock -> Out of Stock)
                                lastStockState.put(url, false);
                                System.out.println("📉 [STOCK UPDATE] Product went OUT OF STOCK.");
                                sendTelegramAlert(botToken, chatId, "📉 *[STOCK UPDATE] Product went OUT OF STOCK!*\n\nMonitoring for the next restock...\n🔗 [Blinkit Link](" + url + ")", url);
                            } else {
                                // No change in stock state
                                System.out.println("ℹ️ No state change. Stock is still: " + (isCurrentlyInStock ? "🟢 IN STOCK" : "🔴 OUT OF STOCK"));
                            }

                        } catch (Exception e) {
                            System.err.println("❌ Error checking product URL: " + url);
                            System.err.println("Details: " + e.getMessage());
                        }
                    }
                }

                System.out.println("\nRun #" + loopCounter + " completed. Sleeping for " + intervalMinutes + " minute(s) before next check...");
                loopCounter++;
                sleep(intervalMinutes * 60 * 1000);
            }

        } catch (Exception e) {
            System.err.println("💥 Fatal error in Playwright browser service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Loads settings from the config.properties file.
     */
    private static Properties loadConfig() {
        Properties config = new Properties();
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStream input = new FileInputStream(file)) {
                config.load(input);
                System.out.println("Loaded config successfully from " + CONFIG_FILE);
            } catch (IOException e) {
                System.err.println("⚠️ Could not load " + CONFIG_FILE + ". Using defaults. Error: " + e.getMessage());
            }
        } else {
            System.out.println("⚠️ " + CONFIG_FILE + " not found. Run will use defaults.");
        }
        return config;
    }

    /**
     * Reads URLs from urls.txt, skipping comments and empty lines.
     */
    private static List<String> loadUrls() {
        List<String> urls = new ArrayList<>();
        File file = new File(URLS_FILE);
        if (!file.exists()) {
            System.out.println("⚠️ " + URLS_FILE + " does not exist. Creating empty file.");
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("❌ Could not create " + URLS_FILE + ": " + e.getMessage());
            }
            return urls;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    urls.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Error reading " + URLS_FILE + ": " + e.getMessage());
        }
        return urls;
    }

    /**
     * Helper sleep method that swallows InterruptedException.
     */
    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sends an HTTP POST alert to Telegram Bot API.
     */
    private static void sendTelegramAlert(String botToken, String chatId, String message, String url) {
        if (botToken == null || botToken.isEmpty() || botToken.contains("YOUR_TELEGRAM_BOT_TOKEN") ||
            chatId == null || chatId.isEmpty() || chatId.contains("YOUR_TELEGRAM_CHAT_ID")) {
            System.out.println("⚠️ Telegram Bot Token or Chat ID is not configured in config.properties. Skipping Telegram alert.");
            return;
        }

        try {
            String telegramUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            
            // Construct JSON payload manually to avoid extra dependencies
            String jsonPayload = "{"
                    + "\"chat_id\": \"" + chatId + "\","
                    + "\"text\": \"" + escapeJson(message) + "\","
                    + "\"parse_mode\": \"Markdown\""
                    + "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(telegramUrl))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("✅ Telegram alert sent successfully!");
            } else {
                System.err.println("❌ Failed to send Telegram alert. Status code: " + response.statusCode());
                System.err.println("Response: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("❌ Exception while sending Telegram message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Escapes critical characters for standard JSON string payload.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
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
}
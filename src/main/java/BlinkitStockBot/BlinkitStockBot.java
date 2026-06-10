package BlinkitStockBot;import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class BlinkitStockBot {

    // =========================================
    // TELEGRAM CONFIG
    // =========================================

    private static final String BOT_TOKEN =
            "8358320475:AAGlhNyENBwLzZ_-fEfqph93vhrXPRmp9-U";

    private static final String CHAT_ID =
            "1456153642";

    // =========================================
    // SETTINGS
    // =========================================

    private static final int LOOP_DELAY_SECONDS = 1;

    // Prevent repeated alerts
    private static final Map<String, Boolean>
            stockState = new HashMap<>();

    public static void main(String[] args) {

        ChromeOptions options =
                new ChromeOptions();

        // SAVE LOGIN SESSION
        options.addArguments(
                "user-data-dir=C:/selenium/blinkit-profile"
        );

        options.addArguments("--start-maximized");

        WebDriver driver =
                new ChromeDriver(options);

        try {

            // OPEN BLINKIT
            driver.get("https://blinkit.com/");

            System.out.println(
                    "================================"
            );

            System.out.println(
                    "LOGIN MANUALLY FIRST TIME"
            );

            System.out.println(
                    "SELECT ADDRESS FIRST TIME"
            );

            System.out.println(
                    "================================"
            );

            Thread.sleep(30000);

            // SET LOCATION
            setupLocation(driver);

            // INFINITE LOOP
            while (true) {

                List<String> productLinks =
                        loadLinks();

                for (String productUrl : productLinks) {

                    try {

                        System.out.println(
                                "================================"
                        );

                        System.out.println(
                                "Checking Product:"
                        );

                        System.out.println(productUrl);

                        // OPEN PRODUCT
                        driver.get(productUrl);

                        Thread.sleep(5000);

                        boolean inStock =
                                isProductInStock(driver);

                        if (inStock) {

                            Boolean previous =
                                    stockState.get(productUrl);

                            // SEND ONLY ONCE
                            if (previous == null || !previous) {

                                System.out.println(
                                        "PRODUCT IN STOCK"
                                );

                                String message =
                                        "🔥 PRODUCT IN STOCK 🔥\n\n"
                                                + productUrl
                                                + "\n\nTime: "
                                                + LocalDateTime.now();

                                sendTelegramAlert(message);
                            }

                            stockState.put(
                                    productUrl,
                                    true
                            );

                        } else {

                            System.out.println(
                                    "NOT IN STOCK"
                            );

                            stockState.put(
                                    productUrl,
                                    false
                            );
                        }

                        Thread.sleep(3000);

                    } catch (Exception e) {

                        System.out.println(
                                "ERROR CHECKING PRODUCT"
                        );

                        e.printStackTrace();
                    }
                }

                System.out.println(
                        "================================"
                );

                System.out.println(
                        "NEXT LOOP STARTING..."
                );

                System.out.println(
                        "================================"
                );

                Thread.sleep(
                        LOOP_DELAY_SECONDS * 1000L
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            // driver.quit();
        }
    }

    // =========================================
    // SET LOCATION
    // =========================================

    private static void setupLocation(
            WebDriver driver
    ) {

        try {

            Thread.sleep(5000);

            // CLICK DELIVERY BUTTON
            List<WebElement> locationButtons =
                    driver.findElements(
                            By.xpath(
                                    "//*[contains(text(),'Delivery')]"
                            )
                    );

            if (!locationButtons.isEmpty()) {

                locationButtons.get(0).click();

                Thread.sleep(3000);
            }

            // ENTER PINCODE
            List<WebElement> inputs =
                    driver.findElements(
                            By.tagName("input")
                    );

            for (WebElement input : inputs) {

                try {

                    input.clear();

                    input.sendKeys("500050");

                    Thread.sleep(3000);

                    break;

                } catch (Exception ignored) {
                }
            }

            Thread.sleep(5000);

            // SELECT TELANGANA ADDRESS
            List<WebElement> telanganaAddresses =
                    driver.findElements(
                            By.xpath(
                                    "//*[contains(text(),'Telangana')]"
                            )
                    );

            if (!telanganaAddresses.isEmpty()) {

                telanganaAddresses.get(0).click();

                System.out.println(
                        "TELANGANA ADDRESS SELECTED"
                );
            }

            Thread.sleep(5000);

        } catch (Exception e) {

            System.out.println(
                    "LOCATION SETUP FAILED"
            );

            e.printStackTrace();
        }
    }

    // =========================================
    // CHECK STOCK
    // =========================================

    private static boolean isProductInStock(
            WebDriver driver
    ) {

        try {

            // OUT OF STOCK
            List<WebElement> outOfStock =
                    driver.findElements(
                            By.xpath(
                                    "//*[contains(text(),'Out of stock')]"
                            )
                    );

            if (!outOfStock.isEmpty()) {

                System.out.println(
                        "OUT OF STOCK DETECTED"
                );

                return false;
            }

            // COMING SOON
            List<WebElement> comingSoon =
                    driver.findElements(
                            By.xpath(
                                    "//*[contains(text(),'Coming Soon')]"
                            )
                    );

            if (!comingSoon.isEmpty()) {

                System.out.println(
                        "COMING SOON DETECTED"
                );

                return false;
            }

            // ADD BUTTON
            List<WebElement> addButtons =
                    driver.findElements(
                            By.xpath(
                                    "//button[contains(.,'Add')]"
                            )
                    );

            if (!addButtons.isEmpty()) {

                System.out.println(
                        "ADD BUTTON FOUND"
                );

                return true;
            }

            // ADD BUTTON CAPS
            List<WebElement> addButtonsCaps =
                    driver.findElements(
                            By.xpath(
                                    "//button[contains(.,'ADD')]"
                            )
                    );

            if (!addButtonsCaps.isEmpty()) {

                System.out.println(
                        "ADD BUTTON FOUND"
                );

                return true;
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return false;
    }

    // =========================================
    // LOAD LINKS
    // =========================================

    private static List<String> loadLinks() {

        List<String> links =
                new ArrayList<>();

        try (
                BufferedReader br =
                        new BufferedReader(
                                new FileReader(
                                        "products.txt"
                                )
                        )
        ) {

            String line;

            while ((line = br.readLine()) != null) {

                line = line.trim();

                // IGNORE EMPTY LINES
                if (line.isEmpty()) {
                    continue;
                }

                // IGNORE COMMENTS
                if (line.startsWith("#")) {
                    continue;
                }

                // ONLY VALID URLS
                if (line.startsWith("http")) {

                    links.add(line);
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return links;
    }

    // =========================================
    // TELEGRAM ALERT
    // =========================================

    private static void sendTelegramAlert(
            String message
    ) {

        try {

            String encodedMessage =
                    URLEncoder.encode(
                            message,
                            StandardCharsets.UTF_8
                    );

            String telegramUrl =
                    "https://api.telegram.org/bot"
                            + BOT_TOKEN
                            + "/sendMessage?chat_id="
                            + CHAT_ID
                            + "&text="
                            + encodedMessage;

            URL url =
                    new URL(telegramUrl);

            HttpURLConnection connection =
                    (HttpURLConnection)
                            url.openConnection();

            connection.setRequestMethod("GET");

            int responseCode =
                    connection.getResponseCode();

            System.out.println(
                    "TELEGRAM RESPONSE: "
                            + responseCode
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}
package iq.stock_checker;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DebugBlinkit {
    public static void main(String[] args) {
        System.out.println("Analyzing XPath of the input element...");
        try {
            if (Files.exists(Paths.get("blinkit_after_click_pretty.html"))) {
                String html = Files.readString(Paths.get("blinkit_after_click_pretty.html"));
                Document doc = Jsoup.parse(html);
                Element input = doc.selectFirst("input[name=select-locality]");
                if (input == null) {
                    input = doc.selectFirst("input[placeholder*=location]");
                }
                if (input != null) {
                    System.out.println("Found input element!");
                    System.out.println("Outer HTML: " + input.outerHtml());
                    System.out.println("Computed XPath: " + getXPath(input));
                } else {
                    System.out.println("ERROR: Input element not found in blinkit_after_click_pretty.html");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("\nAnalyzing location button in blinkit_page_source_pretty.html...");
        try {
            if (Files.exists(Paths.get("blinkit_page_source_pretty.html"))) {
                String html = Files.readString(Paths.get("blinkit_page_source_pretty.html"));
                Document doc = Jsoup.parse(html);
                
                // Let's search for divs/buttons that are likely the location trigger
                // We'll search for elements containing text like "Delivery in" or "Detect my location" or similar
                System.out.println("Searching for elements with text or class related to location...");
                for (Element el : doc.getAllElements()) {
                    String className = el.className();
                    String text = el.ownText().trim();
                    if (className.contains("Location") || className.contains("Header") || text.toLowerCase().contains("delivery in") || text.toLowerCase().contains("minutes")) {
                        // Let's print the element if it's a leaf or has a clean class
                        if (el.children().isEmpty() || text.length() > 0) {
                            System.out.println("Potential Match: <" + el.tagName() + " class=\"" + className + "\"> Text: " + text);
                            System.out.println("  XPath: " + getXPath(el));
                        }
                    }
                }
            } else {
                System.out.println("blinkit_page_source_pretty.html not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getXPath(Element element) {
        StringBuilder xpath = new StringBuilder();
        Element current = element;
        while (current != null && !current.tagName().equals("#root")) {
            String tagName = current.tagName();
            Element parent = current.parent();
            if (parent != null) {
                int index = 1;
                for (Element sibling : parent.children()) {
                    if (sibling == current) {
                        break;
                    }
                    if (sibling.tagName().equals(tagName)) {
                        index++;
                    }
                }
                xpath.insert(0, "/" + tagName + "[" + index + "]");
            } else {
                xpath.insert(0, "/" + tagName);
            }
            current = parent;
        }
        return xpath.toString();
    }
}

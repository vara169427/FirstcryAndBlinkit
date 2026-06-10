package Blinki.Stock;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class test2 {
    public static void main(String[] args) {

    	ChromeOptions options = new ChromeOptions();

    	options.addArguments(
    	    "--user-data-dir=C:\\Users\\91709\\AppData\\Local\\Google\\Chrome\\User Data"
    	);

    	options.addArguments("--profile-directory=Profile 11");

        WebDriver driver = new ChromeDriver(options);

        driver.get("https://blinkit.com");
    }
}
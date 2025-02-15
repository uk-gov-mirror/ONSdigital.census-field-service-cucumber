package uk.gov.ons.ctp.integration.fieldsvccucumber.selenium;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.ctp.common.util.WebDriverType;
import uk.gov.ons.ctp.common.util.WebDriverUtils;

@Component
public class WebDriverFactory {

  @Value("${webdriver.type}")
  private String driverTypeName;

  @Value("${webdriver.logging_level}")
  private String driverLoggingLevel;

  @Value("${webdriver.headless}")
  private Boolean headless;

  private static final Logger log = LoggerFactory.getLogger(WebDriverFactory.class);

  private static final int DRIVER_POOL_SIZE = 2;
  private static final int MAX_CLOSE_WAIT_ITERATIONS = 20;

  private int numCores = 1;
  private boolean shutdown;

  private final BlockingQueue<WebDriver> cachedWebDrivers =
      new ArrayBlockingQueue<>(DRIVER_POOL_SIZE);

  private final AtomicInteger driversQuitting = new AtomicInteger();

  public void closeWebDriver(WebDriver driver) {
    driversQuitting.incrementAndGet();
    Thread t =
        new Thread(
            () -> {
              driver.quit();
              driversQuitting.decrementAndGet();
            });
    t.start();
  }

  public WebDriver getWebDriver() throws InterruptedException {
    return numCores > 1 ? cachedWebDrivers.take() : createWebDriver();
  }

  private WebDriver createWebDriver() {
    final WebDriver webDriver =
        WebDriverUtils.getWebDriver(
            WebDriverType.valueOf(driverTypeName), headless, driverLoggingLevel);
    webDriver.manage().timeouts().implicitlyWait(1, TimeUnit.MICROSECONDS);
    return webDriver;
  }

  private void fillPoolOfWebDrivers() {
    while (!shutdown) {
      try {
        WebDriver driver = createWebDriver();
        if (shutdown) {
          log.info("Adding freshly created driver to be closed");
          closeWebDriver(driver);
        } else {
          cachedWebDrivers.put(driver);
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @PostConstruct
  public void startup() {
    if (headless) {
      numCores = Runtime.getRuntime().availableProcessors();
    }
    log.info("Running cucumber with {} processor cores", numCores);
    if (numCores > 1) {
      Thread cacheFillerThread = new Thread(this::fillPoolOfWebDrivers);
      cacheFillerThread.start();
    }
  }

  @PreDestroy
  public void shutdown() {
    log.info("Test Shutdown - closing {} WebDrivers", cachedWebDrivers.size());
    shutdown = true;
    WebDriver driver = null;
    do {
      driver = cachedWebDrivers.poll();
      if (driver != null) {
        log.info("Shutting down pooled Selenium webDriver");
        closeWebDriver(driver);
      }
    } while (driver != null);

    waitForAllDriversToQuit();
  }

  private void waitForAllDriversToQuit() {
    int numClosing = driversQuitting.get();
    for (int i = 0; i < MAX_CLOSE_WAIT_ITERATIONS && numClosing != 0; i++) {
      log.info("Waiting for {} drivers to quit", numClosing);
      try {
        Thread.sleep(500);
        numClosing = driversQuitting.get();
      } catch (InterruptedException e) {
      }
    }
  }
}

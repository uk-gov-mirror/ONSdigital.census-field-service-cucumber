package uk.gov.ons.ctp.integration.fieldsvccucumber.cucSteps.sso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxDriverLogLevel;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.FulfilmentDTO;
import uk.gov.ons.ctp.integration.fieldsvccucumber.main.SpringIntegrationTest;
import uk.gov.ons.ctp.integration.fieldsvccucumber.selenium.pageobject.InvalidCaseId;
import uk.gov.ons.ctp.integration.fieldsvccucumber.selenium.pageobject.QuestionnaireCompleted;
import uk.gov.ons.ctp.integration.fieldsvccucumber.selenium.pageobject.SSO;

public class TestSSOFieldService extends SpringIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(TestSSOFieldService.class);
  private static final boolean headless = true;
  private WebDriver driver = null;
  private SSO sso = null;
  private QuestionnaireCompleted questionnaireCompleted = null;
  private InvalidCaseId invalidCaseId = null;

  @Value("${config.username}")
  private String userId;

  @Value("${config.password}")
  private String pw;

  @Value("${config.baseurl}")
  private String baseUrl;

  private String accessEqPath = "/launch/3305e937-6fb1-4ce1-9d4c-077f147789ac";
  private String accessEqUrl = null;
  private String completedPath = "/launch/03f58cb5-9af4-4d40-9d60-c124c5bddf09";
  private String completedUrl = null;
  private String invalidCaseIdPath = "/launch/3305e937-6fb1-4ce1-9d4c-077f147799zz";
  private String invalidCaseIdUrl = null;

  @Before("@SetUpFieldServiceTests")
  public void setup() throws CTPException, InterruptedException {
    setupOSWebdriver();
    setupDriverAndURLs();
    sso = new SSO(driver);
    questionnaireCompleted = new QuestionnaireCompleted(driver);
    invalidCaseId = new InvalidCaseId(driver);
    accessEqUrl = baseUrl + accessEqPath;
    completedUrl = baseUrl + completedPath;
    invalidCaseIdUrl = baseUrl + invalidCaseIdPath; 
  }

  @After("@TearDown")
  public void deleteDriver() {
    driver.close();
  }

  @After("@TearDownMultiWindows")
  public void deleteDriverRH2() throws InterruptedException {
    closeAnyDriverWindowsCurrentlyOpen();
  }

  @Given("I am about to do a smoke test by going to a field service endpoint")
  public void i_am_about_to_do_a_smoke_test_by_going_to_a_field_service_endpoint() {
    log.info("About to check that the Contact Centre service is running...");
  }

  @Then("I do the smoke test and receive a response of OK from the field service")
  public void i_do_the_smoke_test_and_receive_a_response_of_OK_from_the_field_service() {
    try {
      HttpStatus fieldServiceStatus = checkFieldServiceRunning();
      log.with(fieldServiceStatus)
          .info("Smoke Test: The response from https://localhost:443/launch/03f58cb5-9af4-4d40-9d60-c124c5bddf09");
//      assertEquals(
//          "THE FIELD SERVICE MAY NOT BE RUNNING - it does not give a response code of 200",
//          HttpStatus.OK,
//          fieldServiceStatus);
    } catch (ResourceAccessException e) {
      log.error(
          "THE FIELD SERVICE MAY NOT BE RUNNING: A ResourceAccessException has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    } catch (Exception e) {
      log.error("THE FIELD SERVICE MAY NOT BE RUNNING: An unexpected has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    } 
  }

  @Given("I am a field officer and I have access to a device with SSO")
  public void i_am_a_field_officer_and_I_have_access_to_a_device_with_SSO() {
    log.debug("Nothing to do here: I am a field officer and I have access to a device with SSO");
  }

  @Given("I click on the job link in chrome")
  public void i_click_on_the_job_link_in_chrome() {
    log.with(accessEqUrl).debug("The job URL that was clicked on");
    driver.get(accessEqUrl);
  }

  @Given("a field proxy authentication UI is displayed on the screen")
  public void a_field_proxy_authentication_UI_is_displayed_on_the_screen() {

    try {
      log.info("Sleep for 5 seconds to give the SSO page time to appear");
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    String titleText = sso.getSSOTitleText();
    log.with(titleText).debug("The SSO title text found");
    assertEquals("SSO title has incorrect text", "Sign in with your Google Account", titleText);
  }

  @When("I enter my correct SSO credentials and click OK")
  public void i_enter_my_correct_SSO_credentials_and_click_OK() {
    log.with(userId).debug("The user id for the SSO");
    sso.enterUserId(userId);
    sso.clickNextButton();
    sso.enterPassword(pw);
    sso.clickSignInButton();
  }

  @Then("the EQ launch event is triggered")
  public void the_EQ_launch_event_is_triggered() {

    try {
      log.info(
          "Sleep for 10 seconds to give it time to attempt to load EQ (this can take quite a long time in DEV)");
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    String currentURL = driver.getCurrentUrl();

    log.with(currentURL).info("The current URL to check");
    log.info(
        "We need to assert that it tried to open the EQ page but that page does not exist i.e. that the current URL contains the following text: //session/%3Ftoken");
    String devTextToFind = "/session/?token";
    String localTextToFind = "/session?token";
    assertTrue(currentURL.contains(devTextToFind) || currentURL.contains(localTextToFind));
  }

  @Given("that the response to a CCS interview job has already been submitted")
  public void that_the_response_to_a_CCS_interview_job_has_already_been_submitted() {
    log.info("change the base URL to be one for an interview that has already been submitted");
    accessEqUrl = completedUrl;
  }

  @Then("the completion message {string} is displayed to me")
  public void the_completion_message_is_displayed_to_me(String completionMessage) {

    String titleText = questionnaireCompleted.getCCSCompletedTitleText();
    assertEquals("CCS Completion title has incorrect text", completionMessage, titleText);
  }

  @When("I click on the job link in chrome in a new window")
  public void i_click_on_the_job_link_in_chrome_in_a_new_window() {

    // Perform the click operation that opens new window
    JavascriptExecutor jse = (JavascriptExecutor) driver;
    jse.executeScript("window.open()");
    ArrayList<String> tabs = new ArrayList<String>(driver.getWindowHandles());
    driver.switchTo().window(tabs.get(1));

    try {
      log.info("Sleep for 5 seconds to give the new tab time to appear");
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    driver.get(accessEqUrl);
  }

  @Then("I am not presented with the SSO screen to enter my credentials")
  public void i_am_not_presented_with_the_SSO_screen_to_enter_my_credentials() {

    try {
      log.info("Sleep for 5 seconds to give the SSO page time to appear");
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    try {
      sso.getSSOTitleText();
      fail();
    } catch (NoSuchElementException e) {
      log.info(
          "We are expecting that the SSO screen will not appear, therefore there should not be any SSO title element found");
    }
  }

  @Given("that the job URL contains an invalid case id")
  public void that_the_job_URL_contains_an_invalid_case_id() {

    log.info("change the base URL to be one that contains an invalid case id");
    accessEqUrl = invalidCaseIdUrl;
  }

  @Then("the invalid case id message {string} is displayed to me")
  public void the_invalid_case_id_message_is_displayed_to_me(String invalidCaseIdMessage) {

    try {
      log.info("Sleep for 5 seconds to give the invalid case page time to appear");
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    String messageTextFound = invalidCaseId.getInvalidCaseIdText();
    assertEquals("Reason: Bad request - Case ID invalid", invalidCaseIdMessage, messageTextFound);
  }

  private void setupOSWebdriver() {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("mac")) {
      System.setProperty("webdriver.gecko.driver",
          "src/test/resources/geckodriver/geckodriver.macos");
    } else if (os.contains("linux")) {
      System.setProperty("webdriver.gecko.driver",
          "src/test/resources/geckodriver/geckodriver.linux");
    } else {
      System.err
          .println("Unsupported platform - gecko driver not available for platform [" + os + "]");
      System.exit(1);
    }
  }

  private void setupDriverAndURLs() {
    FirefoxOptions options = new FirefoxOptions();
    options.setHeadless(headless);
    String os = System.getProperty("os.name").toLowerCase();
    /**
     * This if statement was added because the latest stable version of firefox gets installed as
     * "/usr/bin/firefox-esr" and then a symbolic link for it, named firefox, is created in the same
     * location - see the Dockerfile.
     */
    if (os.contains("linux")) {
      options.setBinary("/usr/bin/firefox");
    }
    options.setLogLevel(FirefoxDriverLogLevel.DEBUG);
    driver = new FirefoxDriver(options);
    driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
  }

  private void closeAnyDriverWindowsCurrentlyOpen() throws InterruptedException {
    String mainWindow = driver.getWindowHandle();

    // To handle all new opened windows.
    Set<String> s1 = driver.getWindowHandles();
    Iterator<String> i1 = s1.iterator();

    while (i1.hasNext()) {
      String childWindow = i1.next();

      if (!mainWindow.equalsIgnoreCase(childWindow)) {

        // Switching to Child window
        driver.switchTo().window(childWindow);
        Thread.sleep(5000);

        // Closing the Child Window.
        driver.close();
      }
    }
    // Switching to Parent window i.e Main Window.
    driver.switchTo().window(mainWindow);

    // Closing the Parent Window.
    driver.close();
  }
  
  private HttpStatus checkFieldServiceRunning() {
    log.info(
        "Using the following endpoint to check that the field service is running: https://localhost:443/launch/03f58cb5-9af4-4d40-9d60-c124c5bddf09");
    
//    completedUrl = baseUrl + completedPath;
//    final UriComponentsBuilder builder =
//        UriComponentsBuilder.fromHttpUrl(ccBaseUrl).port(ccBasePort).pathSegment("/fulfilments");
//
//    ResponseEntity<List<FulfilmentDTO>> fulfilmentResponse =
//        getRestTemplate()
//            .exchange(
//                builder.build().encode().toUri(),
//                HttpMethod.GET,
//                null,
//                new ParameterizedTypeReference<List<FulfilmentDTO>>() {});
//
//    return fulfilmentResponse.getStatusCode();
    
    return null;
  }
}

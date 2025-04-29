package steps;

import io.cucumber.java.en.*;
import pages.DynamicLoadingPage;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicLoadingSteps {

    DynamicLoadingPage dynamicPage;

    @Given("I open the dynamic loading page")
    public void openDynamicLoadingPage() {
        dynamicPage.openUrl("https://the-internet.herokuapp.com/dynamic_loading/2");
    }

    @When("I click the Start button")
    public void startLoading() {
        dynamicPage.startLoading();
    }

    @Then("I should see {string} message")
    public void verifyLoadedText(String expectedText) {
        assertThat(dynamicPage.getLoadedText()).isEqualTo(expectedText);
    }
}

package steps;

import io.cucumber.java.en.*;
import pages.DropdownPage;

import static org.assertj.core.api.Assertions.assertThat;

public class DropdownSteps {

    DropdownPage dropdownPage;

    @Given("I open the dropdown page")
    public void openDropdownPage() {
        dropdownPage.openUrl("https://the-internet.herokuapp.com/dropdown");
    }

    @When("I select {string}")
    public void selectOption(String optionText) {
        dropdownPage.selectOption(optionText);
    }

    @Then("I should see {string} as selected")
    public void verifySelectedOption(String expected) {
        assertThat(dropdownPage.getSelectedOption()).isEqualTo(expected);
    }
}

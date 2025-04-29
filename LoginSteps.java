package steps;

import io.cucumber.java.en.*;
import net.thucydides.core.annotations.Steps;
import pages.LoginPage;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginSteps {

    LoginPage loginPage;

    @Given("I open the login page")
    public void openLoginPage() {
        loginPage.openUrl("https://the-internet.herokuapp.com/login");
    }

    @When("I login as {string} with password {string}")
    public void login(String username, String password) {
        loginPage.loginAs(username, password);
    }

    @Then("I should see the success message {string}")
    public void verifyLoginSuccess(String expectedMessage) {
        assertThat(loginPage.getFlashMessage()).contains(expectedMessage);
    }
}

package tests;

import net.serenitybdd.junit5.SerenityTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pages.LoginPage;
import pages.DropdownPage;
import pages.DynamicLoadingPage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates usage of WebElementFacade in different scenarios:
 * - Login Form Handling
 * - Dropdown Interaction
 * - Dynamic Content Loading
 */
@SerenityTest
class WebElementFacadeExamplesTest {

    LoginPage loginPage;
    DropdownPage dropdownPage;
    DynamicLoadingPage dynamicLoadingPage;

    @Test
    @DisplayName("Login with valid credentials and verify success message")
    void loginFormHandlingExample() {
        loginPage.openUrl("https://the-internet.herokuapp.com/login");
        loginPage.loginAs("tomsmith", "SuperSecretPassword!");

        assertThat(loginPage.getFlashMessage())
            .contains("You logged into a secure area!");
    }

    @Test
    @DisplayName("Select an option from dropdown and verify selection")
    void dropdownInteractionExample() {
        dropdownPage.openUrl("https://the-internet.herokuapp.com/dropdown");
        dropdownPage.selectOption("Option 2");

        assertThat(dropdownPage.getSelectedOption())
            .isEqualTo("Option 2");
    }

    @Test
    @DisplayName("Wait for dynamic content to load and verify the result")
    void dynamicContentLoadingExample() {
        dynamicLoadingPage.openUrl("https://the-internet.herokuapp.com/dynamic_loading/2");
        dynamicLoadingPage.startLoading();

        assertThat(dynamicLoadingPage.getLoadedText())
            .isEqualTo("Hello World!");
    }
}

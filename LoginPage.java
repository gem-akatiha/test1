package pages;

import net.serenitybdd.core.annotations.findby.FindBy;
import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.core.pages.WebElementFacade;

/**
 * Page Object for the Login Page.
 */
public class LoginPage extends PageObject {

    @FindBy(id = "username")
    private WebElementFacade usernameField;

    @FindBy(id = "password")
    private WebElementFacade passwordField;

    @FindBy(css = "button[type='submit']")
    private WebElementFacade loginButton;

    @FindBy(id = "flash")
    private WebElementFacade flashMessage;

    public void loginAs(String username, String password) {
        usernameField.type(username);
        passwordField.type(password);
        loginButton.click();
    }

    public String getFlashMessage() {
        return flashMessage.waitUntilVisible().getText();
    }
}

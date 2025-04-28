package pages;

import net.serenitybdd.core.annotations.findby.FindBy;
import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.core.pages.WebElementFacade;

/**
 * Page Object for the Dynamic Loading Example.
 */
public class DynamicLoadingPage extends PageObject {

    @FindBy(css = "#start button")
    private WebElementFacade startButton;

    @FindBy(css = "#finish h4")
    private WebElementFacade finishText;

    public void startLoading() {
        startButton.click();
    }

    public String getLoadedText() {
        return finishText.waitUntilVisible().getText();
    }
}

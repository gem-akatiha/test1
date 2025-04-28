package pages;

import net.serenitybdd.core.annotations.findby.FindBy;
import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.core.pages.WebElementFacade;

/**
 * Page Object for the Dropdown Page.
 */
public class DropdownPage extends PageObject {

    @FindBy(id = "dropdown")
    private WebElementFacade dropdownMenu;

    public void selectOption(String optionText) {
        dropdownMenu.selectByVisibleText(optionText);
    }

    public String getSelectedOption() {
        return dropdownMenu.getSelectedVisibleTextValue();
    }
}

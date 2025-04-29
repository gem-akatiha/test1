Feature: Examples on How to use WebElementFacade

  Scenario: Login with valid credentials
    Given I open the login page
    When I login as "tomsmith" with password "SuperSecretPassword!"
    Then I should see the success message "You logged into a secure area!"

  Scenario: Select option from dropdown
    Given I open the dropdown page
    When I select "Option 2"
    Then I should see "Option 2" as selected

  Scenario: Load dynamic content
    Given I open the dynamic loading page
    When I click the Start button
    Then I should see "Hello World!" message

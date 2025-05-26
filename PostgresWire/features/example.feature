Feature: Example addition
  Scenario: Check simple math
    Given two numbers 2 and 2
    When they are added
    Then the result should be 4

  Scenario: Check string equality
    Given a string "Hello"
    When compared to "Hello"
    Then they should be equal

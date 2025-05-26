Feature: Goose test scenarios from Gherkin feature files

    Rule: Goose scenarios can be executed from Gherkin feature files
        Scenario: Simple HTTP GET request with Goose
            Given a loadtest-compatible feature file with name "http-get.feature" and content:
            """
            @loadtest
            Feature: HTTP GET Request
            Scenario: Perform HTTP GET
                Given a single-threaded HTTP Server named "counting-server" running on "http://127.0.0.1"
                And the server keeps counting HTTP requests
                And the current request count is 0
                And the server "counting-server" sleeps for 120 milliseconds before responding

                When at least 100 GET requests are sent to "http://127.0.0.1" at route "/"

                Then the response status should be 200
                And the response body should contain "count" larger than 99
            """
            And step definitions are implemented for the feature file "http-get.feature"
            
            When a Cucumber test is executed for the feature file "http-get.feature"
            And a Goose test is executed for the feature file "http-get.feature"
            
            Then the HTTP Server "counting-server" should still be running
            And the HTTP Server "counting-server" should have a request count larger than 99
            And the Cucumber test for "http-get.feature" should pass
            And the Goose test for "http-get.feature" should fail because the response time is too high
            And the Goose test for "http-get.feature" should have a response time larger than 10 seconds

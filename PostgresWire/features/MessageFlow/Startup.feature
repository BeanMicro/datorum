Feature: Postgres Backend Protocol
  In order to interact with Postgres clients
  As a backend server
  I want to implement the wire-protocol messages correctly

  Rule: Startup and Authentication
    Scenario: Successful startup and authentication
      Given client sends StartupMessage(application_name: "psql")
      When server processes StartupMessage
      Then server responds with AuthenticationOk
      And server responds with ParameterStatus(name: "client_encoding", value: "UTF8")
      And server responds with ReadyForQuery(status: "I")

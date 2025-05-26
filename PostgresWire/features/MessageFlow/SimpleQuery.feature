Feature: Postgres Backend Protocol
  In order to interact with Postgres clients
  As a backend server
  I want to implement the wire-protocol messages correctly

  Rule: Simple Query
    Scenario: Execute a successful simple query
      Given client is authenticated
      When client sends Query(sql: "SELECT 1")
      Then server responds with RowDescription(fields: [{ name: "?column?", type: "int4" }])
      And server responds with DataRow(values: ["1"])
      And server responds with CommandComplete(tag: "SELECT 1")
      And server responds with ReadyForQuery(status: "I")

    Scenario: Simple query with syntax error
      Given client is authenticated
      When client sends Query(sql: "SELEC invalid")
      Then server responds with ErrorResponse(severity: "ERROR", code: "42601")
      And server responds with ReadyForQuery(status: "I")

  Rule: Extended Query Protocol (Prepared Statements)
    Scenario: Parse, Bind, Execute and Sync flow
      Given client is authenticated
      When client sends Parse(name: "", query: "INSERT INTO t (id) VALUES ($1)")
      And client sends Bind(name: "", statement: "", parameters: [1])
      And client sends Describe(type: "Statement", name: "")
      And client sends Execute(name: "", max_rows: 0)
      And client sends Sync
      Then server responds with ParseComplete
      And server responds with BindComplete
      And server responds with ParameterDescription(types: ["int4"])
      And server responds with RowDescription(fields: [])
      And server responds with CommandComplete(tag: "INSERT 0 1")
      And server responds with ReadyForQuery(status: "I")
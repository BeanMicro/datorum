Feature: Postgres Backend Protocol
  In order to interact with Postgres clients
  As a backend server
  I want to implement the wire-protocol messages correctly

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
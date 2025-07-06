use apache_avro::schema::RecordSchema;
use apache_avro::Schema;

static RAW_SCHEMA_1: &str = r#"{
        "name": "A",
        "type": "record",
        "fields": [
            {"name": "field_one", "type": "float"}
        ]
    }"#;

// This definition depends on the definition of A above
static RAW_SCHEMA_2: &str = r#"{
        "name": "B",
        "type": "record",
        "fields": [
            {"name": "field_one", "type": "A"}
        ]
    }"#;

pub fn test_schemas() {
    // if the schemas are not valid, this function will return an error
    let schemas: Vec<Schema> = Schema::parse_list(&[RAW_SCHEMA_1, RAW_SCHEMA_2]).unwrap();
    // schemas can be printed for debugging
    println!("{:?}", schemas);
    println!("--- Pretty Printed JSON Schema ---");
    println!("{}", serde_json::to_string_pretty(&schemas).unwrap());

    println!("--- Schema fields ---");
    let schema_one = &schemas[0];
    println!("Name: {}", schema_one.name().unwrap());
    // Check if the schema is a record type and access its fields
    if let Schema::Record(RecordSchema { fields, .. }) = schema_one {
        for field in fields {
            println!("Field: {} of type {:?}", field.name, field.schema);
        }
    } else {
        println!("Schema is not a record type");
    }
}

use cucumber::{given, then, when, World, WorldInit};

#[derive(Debug, Default, WorldInit)]
struct ExampleWorld {
    numbers: Vec<i32>,
    result: Option<i32>,
    text: Option<String>,
}

#[given(expr = "two numbers {int} and {int}")]
fn two_numbers(world: &mut ExampleWorld, a: i32, b: i32) {
    world.numbers = vec![a, b];
}

#[when("they are added")]
fn they_are_added(world: &mut ExampleWorld) {
    world.result = Some(world.numbers.iter().sum());
}

#[then(expr = "the result should be {int}")]
fn result_should_be(world: &mut ExampleWorld, expected: i32) {
    assert_eq!(world.result, Some(expected));
}

#[given(expr = "a string {string}")]
fn a_string(world: &mut ExampleWorld, text: String) {
    world.text = Some(text);
}

#[when(expr = "compared to {string}")]
fn compared_to(world: &mut ExampleWorld, other: String) {
    if let Some(ref t) = world.text {
        assert_eq!(t, &other);
    } else {
        panic!("No text set");
    }
}

#[then("they should be equal")]
fn they_should_be_equal(_world: &mut ExampleWorld) {}

#[tokio::main]
async fn main() {
    ExampleWorld::cucumber()
        .run("tests/features")
        .await;
}

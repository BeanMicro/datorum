use cucumber::{given, then, when};
#[derive(Debug, Default, cucumber::World)]
pub struct PostgresWireWorld {
    numbers: Vec<i32>,
    result: Option<i32>,
    text: Option<String>,
}

#[given(expr = "two numbers {int} and {int}")]
fn two_numbers(world: &mut PostgresWireWorld, a: i32, b: i32) {
    world.numbers = vec![a, b];
}

#[when("they are added")]
fn they_are_added(world: &mut PostgresWireWorld) {
    world.result = Some(world.numbers.iter().sum());
}

#[then(expr = "the result should be {int}")]
fn result_should_be(world: &mut PostgresWireWorld, expected: i32) {
    assert_eq!(world.result, Some(expected));
}

#[given(expr = "a string {string}")]
fn a_string(world: &mut PostgresWireWorld, text: String) {
    world.text = Some(text);
}

#[when(expr = "compared to {string}")]
fn compared_to(world: &mut PostgresWireWorld, other: String) {
    if let Some(ref t) = world.text {
        assert_eq!(t, &other);
    } else {
        panic!("No text set");
    }
}

#[then("they should be equal")]
fn they_should_be_equal(_world: &mut PostgresWireWorld) {}

pub fn add(left: u64, right: u64) -> u64 {
    left + right
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn it_works() {
        let result = add(2, 2);
        assert_eq!(result, 4);
    }
}

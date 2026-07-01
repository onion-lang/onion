# Error Handling Examples

Onion provides `Option` and `Result` types for explicit, composable error handling without relying solely on exceptions.

## Option

`Option[T]` represents a value that may be absent.

```onion
def findUser(id: Int): Option[User] {
  if id > 0 {
    return Option::some(new User(id, "Alice"))
  }
  return Option::none()
}

val user = findUser(1)
if user.isEmpty() {
  println("not found")
} else {
  println("found " + user.get().name())
}

// Provide a fallback with getOrElse.
val name = user.map((u: User) -> { return u.name() }).getOrElse("guest")
```

## Result

`Result[T, E]` represents either a success value or an error value.

```onion
def parseAge(raw: String): Result[Int, String] {
  try {
    val age = JInteger::parseInt(raw)
    if age >= 0 && age <= 150 {
      return Result::ok(age)
    }
    return Result::err("age out of range")
  } catch e: NumberFormatException {
    return Result::err("invalid age")
  }
}

val age = parseAge("30")
if age.isOk() {
  println("age=" + age.getOrElse(0))
} else {
  println("error=" + age.getError())
}
```

## Do-Notation for Result

Chain fallible operations with `do[Result]`.

```onion
def validate(input: UserInput): Result[UserInput, String] {
  return do[Result] {
    name <- optionToResult(nonEmpty(input.name()), "name is required")
    age <- parseAge(input.age())
    email <- validateEmail(input.email())
    ret new UserInput(name, "" + age, email)
  }
}
```

## Complete Example: Result Validation

**`ResultValidation.on`** validates user input using `Option` and `Result`.

```onion
record UserInput(name: String, age: String, email: String)

def nonEmpty(value: String): Option[String] {
  if value != null && value.trim().length() > 0 {
    return Option::some(value.trim())
  }
  return Option::none()
}

def parseAge(raw: String): Result[Int, String] {
  try {
    val age = JInteger::parseInt(raw)
    if age >= 0 && age <= 150 {
      return Result::ok(age)
    }
    return Result::err("age out of range: " + raw)
  } catch e: NumberFormatException {
    return Result::err("invalid age: " + raw)
  }
}

def validateEmail(raw: String): Result[String, String] {
  val trimmed = raw.trim()
  if trimmed.indexOf("@") > 0 && trimmed.indexOf(".") > trimmed.indexOf("@") {
    return Result::ok(trimmed)
  }
  return Result::err("invalid email: " + raw)
}

def validateDo(input: UserInput): Result[UserInput, String] {
  return do[Result] {
    name <- optionToResult(nonEmpty(input.name()), "name is required")
    age <- parseAge(input.age())
    email <- validateEmail(input.email())
    ret new UserInput(name, "" + age, email)
  }
}

val good = new UserInput("Alice", "30", "alice@example.com")
println(formatResult(validateDo(good)))
```

Output:

```
OK(Alice, 30, alice@example.com)
ERR(name is required)
ERR(invalid age: abc)
ERR(invalid email: not-an-email)
```

## Combining Option and Result

Convert an `Option` into a `Result` to provide a custom error message.

```onion
def optionToResult(opt: Option[String], error: String): Result[String, String] {
  if opt.isEmpty() {
    return Result::err(error)
  }
  return Result::ok(opt.get())
}
```

## Next Steps

- [Async Examples](async.md) - Error handling with futures
- [Scripting Examples](scripting.md) - Validate CLI input
- [Standard Library Reference](../reference/stdlib.md) - Full Option/Result API

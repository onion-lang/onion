# エラーハンドリングの例

Onionは `Option` と `Result` 型を提供し、例外のみに頼らない明示的で合成可能なエラーハンドリングができます。

## Option

`Option[T]` は値が存在するかもしれないことを表します。

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

// getOrElse でフォールバックを提供
val name = user.map((u: User) -> { return u.name() }).getOrElse("guest")
```

## Result

`Result[T, E]` は成功値またはエラー値のどちらかを表します。

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

## ResultのDo記法

`do[Result]` で失敗しうる処理を連鎖させます。

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

## 完全な例: Resultによる入力検証

**`ResultValidation.on`** は `Option` と `Result` を使ってユーザー入力を検証します。

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

出力:

```
OK(Alice, 30, alice@example.com)
ERR(name is required)
ERR(invalid age: abc)
ERR(invalid email: not-an-email)
```

## OptionとResultの組み合わせ

`Option` を `Result` に変換してカスタムエラーメッセージを与えます。

```onion
def optionToResult(opt: Option[String], error: String): Result[String, String] {
  if opt.isEmpty() {
    return Result::err(error)
  }
  return Result::ok(opt.get())
}
```

## 次のステップ

- [非同期の例](async.md) - Futureを使ったエラーハンドリング
- [スクリプティングの例](scripting.md) - CLI入力の検証
- [標準ライブラリリファレンス](../reference/stdlib.md) - Option/Result API全文

# Documentation verifier fixture

<!-- onion-example: compile -->
```onion
val answer = 42
```

<!-- onion-example: run -->
```onion
System::err.println("warning")
println("hello")
```
<!-- onion-output: stdout -->
```text
hello
```
<!-- onion-output: stderr -->
```text
warning
```

<!-- onion-example: reject code=E0002 -->
```onion
println(undefinedValue)
```

<!-- onion-example: fragment reason="uses a value introduced by surrounding prose" -->
````onion
value.transform()
````

# Hello World

Let's write and run your first Onion program!

## The Classic Hello World

Create a file named `Hello.on`:

```onion
IO::println("Hello, World!")
```

That's it! This single line is a complete Onion program.

## Running Your Program

There are two ways to run Onion programs:

### Method 1: Direct Execution with `onion`

The `onion` command compiles and runs your program in memory:

```bash
onion Hello.on
```

Output:
```
Hello, World!
```

### Method 2: Compilation with `onionc`

Compile to `.class` files and run with Java:

```bash
# Compile
onionc Hello.on

# Run with Java
java Hello
```

## Understanding the Code

```onion
IO::println("Hello, World!")
```

- `IO` - The built-in I/O module
- `::` - Static method access operator (similar to Java's `.`)
- `println()` - Print with newline function
- `"Hello, World!"` - String literal

## More Examples

### Using Variables

```onion
val message: String = "Hello, Onion!"
IO::println(message)
```

### Getting User Input

```onion
val name: String = IO::readln("What's your name? ")
IO::println("Hello, " + name + "!")
```

Run it:
```bash
onion greeting.on
```

Output:
```
What's your name? Alice
Hello, Alice!
```

### With a Main Class

You can also write programs with explicit class definitions:

```onion
class HelloWorld {
  public:
    static def main(args: String[]) {
      IO::println("Hello, World!")
    }
}
```

This follows the traditional Java main method pattern.

## Command-Line Arguments

Access command-line arguments through the `main` method:

```onion
class ArgsDemo {
  public:
    static def main(args: String[]) {
      IO::println("Number of arguments: " + args.length)
      for var i: Int = 0; i < args.length; i = i + 1 {
        IO::println("Arg " + i + ": " + args[i])
      }
    }
}
```

Run with arguments:
```bash
onion ArgsDemo.on hello world 123
```

Output:
```
Number of arguments: 3
Arg 0: hello
Arg 1: world
Arg 2: 123
```

## Next Steps

Now that you've written your first Onion program, continue with:

- [Quick Start Guide](quick-start.md) - Learn essential language features
- [Basic Syntax](../guide/basic-syntax.md) - Variables, types, and operators

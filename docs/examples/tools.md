# Tools

A `tool` is a function with a checked capability boundary. One declaration yields a
CLI, `--help`, a machine-readable contract, and a `--plan` dry run — and the compiler
proves the body cannot exceed the declaration (`E0077` at the offending call site if
it tries; `E0078` if it overclaims).

The three-command sequence an agent follows:

```bash
$ onion Snapshot.on --contract
[{"tool":"snapshot","params":[{"name":"src","type":"String","role":"positional"},
  {"name":"dst","type":"String","role":"positional"},
  {"name":"tag","type":"String","role":"flag","default":"latest"}],
  "returns":"Int","capabilities":["read(src)","write(dst)","console"]}]

$ onion Snapshot.on notes.txt backup.txt --plan
plan: `snapshot` would
  read    src = notes.txt
  write   dst = backup.txt
  console
(nothing was executed)

$ onion Snapshot.on notes.txt backup.txt --tag nightly
snapshot [nightly]: 1 file copied
```

**Snapshot.on**

```onion
tool snapshot(src: String, dst: String, tag: String = "latest"): Int
  requires { read(src), write(dst), console }
{
  val data = Files::readText(src)
  Files::writeText(dst, data)
  IO::println("snapshot [" + tag + "]: 1 file copied")
  return 0
}
```

Required parameters become positionals; defaulted parameters become `--name` flags; a
`Boolean` default becomes a switch. An absent flag's default is evaluated as the
original expression, in the language.

The full demo — a log-digesting tool that combines a `shape` boundary for the messy
input with a capability boundary for the effects — is `run/ToolDemo.on` in the
repository, and the guide chapter walks through every piece:
[Tools and Capabilities](../guide/tools.md).

# tool

`tool` は検査済み capability 境界を持つ関数です。一つの宣言から CLI・`--help`・機械可読
契約・`--plan` ドライランが導出され、本体が宣言を超えられないことをコンパイラが証明
します（超えようとすれば違反呼び出しの位置で `E0077`、過大申告なら `E0078`）。

エージェントがたどる3コマンドの手順：

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

必須パラメータは位置引数に、デフォルト付きパラメータは `--name` フラグに、`Boolean` の
デフォルトはスイッチになります。省略されたフラグのデフォルトは元の式として言語内で
評価されます。

完全なデモ —— 雑な入力のための `shape` 境界と効果のための capability 境界を一つの
ファイルで組み合わせた、ログ集計 tool —— はリポジトリの `run/ToolDemo.on` にあり、
ガイド章が各部品を解説しています：[tool と capability](../guide/tools.md)。

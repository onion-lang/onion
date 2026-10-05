# Dogfood scenarios

Real recurring tasks written in Onion, kept building and passing as the language
changes. They are the evolution loop's main fitness signal
([docs/evolution](../docs/evolution/README.md)): a scenario that breaks, or needs a
workaround, becomes a `kind:friction` or `kind:bug` issue labelled `source:dogfood`.

| Project | Task | Exercises |
|---|---|---|
| `meeting-summary` | Meeting notes to a typed summary through Claude | `onion-llm`, json shapes with lists and nested records, `LlmError` select, `tool` + `requires` |
| `gh-digest` | A day of a repository's PRs and issues as Markdown, posted to Slack | `Http::request`, json shapes over a real API's response, `Result`, `tool` with `clock`/`env`/`net` |
| `sdk-probe` | The Anthropic Java SDK called directly | Interop with a large Kotlin-built SDK: builders, overloads, `Optional`, a caught SDK exception |
| `xlsx-report` | A sales spreadsheet to a slide deck of regional totals | Apache POI through plain interop, try-with-resources, `groupBy`/`sumBy`, auto-CLI flags |

## Run them

```bash
dogfood/run.sh                 # builds onion and onion-llm with sbt, then every project
dogfood/run.sh gh-digest       # only some projects
dogfood/run.sh --prebuilt      # reuse the last `sbt assembly`; no sbt needed
```

Which `onion-llm` a project gets: the default mode builds it from this checkout and publishes
it locally as `0.0.0-dogfood`. `--prebuilt` uses that local copy if it exists, and otherwise
resolves `onion-llm` from Maven Central at the jar's own version (the latest release when the
jar is a development build; `LLM_VERSION=x.y.z` overrides), so all four projects run with a
release jar and no sbt.

CI runs the same script (`.github/workflows/dogfood.yml`). On Windows, where `sbt` is
`sbt.bat`, build first from PowerShell and then use `--prebuilt` from Git Bash:

```powershell
$PSNativeCommandArgumentPassing = 'Standard'
sbt.bat 'assembly; set llm / version := "0.0.0-dogfood"; llm/publishLocal'
```

## Rules for a scenario

- **Offline.** No API keys and no network beyond Maven downloads. Tests start a local
  fake (`onion.Server`) and pass its URL in; `run.sh` also unsets credentials and points
  `ANTHROPIC_BASE_URL` and `GITHUB_API_URL` at a closed port.
- **Fixtures say what they are.** A response that was not captured from the real service
  is marked synthetic in a README next to it.
- **A workaround is a finding.** Comment it in the code with the issue it waits on.
- **Real tasks, not invented ones.** Add a scenario when it is something someone does.

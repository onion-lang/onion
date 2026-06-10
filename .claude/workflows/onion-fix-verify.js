export const meta = {
  name: 'onion-fix-verify',
  description: '修正後のOnionコンパイラを多角的に検証する（再現ケース＋周辺リグレッション＋エッジケース）',
  whenToUse: 'バグ修正やgrammar変更の後、sbt testに加えて挙動レベルの検証をしたいとき。args: { jar: string, fixDescription: string, repros: string[] }',
  phases: [{ title: 'Verify', detail: '3視点の並列検証' }],
}

const JAR = (args && args.jar) || '/tmp/onion-latest.jar'
const FIX = (args && args.fixDescription) || '(説明なし)'
const REPROS = (args && args.repros) || []

const VERDICT = {
  type: 'object',
  properties: {
    pass: { type: 'boolean' },
    issues: { type: 'array', items: { type: 'string' } },
    detail: { type: 'string' },
  },
  required: ['pass', 'detail'],
}

const COMMON = `Onionコンパイラ（${JAR}）の修正を検証します。
修正内容: ${FIX}
プローブ方法: /tmp/verify-N.on にコードをWrite → java -cp ${JAR} onion.tools.ScriptRunner <ファイル>
sbtは使わない。日本語で簡潔に。`

phase('Verify')
const lenses = [
  `${COMMON}\n視点1: 再現ケースの確認。以下の再現コードが全て正しく動く（または正しいコンパイルエラーになる）ことを確認:\n${REPROS.join('\n---\n')}`,
  `${COMMON}\n視点2: 周辺リグレッション。この修正が壊しうる隣接機能を5つ挙げ、それぞれ動作確認するOnionコードを書いて実行。`,
  `${COMMON}\n視点3: エッジケース攻撃。この修正の境界条件（空、ネスト、組合せ、極端な値）を突くコードを5つ書いて実行し、クラッシュや誤動作を探す。`,
]

const verdicts = (await parallel(lenses.map((p, i) => () =>
  agent(p, { label: `lens:${i + 1}`, schema: VERDICT })
))).filter(Boolean)

return {
  allPass: verdicts.every(v => v.pass),
  issues: verdicts.flatMap(v => v.issues || []),
  details: verdicts.map(v => v.detail),
}

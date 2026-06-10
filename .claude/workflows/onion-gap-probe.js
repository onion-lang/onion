export const meta = {
  name: 'onion-gap-probe',
  description: 'Onion言語の実用性ギャップを複数ドメイン並列でプローブし、budget内でbroken検証まで行う',
  whenToUse: '新機能実装後の回帰探索や、実用化ギャップの棚卸しをしたいとき。args: { jar?: string, domains?: string[] }',
  phases: [
    { title: 'Probe', detail: 'ドメイン別の並列プローブ' },
    { title: 'Verify', detail: 'broken報告の再現検証（budget残量があるときのみ）' },
  ],
}

const JAR = (args && args.jar) || '/tmp/onion-latest.jar'

const FINDINGS_SCHEMA = {
  type: 'object',
  properties: {
    domain: { type: 'string' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string' },
          status: { type: 'string', enum: ['works', 'broken', 'awkward'] },
          repro: { type: 'string', description: '最小再現Onionコード（broken/awkwardは必須）' },
          observed: { type: 'string' },
          suggestion: { type: 'string' },
          severity: { type: 'string', enum: ['high', 'medium', 'low'] },
        },
        required: ['title', 'status', 'observed', 'severity'],
      },
    },
  },
  required: ['domain', 'findings'],
}

const COMMON = `あなたはOnion言語（JVM上の静的型付け言語）の実用性監査エージェントです。
${JAR} に最新コンパイラがあります。プローブ方法:
  1. /tmp/probe-<ドメイン>-N.on にOnionコードをWriteで書く
  2. Bashで: java -cp ${JAR} onion.tools.ScriptRunner <ファイル> を実行
  3. 出力・エラーを観察。E00xx、I0000、VerifyError、間違った実行結果が重要
※ sbtは絶対に使わない（ロック競合）。javaコマンド直のみ。
※ 文法: 補間 "#{expr}"、リスト [1,2,3]、マップ ["k": v]、範囲 1..5 / 0..<5、
   ラムダ (x: Int) -> x*2 / x -> x*2（要期待型）、trailing lambda xs.map { x => ... }、
   class A { public: def m(): T { ... } }、def this { }、IO::println、(e as T)、
   static var f: T、C::field 代入可、else if、単一行ブロック可。
10個前後のプローブを実行し works/broken/awkward で分類。brokenは最小再現必須。日本語で簡潔に。`

const DEFAULT_DOMAINS = [
  'エラーメッセージ品質: わざと間違えて診断の明瞭さを評価',
  'OOP: 継承/抽象/interface/record/enum/sealed+select/コンストラクタ連鎖',
  'stdlib: Json/Files/DateTime/Strings/Regex/Rand/Proc/Option/Result',
  'ジェネリクス: 自作Box[T]/境界/ネスト/推論/Java相互運用',
  'null安全: T?/?./?:/スマートキャスト',
  '並行処理: Future/do記法/Thread/SAM',
  'スクリプト体験: args/env/終了コード/stdin/シバン/import',
]
const domains = (args && args.domains) || DEFAULT_DOMAINS

phase('Probe')
const results = (await parallel(domains.map((d, i) => () =>
  agent(`${COMMON}\nドメイン: ${d}`, { label: `probe:${i}`, phase: 'Probe', schema: FINDINGS_SCHEMA })
))).filter(Boolean)

const broken = results.flatMap(r => r.findings.filter(f => f.status === 'broken').map(f => ({ ...f, domain: r.domain })))
const awkward = results.flatMap(r => r.findings.filter(f => f.status === 'awkward').map(f => ({ ...f, domain: r.domain })))

// Verifyはbudgetに余裕があるときだけ（スペンドリミット対策）
let confirmed = broken
if (broken.length > 0 && (!budget.total || budget.remaining() > broken.length * 30000)) {
  phase('Verify')
  log(`broken報告 ${broken.length}件を検証`)
  const verified = (await parallel(broken.map(f => () =>
    agent(`${COMMON}\n以下のbroken報告を検証。再現コードを実行し、本物のバグか報告者の構文ミスかを判定。正しい構文に直しても壊れる場合のみ real=true。\n報告: ${f.title}\n再現:\n${f.repro || '(なし)'}\n観察: ${f.observed}`, {
      label: `verify:${(f.title || '').slice(0, 24)}`,
      phase: 'Verify',
      schema: {
        type: 'object',
        properties: {
          real: { type: 'boolean' },
          minimalRepro: { type: 'string' },
          actualError: { type: 'string' },
        },
        required: ['real', 'actualError'],
      },
    }).then(v => ({ ...f, verdict: v })).catch(() => null)
  ))).filter(Boolean)
  confirmed = verified.filter(v => v.verdict && v.verdict.real).map(v => ({
    ...v, repro: (v.verdict.minimalRepro || v.repro), observed: v.verdict.actualError,
  }))
} else if (broken.length > 0) {
  log(`budget節約のためVerifyをスキップ（broken ${broken.length}件は未検証）`)
}

return {
  confirmedBroken: confirmed.map(c => ({ domain: c.domain, title: c.title, severity: c.severity, repro: c.repro, observed: c.observed })),
  awkward: awkward.map(a => ({ domain: a.domain, title: a.title, severity: a.severity, suggestion: a.suggestion })),
  worksCount: results.flatMap(r => r.findings.filter(f => f.status === 'works')).length,
}

# Generics (Erasure-Based) – Design & TODO

Goal: Add basic generics with Scala-style erasure (upper bounds, generic classes/methods, bridge emission for overriding). Deliver in small PR-sized steps.

## Scope
- **Type params on classes/methods** with optional upper bounds (`T <: Foo`). No variance for now.
- **Type applications** on types/expressions: `Box[Int]`, `foo[String](x)`. Keep syntax close to Scala.
- **Erasure**: map type params to `Object` or bound’s erasure in JVM signatures. Emit bridges when overriding causes erased signature collisions.

Out of scope (for later): variance, wildcards, lower bounds, reified generics, generic fields with runtime type info, constraints beyond single upper bound.

## Incremental Plan
### 1) Grammar & AST
- Extend grammar (JavaCC) to parse:
  - Type parameter lists on classes/methods: `<T>` / `<T <: Bound>`.
  - Type applications on types and expressions.
- AST changes (parser-level `AST`):
  - Add `TypeParameter(name, upperBound: Option[TypeNode])`.
  - Add `TypeApplication(target: TypeNode, args: List[TypeNode])`.
  - Thread type params into `ClassDeclaration`, `MethodDeclaration`, `FunctionDeclaration`, `ClosureExpression` if applicable.
  - Thread type args into `NewObject`, `StaticMethodCall`, `MethodCall`, `StaticMemberSelection` when present.
- Regenerate parser sources (JavaCC) after grammar updates.

Checkpoint: Parser builds; AST nodes produced for simple examples like `class Box[T] { def get(x: T): T = x }` and `new Box[Int]()`.

### 2) Typed AST & Typing
- Typed model additions:
  - `TypedAST.TypeParameter(name, upperBound: IRT.Type)` and `TypeApplication` nodes.
  - Include type params on `ClassDefinition`, `MethodDefinition`.
- Environment/bindings:
  - Track type param scopes (class-level + method-level) with bounds.
  - Validate type argument arity at use sites.
  - Bound checking: substitute type args into bounds, ensure conformity (erased to bound when unknown).
- Erasure semantics in typing:
  - Compute erased type for type params: `erasure(T) = erasure(bound)` else `Object`.
  - Erase generic method/class types for codegen signatures.
- Type inference: initially **not** implemented; require explicit type arguments at calls.

Checkpoint: Typing accepts generic class/method definitions and applications, rejects arity/bound errors, produces TypedAST with erased info attached.

### 3) Code Generation (ASM)
- Signature computation: use erased types for descriptors.
- Bridge methods:
  - When a subclass overrides a generic parent method where the erased signature would differ, emit a bridge that delegates to the typed implementation.
  - Detect collisions during method table assembly (compare erased names+descs).
- Class/method naming: no mangling beyond erasure; rely on bridges for dispatch correctness.

Checkpoint: Bytecode for generic hierarchies loads and runs; overridden generic methods dispatch correctly via bridges.

## Testing Plan
- Parser round-trips: generic class/method definitions and instantiations.
- Typing: arity/bound errors; correct acceptance of `Box[Int]`, rejection of `Box[Int, String]`.
- Runtime: execute small programs with generic collections/identity methods; override scenarios that require bridges.
- Non-generic regressions: full existing test suite.

## Notes
- Keep resource strings (errors) consistent; add new keys for arity/bound errors.
- Maintain backward compatibility: existing non-generic code should compile unchanged.
- Consider feature flags if rollout needs to be staged; otherwise, ship as a single feature branch with checkpoints.

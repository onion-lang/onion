package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import scala.util.boundary, boundary.break

/**
 * Processes destructuring patterns for pattern matching.
 *
 * Handles record pattern matching like `case Point(x, y)` where:
 * - The pattern type is verified to be a record
 * - Field bindings are extracted with their access paths
 * - Nested destructuring patterns are supported to arbitrary depth
 */
private[typing] class DestructuringPatternProcessor(private val typing: Typing) {
  import typing.*

  /**
   * Find the getter method for a field in a record type.
   * Records expose fields as zero-argument methods.
   */
  def findFieldGetter(classDef: ClassDefinition, fieldName: String): Option[Method] = {
    val getters = classDef.methods(fieldName)
    if (getters.isEmpty) None
    else Some(getters.find(m => m.arguments.isEmpty).getOrElse(getters.head))
  }

  /**
   * Process a destructuring pattern.
   *
   * @param dp The destructuring pattern AST node
   * @param bind The binding for the matched value
   * @param context The local context (for error reporting)
   * @return Some((instanceOfCheck, bindingInfo)) on success, None on error
   */
  def process(
    dp: AST.DestructuringPattern,
    bind: ClosureLocalBinding,
    context: LocalContext
  ): Option[(Term, PatternBindingInfo)] = boundary {
    val AST.DestructuringPattern(_, constructor, fieldPatterns) = dp

    def resolveRecordClass(node: AST.Node, name: String): ClassDefinition = {
      val recordType = load(name)
      recordType match {
        case null =>
          report(NOT_A_RECORD_TYPE, node, name)
          break(None)
        case classDef: ClassDefinition =>
          classDef
        case _ =>
          report(NOT_A_RECORD_TYPE, node, name)
          break(None)
      }
    }

    // Recursive helper to process nested field patterns at any depth
    def processNestedFieldPattern(
      fieldPattern: AST.Pattern,
      fieldType: Type,
      currentPath: List[AccessStep],
      bindingEntries: scala.collection.mutable.ArrayBuffer[BindingEntry],
      nestedConditions: scala.collection.mutable.ArrayBuffer[Term],
      parentNode: AST.Node
    ): Unit = fieldPattern match {
      case AST.WildcardPattern(_) =>
        // Skip wildcard - no binding created

      case AST.BindingPattern(_, bindingName) =>
        // Simple binding
        bindingEntries += BindingEntry(bindingName, fieldType, currentPath)

      case nested @ AST.DestructuringPattern(_, ctorName, fieldPats) =>
        // Nested destructuring pattern - recurse to any depth
        val nestedClassDef = resolveRecordClass(nested, ctorName)
        val nestedType = nestedClassDef

        val nestedFields = nestedClassDef.fields
        if (fieldPats.length != nestedFields.length) {
          report(WRONG_BINDING_COUNT, nested, Int.box(nestedFields.length), Int.box(fieldPats.length), ctorName)
          break(None)
        }

        // Build accessor for nested type check by following the access path
        def buildAccessorForCondition(base: Term, path: List[AccessStep]): Term = path match {
          case Nil => base
          case AccessStep(castType, getter) :: rest =>
            val cast = new AsInstanceOf(base, castType)
            if (getter == null) buildAccessorForCondition(cast, rest)
            else {
              val call = new Call(cast, getter, Array.empty)
              buildAccessorForCondition(call, rest)
            }
        }

        val fieldAccess = buildAccessorForCondition(new RefLocal(bind), currentPath)
        val nestedInstanceOf = new InstanceOf(fieldAccess, nestedType.asInstanceOf[ObjectType])
        nestedConditions += nestedInstanceOf

        // Process nested field patterns recursively
        for ((nestedFieldPat, nestedField) <- fieldPats.zip(nestedFields)) {
          val nestedGetter = findFieldGetter(nestedClassDef, nestedField.name).getOrElse {
            report(NOT_A_RECORD_TYPE, nested, ctorName)
            break(None)
          }
          val nestedPath = currentPath :+ AccessStep(nestedType.asInstanceOf[ClassType], nestedGetter)
          processNestedFieldPattern(nestedFieldPat, nestedField.`type`, nestedPath, bindingEntries, nestedConditions, nested)
        }

      case other =>
        // Other patterns not supported in destructuring position
        report(NOT_A_RECORD_TYPE, parentNode, s"unsupported pattern type in destructuring: ${other.getClass.getSimpleName}")
        break(None)
    }

    // Look up the record type by constructor name
    val classDef = resolveRecordClass(dp, constructor)
    val recordType = classDef

    // Get fields in order (records store fields in insertion order)
    val fields = classDef.fields
    val fieldCount = fields.length

    // Check binding count matches field count
    if (fieldPatterns.length != fieldCount) {
      report(WRONG_BINDING_COUNT, dp, Int.box(fieldCount), Int.box(fieldPatterns.length), constructor)
      break(None)
    }

    // Create instanceof check
    val instanceOfCheck = new InstanceOf(new RefLocal(bind), recordType.asInstanceOf[ObjectType])

    // Process each field pattern recursively
    val bindingEntries = scala.collection.mutable.ArrayBuffer[BindingEntry]()
    val nestedConditions = scala.collection.mutable.ArrayBuffer[Term]()

    for ((fieldPattern, field) <- fieldPatterns.zip(fields)) {
      val getter = findFieldGetter(classDef, field.name).getOrElse {
        report(NOT_A_RECORD_TYPE, dp, constructor)
        break(None)
      }
      val currentPath = List(AccessStep(recordType.asInstanceOf[ClassType], getter))
      processNestedFieldPattern(fieldPattern, field.`type`, currentPath, bindingEntries, nestedConditions, dp)
    }

    val bindingInfo = MultiBindings(recordType.asInstanceOf[ClassType], bindingEntries.toList, nestedConditions.toList)
    Some((instanceOfCheck, bindingInfo))
  }
}

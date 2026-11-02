package onion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Written by the compiler on a method that carries contract clauses (RFC #2557), so that code
 * compiled later, against this class file, can read them. Each clause is source text; a clause the
 * compiler could not write as text that reads back as the same clause is left out.
 *
 * <p>It is visible to the compiler (it is in the class file) and invisible to reflection at run
 * time, like the other markers: a contract is not a behaviour of the running program.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Contract {
    /** The parameter names the clauses are written over, in order: a built module has no other record of them. */
    String[] params() default {};
    String[] requires() default {};
    String[] ensures() default {};
}

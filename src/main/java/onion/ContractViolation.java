package onion;

/**
 * Thrown when a contract guard fails at run time (RFC #2557, design 7.4, {@code --contracts runtime}).
 *
 * It is an {@link AssertionError}, so a violation can be told from an ordinary exception, and it
 * has no error code of its own: the message names the clause, the method and where it was written.
 */
public class ContractViolation extends AssertionError {
    public ContractViolation(String message) {
        super(message);
    }
}

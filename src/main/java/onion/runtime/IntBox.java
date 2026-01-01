package onion.runtime;

/**
 * Box for mutable int variables captured by closures.
 * This allows closures to share the same mutable variable with their enclosing scope.
 */
public class IntBox {
    public int value;

    public IntBox(int value) {
        this.value = value;
    }
}

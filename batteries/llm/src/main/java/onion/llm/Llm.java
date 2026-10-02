package onion.llm;

/**
 * Entry point of the {@code onion-llm} battery.
 *
 * <pre>
 * //&gt; using dep "org.onion_lang:onion-llm:&lt;version&gt;"
 * import { onion.llm.Llm; onion.llm.LlmError }
 *
 * val claude = Llm::claude().effort("low").system("You summarize meetings.")
 * val r: Result[Summary, LlmError] = claude.ask(Summary::doc(), "Summarize: " + notes)
 * </pre>
 *
 * Building a client is effect-free; only {@link Claude#ask} and {@link Claude#text} touch
 * the network.
 */
public final class Llm {
    private Llm() {}

    /**
     * A Claude client with the defaults: model {@value Claude#DEFAULT_MODEL}, effort
     * {@value Claude#DEFAULT_EFFORT}, {@value Claude#DEFAULT_MAX_TOKENS} max tokens, the
     * server-side refusal fallback on, credentials from the environment.
     */
    public static Claude claude() {
        return Claude.defaults();
    }

    /** {@link #claude()} with another model, e.g. {@code Llm::claude("claude-sonnet-5-5")}. */
    public static Claude claude(String model) {
        return Claude.defaults().model(model);
    }
}

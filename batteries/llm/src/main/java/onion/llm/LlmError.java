package onion.llm;

import java.util.List;
import onion.Defect;

/**
 * Why a {@link Claude} call produced no value. A plain value: it comes back in an
 * {@code Err}, never thrown. The interface is sealed, so an Onion {@code select} over the
 * five cases is checked for exhaustiveness and needs no {@code else}:
 *
 * <pre>
 * select err.error() {
 *   case e is LlmError.Refusal:   println("declined: " + e.category())
 *   case e is LlmError.Invalid:   foreach d: Defect in e.defects() { println(d.describe()) }
 *   case e is LlmError.Truncated: println("output cut at max_tokens")
 *   case e is LlmError.Api:       println("api " + e.status() + ": " + e.message())
 *   case e is LlmError.Transport: println("network: " + e.message())
 * }
 * </pre>
 *
 * The cases split along "what to do next": {@link Transport} and a {@link Api#retryable()
 * retryable} {@link Api} are worth retrying later; a {@link Refusal} is the model (or a
 * safety classifier) declining; {@link Truncated} asks for a larger {@code maxTokens};
 * {@link Invalid} means the answer arrived but does not fit the shape.
 */
public sealed interface LlmError
        permits LlmError.Transport, LlmError.Api, LlmError.Refusal, LlmError.Truncated, LlmError.Invalid {

    /** A one-line, human-readable account of the failure. */
    String describe();

    /**
     * No HTTP response was obtained: the connection failed or broke, the response could not
     * be read, or a configured credential source (such as an {@code ant auth login}
     * profile) could not be resolved, so the request was never sent. (No credentials at
     * all is not this case: the request goes out unauthenticated and comes back as
     * {@link Api} 401.)
     *
     * @param message what went wrong (never null)
     * @param cause   the underlying exception, or null
     */
    record Transport(String message, Throwable cause) implements LlmError {
        @Override
        public String describe() {
            return "transport: " + message;
        }
    }

    /**
     * The API answered with an error status.
     *
     * @param status    the HTTP status (400, 401, 403, 404, 413, 429, 500, 529, ...)
     * @param errorType the API's error type, e.g. {@code "invalid_request_error"},
     *                  {@code "rate_limit_error"}, {@code "overloaded_error"}; or
     *                  {@code "unknown"} when the body carried none. (Not named {@code type}:
     *                  that is a reserved word in Onion, so {@code e.type()} would not parse.)
     * @param message   the API's error message
     * @param retryable whether trying again later can succeed (429 and 5xx). The SDK has
     *                  already retried such a request with backoff before reporting it
     */
    record Api(int status, String errorType, String message, boolean retryable) implements LlmError {
        @Override
        public String describe() {
            return "api " + status + " " + errorType + ": " + message;
        }
    }

    /**
     * The model, or a safety classifier, declined the request ({@code stop_reason:
     * "refusal"}). With the server-side fallback on (the default), this means every model
     * in the fallback chain declined.
     *
     * @param category    the refusal category from {@code stop_details}, such as
     *                    {@code "cyber"} or {@code "bio"}; null when the API gave none
     * @param explanation the API's explanation; null when it gave none
     */
    record Refusal(String category, String explanation) implements LlmError {
        @Override
        public String describe() {
            String what = "refused" + (category == null ? "" : " (" + category + ")");
            return explanation == null ? what : what + ": " + explanation;
        }
    }

    /**
     * The output was cut off before the model finished: {@code stop_reason} was
     * {@code "max_tokens"} (raise {@link Claude#maxTokens(int)}) or
     * {@code "model_context_window_exceeded"}.
     *
     * @param stopReason  the stop reason as the API spelled it
     * @param partialText the text produced before the cut (possibly empty, never null)
     */
    record Truncated(String stopReason, String partialText) implements LlmError {
        @Override
        public String describe() {
            return "truncated: stop_reason " + stopReason;
        }
    }

    /**
     * The model answered, but its answer does not read as the requested shape.
     *
     * @param defects every reason it does not, with paths such as {@code actions[2].owner}
     * @param rawText the model's answer, verbatim
     */
    record Invalid(List<Defect> defects, String rawText) implements LlmError {
        public Invalid {
            defects = List.copyOf(defects);
        }

        @Override
        public String describe() {
            StringBuilder b = new StringBuilder("invalid answer:");
            for (Defect d : defects) b.append("\n  ").append(d.describe());
            return b.toString();
        }
    }
}

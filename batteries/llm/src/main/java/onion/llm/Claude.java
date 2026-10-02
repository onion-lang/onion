package onion.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.CredentialResolutionException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NoCredentialsException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.ErrorType;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaJsonOutputFormat;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaRefusalStopDetails;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import onion.Outcome;
import onion.Result;
import onion.Shape;

/**
 * An immutable Claude client description, made by {@link Llm#claude()}. Every builder
 * method returns a new {@code Claude} and leaves the receiver unchanged, so a base client
 * (say, with a system prompt) can be shared and specialised. Building is effect-free; only
 * {@link #ask} and {@link #text} call the API.
 *
 * <p>Each call goes to the Messages API (beta endpoint, non-streaming) and checks the
 * response in this order: {@code stop_reason} {@code "refusal"} is {@link LlmError.Refusal};
 * {@code "max_tokens"} (or {@code "model_context_window_exceeded"}) is
 * {@link LlmError.Truncated}; otherwise the answer is the text of the text blocks,
 * thinking blocks skipped. HTTP errors are {@link LlmError.Api}, no response at all is
 * {@link LlmError.Transport}. The SDK's default retries (with backoff, for 429 and 5xx
 * and connection failures) run before any of these is reported.
 *
 * <p>Credentials resolve the way {@code AnthropicOkHttpClient.fromEnv()} resolves them:
 * {@code ANTHROPIC_API_KEY} (or {@code ANTHROPIC_AUTH_TOKEN}), else an {@code ant auth
 * login} profile. {@code ANTHROPIC_BASE_URL} is honoured too; {@link #baseUrl} overrides it.
 * With no credentials at all the SDK still sends the request, unauthenticated, and the
 * API's answer comes back as {@link LlmError.Api} 401 {@code authentication_error}.
 */
public final class Claude {

    /** The model a client starts with. */
    public static final String DEFAULT_MODEL = "claude-opus-5-5";
    /** The {@code output_config.effort} a client starts with. */
    public static final String DEFAULT_EFFORT = "medium";
    /** The {@code max_tokens} a client starts with. */
    public static final int DEFAULT_MAX_TOKENS = 16000;
    /** The beta header that gates the {@code fallbacks: "default"} request parameter. */
    public static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private static final List<String> EFFORTS = List.of("low", "medium", "high", "xhigh", "max");

    private final String model;
    private final String effort;
    private final int maxTokens;
    private final String system;     // null: no system prompt
    private final boolean fallbacks;
    private final String baseUrl;    // null: from the environment, else the SDK default
    private final String apiKey;     // null: from the environment / profile

    private Claude(String model, String effort, int maxTokens, String system,
                   boolean fallbacks, String baseUrl, String apiKey) {
        this.model = model;
        this.effort = effort;
        this.maxTokens = maxTokens;
        this.system = system;
        this.fallbacks = fallbacks;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    static Claude defaults() {
        return new Claude(DEFAULT_MODEL, DEFAULT_EFFORT, DEFAULT_MAX_TOKENS, null, true, null, null);
    }

    // ------------------------------------------------------------------ builder steps

    /**
     * Uses another model, by its API id (e.g. {@code "claude-sonnet-5-5"}).
     *
     * @throws IllegalArgumentException if the id is null or blank
     */
    public Claude model(String model) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Claude.model: model id is null or blank");
        }
        return new Claude(model, effort, maxTokens, system, fallbacks, baseUrl, apiKey);
    }

    /**
     * Sets {@code output_config.effort}: {@code "low"}, {@code "medium"} (the default),
     * {@code "high"}, {@code "xhigh"} or {@code "max"}. Thinking depth follows effort; the
     * battery never sends a {@code thinking} parameter (Claude Opus 5.5 rejects disabling
     * it or giving it a token budget).
     *
     * @throws IllegalArgumentException for any other value
     */
    public Claude effort(String effort) {
        String e = effort == null ? null : effort.trim().toLowerCase(Locale.ROOT);
        if (e == null || !EFFORTS.contains(e)) {
            throw new IllegalArgumentException(
                    "Claude.effort: expected one of " + EFFORTS + ", got " + (effort == null ? "null" : "'" + effort + "'"));
        }
        return new Claude(model, e, maxTokens, system, fallbacks, baseUrl, apiKey);
    }

    /**
     * Sets {@code max_tokens}, the cap on the output (thinking included). A response that
     * reaches it is {@link LlmError.Truncated}.
     *
     * @throws IllegalArgumentException if not positive
     */
    public Claude maxTokens(int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("Claude.maxTokens: must be positive, got " + maxTokens);
        }
        return new Claude(model, effort, maxTokens, system, fallbacks, baseUrl, apiKey);
    }

    /** Sets the system prompt; {@code null} removes it. */
    public Claude system(String system) {
        return new Claude(model, effort, maxTokens, system, fallbacks, baseUrl, apiKey);
    }

    /**
     * Turns the server-side refusal fallback on (the default) or off. When on, the request
     * carries {@code fallbacks: "default"} with the {@value #FALLBACK_BETA} beta header, so
     * a request a safety classifier declines is re-run server-side on Anthropic's
     * recommended fallback model instead of coming back as a refusal.
     */
    public Claude fallbacks(boolean enabled) {
        return new Claude(model, effort, maxTokens, system, enabled, baseUrl, apiKey);
    }

    /**
     * Sends requests to another base URL (a proxy, a gateway, or a local fake in tests)
     * instead of {@code https://api.anthropic.com}.
     *
     * @throws IllegalArgumentException if it is not an absolute http or https URL
     */
    public Claude baseUrl(String url) {
        if (url == null) throw new IllegalArgumentException("Claude.baseUrl: url is null");
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Claude.baseUrl: not a URL: " + url, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
            throw new IllegalArgumentException("Claude.baseUrl: expected an absolute http(s) URL, got " + url);
        }
        return new Claude(model, effort, maxTokens, system, fallbacks, url, apiKey);
    }

    /**
     * Uses this API key instead of resolving credentials from the environment. Prefer the
     * environment ({@code ANTHROPIC_API_KEY} or {@code ant auth login}): a key in a script
     * is a key in version control.
     *
     * @throws IllegalArgumentException if the key is null or blank
     */
    public Claude apiKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Claude.apiKey: key is null or blank");
        }
        return new Claude(model, effort, maxTokens, system, fallbacks, baseUrl, key);
    }

    // ------------------------------------------------------------------ accessors

    /** The model id requests are sent to. */
    public String model() {
        return model;
    }

    /** The {@code output_config.effort} sent with each request. */
    public String effort() {
        return effort;
    }

    /** The {@code max_tokens} sent with each request. */
    public int maxTokens() {
        return maxTokens;
    }

    /** Whether the server-side refusal fallback is requested. */
    public boolean fallbacksEnabled() {
        return fallbacks;
    }

    // ------------------------------------------------------------------ calls

    /**
     * Asks for an answer of the given shape: the shape's JSON Schema is sent as the
     * structured-output format ({@code output_config.format}), and the answer is read
     * back with {@code shape.parse}. An answer that does not read is
     * {@link LlmError.Invalid}, carrying every defect and the raw text.
     *
     * @throws IllegalArgumentException if the shape has no JSON Schema (only a
     *         {@code shape name = json} shape has one), or the prompt is null — both
     *         programming errors, reported where they were written, before any request
     */
    public <T> Result<T, LlmError> ask(Shape<T> shape, String prompt) {
        if (shape == null) throw new IllegalArgumentException("Claude.ask: shape is null");
        if (!shape.hasJsonSchema()) {
            throw new IllegalArgumentException("Claude.ask: shape " + shape.describe()
                    + " has no JSON Schema; declare it as `shape name = json`");
        }
        requirePrompt("ask", prompt);
        Map<String, JsonValue> schema = schemaOf(shape.jsonSchema());
        Result<String, LlmError> text = call(prompt, schema);
        if (text.isErr()) return Result.err(text.getError());
        String raw = text.get();
        Outcome<T> read = shape.parse(raw);
        if (read.isOk()) return Result.ok(read.get());
        return Result.err(new LlmError.Invalid(read.defects(), raw));
    }

    /** Asks for a free-text answer. */
    public Result<String, LlmError> text(String prompt) {
        requirePrompt("text", prompt);
        return call(prompt, null);
    }

    @Override
    public String toString() {
        return "Claude(model=" + model + ", effort=" + effort + ", maxTokens=" + maxTokens
                + ", fallbacks=" + (fallbacks ? "default" : "off")
                + (system == null ? "" : ", system=set")
                + (baseUrl == null ? "" : ", baseUrl=" + baseUrl) + ")";
    }

    // ------------------------------------------------------------------ internals

    private static void requirePrompt(String method, String prompt) {
        if (prompt == null) throw new IllegalArgumentException("Claude." + method + ": prompt is null");
    }

    /** The shape's schema text as the SDK's JSON values, key order kept. */
    private static Map<String, JsonValue> schemaOf(String json) {
        Map<String, Object> parsed;
        try {
            parsed = ObjectMappers.jsonMapper().readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Claude.ask: the shape's JSON Schema is not a JSON object: " + json, e);
        }
        Map<String, JsonValue> out = new LinkedHashMap<>();
        parsed.forEach((k, v) -> out.put(k, JsonValue.from(v)));
        return out;
    }

    MessageCreateParams params(String prompt, Map<String, JsonValue> schema) {
        BetaOutputConfig.Builder output = BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.of(effort));
        if (schema != null) {
            output.format(BetaJsonOutputFormat.builder()
                    .schema(BetaJsonOutputFormat.Schema.builder().putAllAdditionalProperties(schema).build())
                    .build());
        }
        MessageCreateParams.Builder p = MessageCreateParams.builder()
                .model(model)
                .maxTokens((long) maxTokens)
                .outputConfig(output.build())
                .addUserMessage(prompt);
        if (system != null) p.system(system);
        if (fallbacks) {
            p.addBeta(FALLBACK_BETA);
            p.fallbacksDefault();
        }
        return p.build();
    }

    /**
     * One SDK client per (base URL, explicit key), shared for the life of the JVM, as the
     * SDK intends: it keeps a connection pool, so later calls reuse the TLS/HTTP/2
     * connection. Never closed, on purpose: closing evicts the pooled HTTP/2 connection,
     * and OkHttp finishes that shutdown on a background thread that may still be loading
     * classes after an Onion script has returned and its class loader has been closed
     * (a NoClassDefFoundError on stderr at exit). OkHttp's own threads are daemons, so an
     * open client does not keep the JVM alive.
     */
    private static final ConcurrentHashMap<List<String>, AnthropicClient> CLIENTS = new ConcurrentHashMap<>();

    private AnthropicClient client() {
        return CLIENTS.computeIfAbsent(Arrays.asList(baseUrl, apiKey), key -> {
            AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder().fromEnv();
            if (baseUrl != null) b.baseUrl(baseUrl);
            if (apiKey != null) b.apiKey(apiKey);
            return b.build();
        });
    }

    /** One request: the answer's text, or why there is none. */
    private Result<String, LlmError> call(String prompt, Map<String, JsonValue> schema) {
        MessageCreateParams params = params(prompt, schema);
        try {
            return read(client().beta().messages().create(params));
        } catch (BadRequestException | UnauthorizedException | PermissionDeniedException
                 | NotFoundException | UnprocessableEntityException e) {
            return Result.err(api(e, false));                    // 4xx: fix the request, do not retry
        } catch (RateLimitException | InternalServerException e) {
            return Result.err(api(e, true));                     // 429, 5xx: already retried by the SDK
        } catch (AnthropicServiceException e) {
            return Result.err(api(e, retryableStatus(e.statusCode())));  // any other status (413, 529, ...)
        } catch (AnthropicIoException e) {
            return Result.err(transport("connection failed", e));
        } catch (AnthropicRetryableException e) {
            return Result.err(transport("request failed", e));
        } catch (NoCredentialsException | CredentialResolutionException e) {
            return Result.err(transport(
                    "no credentials: set ANTHROPIC_API_KEY or run `ant auth login`", e));
        } catch (AnthropicInvalidDataException e) {
            return Result.err(transport("unreadable response", e));
        } catch (AnthropicException e) {
            return Result.err(transport("client error", e));
        }
    }

    /** Reads a response: stop_reason first, content only after. */
    private static Result<String, LlmError> read(BetaMessage message) {
        String stop = message.stopReason().map(BetaStopReason::asString).orElse("");
        if (stop.equals("refusal")) {
            String category = null;
            String explanation = null;
            if (message.stopDetails().isPresent()) {
                BetaRefusalStopDetails d = message.stopDetails().get();
                category = d.category().map(BetaRefusalStopDetails.Category::asString).orElse(null);
                explanation = d.explanation().orElse(null);
            }
            return Result.err(new LlmError.Refusal(category, explanation));
        }
        String text = textOf(message);
        if (stop.equals("max_tokens") || stop.equals("model_context_window_exceeded")) {
            return Result.err(new LlmError.Truncated(stop, text));
        }
        return Result.ok(text);
    }

    /** The concatenated text blocks; thinking (and any other) blocks are skipped. */
    private static String textOf(BetaMessage message) {
        StringBuilder b = new StringBuilder();
        for (BetaContentBlock block : message.content()) {
            block.text().ifPresent(t -> b.append(t.text()));
        }
        return b.toString();
    }

    private static boolean retryableStatus(int status) {
        return status == 429 || status >= 500;
    }

    private static LlmError.Api api(AnthropicServiceException e, boolean retryable) {
        Map<?, ?> error = errorObject(e);
        String type = e.errorType().map(ErrorType::asString)
                .orElseGet(() -> error != null && error.get("type") instanceof String s ? s : "unknown");
        String message = error != null && error.get("message") instanceof String s ? s
                : (e.getMessage() == null ? "HTTP " + e.statusCode() : e.getMessage());
        return new LlmError.Api(e.statusCode(), type, message, retryable);
    }

    /** The {@code error} object of an API error body, or null if the body has none. */
    private static Map<?, ?> errorObject(AnthropicServiceException e) {
        try {
            Object body = e.body().convert(Object.class);
            if (body instanceof Map<?, ?> top && top.get("error") instanceof Map<?, ?> err) return err;
        } catch (RuntimeException ignored) {
            // a non-JSON body: fall back to the exception's own message
        }
        return null;
    }

    private static LlmError.Transport transport(String what, Throwable e) {
        String detail = e.getMessage();
        for (Throwable t = e.getCause(); (detail == null || detail.isEmpty()) && t != null; t = t.getCause()) {
            detail = t.getMessage();
        }
        return new LlmError.Transport(detail == null || detail.isEmpty() ? what : what + ": " + detail, e);
    }
}

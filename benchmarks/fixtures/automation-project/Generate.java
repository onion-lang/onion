import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class Generate {
    private Generate() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0])
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(root);
        for (int stage = 1; stage <= 19; stage++) {
            String className = String.format(Locale.ROOT, "Stage%02d", stage);
            StringBuilder source = new StringBuilder();
            source.append("module readiness.automation\n\n");
            source.append("class ").append(className).append(" {\n");
            source.append("public:\n");
            for (int method = 1; method <= 32; method++) {
                source.append("  def step")
                    .append(String.format(Locale.ROOT, "%02d", method))
                    .append("(value: Int): Int {\n");
                source.append("    return value + ")
                    .append(stage + method)
                    .append("\n");
                source.append("  }\n");
            }
            source.append("}\n");
            Files.writeString(
                root.resolve(className + ".on"),
                source,
                StandardCharsets.UTF_8
            );
        }

        StringBuilder pipeline = new StringBuilder();
        pipeline.append("module readiness.automation\n\n");
        pipeline.append("class AutomationPipeline {\npublic:\n");
        pipeline.append("  def run(value: Int): Int {\n");
        pipeline.append("    var current = value\n");
        for (int stage = 1; stage <= 19; stage++) {
            pipeline.append("    current = new ")
                .append(String.format(Locale.ROOT, "Stage%02d", stage))
                .append("().step01(current)\n");
        }
        pipeline.append("    return current\n  }\n");
        for (int method = 1; method <= 24; method++) {
            pipeline.append("  def normalize")
                .append(String.format(Locale.ROOT, "%02d", method))
                .append("(value: Int): Int {\n");
            pipeline.append("    return value - ")
                .append(method)
                .append("\n");
            pipeline.append("  }\n");
        }
        pipeline.append("}\n");
        Files.writeString(
            root.resolve("Pipeline.on"),
            pipeline,
            StandardCharsets.UTF_8
        );
    }
}

package com.prompt2app.eval;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads {@link EvalCase} instances from {@code eval/cases/*.yaml}.
 *
 * <p>Uses SnakeYAML (transitively from Spring Boot Starter, no extra dep).
 * Snake_case YAML keys (e.g. {@code expected_strategy}) are mapped to
 * camelCase Java fields (e.g. {@code expectedStrategy}) by a custom
 * {@code PropertyUtils}.
 */
public class EvalCaseLoader {

    private final Yaml yaml = createYaml();

    /** Load a single case from a YAML file. */
    public EvalCase load(Path yamlFile) {
        try (InputStream is = Files.newInputStream(yamlFile)) {
            return yaml.loadAs(is, EvalCase.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load case: " + yamlFile, e);
        }
    }

    /** Load all {@code *.yaml} cases from a directory, sorted by file name. */
    public List<EvalCase> loadAll(Path casesDir) {
        if (!Files.isDirectory(casesDir)) {
            throw new IllegalArgumentException("Not a directory: " + casesDir);
        }
        try (Stream<Path> files = Files.list(casesDir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .sorted()
                    .map(this::load)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list cases dir: " + casesDir, e);
        }
    }

    private static Yaml createYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Constructor constructor = new Constructor(EvalCase.class, options);

        PropertyUtils propertyUtils = new PropertyUtils() {
            @Override
            public Property getProperty(Class<?> type, String name) {
                if (name.indexOf('_') >= 0) {
                    name = toCamelCase(name);
                }
                return super.getProperty(type, name);
            }
        };
        propertyUtils.setSkipMissingProperties(true);
        constructor.setPropertyUtils(propertyUtils);

        return new Yaml(constructor);
    }

    private static String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder(snake.length());
        boolean nextUpper = false;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

package com.fsad.mutualfund.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.StringUtils;

public class LocalEnvPs1EnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LocalEnvPs1EnvironmentPostProcessor.class);
    private static final String PROPERTY_SOURCE_NAME = "localEnvPs1";
    private static final Pattern ENV_ASSIGNMENT =
            Pattern.compile("^\\s*\\$env:([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+?)\\s*$");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }

        Path localEnvFile = resolveLocalEnvFile();
        if (localEnvFile == null) {
            return;
        }

        Map<String, Object> values = loadEntries(localEnvFile);
        if (values.isEmpty()) {
            return;
        }

        boolean explicitProfilesAlreadySet =
                StringUtils.hasText(System.getProperty("spring.profiles.active"))
                        || StringUtils.hasText(System.getenv("SPRING_PROFILES_ACTIVE"))
                        || environment.getActiveProfiles().length > 0;

        MutablePropertySources propertySources = environment.getPropertySources();
        SystemEnvironmentPropertySource propertySource =
                new SystemEnvironmentPropertySource(PROPERTY_SOURCE_NAME, values);

        if (propertySources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            propertySources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
        } else {
            propertySources.addLast(propertySource);
        }

        if (!explicitProfilesAlreadySet) {
            applyActiveProfiles(environment, values.get("SPRING_PROFILES_ACTIVE"));
        }

        log.info("Loaded {} local environment entries from {}", values.size(), localEnvFile.toAbsolutePath());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private Path resolveLocalEnvFile() {
        List<Path> candidates = List.of(
                Paths.get("local-env.ps1"),
                Paths.get("backend", "local-env.ps1")
        );

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private Map<String, Object> loadEntries(Path localEnvFile) {
        Map<String, Object> values = new LinkedHashMap<>();

        try {
            for (String rawLine : Files.readAllLines(localEnvFile)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                Matcher matcher = ENV_ASSIGNMENT.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }

                String key = matcher.group(1).trim();
                String value = normalizeValue(matcher.group(2));
                if (!StringUtils.hasText(key) || value == null) {
                    continue;
                }

                values.put(key, value);
            }
        } catch (IOException ex) {
            log.warn("Unable to read local env file at {}", localEnvFile.toAbsolutePath(), ex);
        }

        return values;
    }

    private String normalizeValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }

        return value
                .replace("`\"", "\"")
                .replace("`'", "'")
                .trim();
    }

    private void applyActiveProfiles(ConfigurableEnvironment environment, Object configuredProfiles) {
        if (!(configuredProfiles instanceof String profilesValue) || !StringUtils.hasText(profilesValue)) {
            return;
        }

        String[] profiles = Arrays.stream(profilesValue.split(","))
                .map(profile -> profile == null ? "" : profile.trim())
                .filter(StringUtils::hasText)
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .toArray(String[]::new);

        if (profiles.length > 0) {
            environment.setActiveProfiles(profiles);
        }
    }
}

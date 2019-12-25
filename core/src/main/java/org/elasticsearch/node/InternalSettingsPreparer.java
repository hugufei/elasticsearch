/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsException;
import org.elasticsearch.env.Environment;

import static org.elasticsearch.common.Strings.cleanPath;

/**
 *
 */
public class InternalSettingsPreparer {

    private static final String[] ALLOWED_SUFFIXES = {".yml", ".yaml", ".json"};

    public static final String SECRET_PROMPT_VALUE = "${prompt.secret}";
    public static final String TEXT_PROMPT_VALUE = "${prompt.text}";

    /**
     * Prepares the settings by gathering all elasticsearch system properties and setting defaults.
     */
    public static Settings prepareSettings(Settings input) {
        Settings.Builder output = Settings.builder();
        initializeSettings(output, input, Collections.emptyMap());
        finalizeSettings(output, null);
        return output.build();
    }

    /**
     * Prepares the settings by gathering all elasticsearch system properties, optionally loading the configuration settings,
     * and then replacing all property placeholders. If a {@link Terminal} is provided and configuration settings are loaded,
     * settings with a value of <code>${prompt.text}</code> or <code>${prompt.secret}</code> will result in a prompt for
     * the setting to the user.
     * @param input The custom settings to use. These are not overwritten by settings in the configuration file.
     * @param terminal the Terminal to use for input/output
     * @return the {@link Settings} and {@link Environment} as a {@link Tuple}
     */
    public static Environment prepareEnvironment(Settings input, Terminal terminal) {
        return prepareEnvironment(input, terminal, Collections.emptyMap());
    }

    /**
     * Prepares the settings by gathering all elasticsearch system properties, optionally loading the configuration settings,
     * and then replacing all property placeholders. If a {@link Terminal} is provided and configuration settings are loaded,
     * settings with a value of <code>${prompt.text}</code> or <code>${prompt.secret}</code> will result in a prompt for
     * the setting to the user.
     * @param input The custom settings to use. These are not overwritten by settings in the configuration file.
     * @param terminal the Terminal to use for input/output
     * @param properties Map of properties key/value pairs (usually from the command-line)
     * @return the {@link Settings} and {@link Environment} as a {@link Tuple}
     */
    // 加载配置文件，得到Environment
    public static Environment prepareEnvironment(Settings input, Terminal terminal, Map<String, String> properties) {
        // just create enough settings to build the environment, to get the config dir
        // 构建一个默认的Settings的实例
        Settings.Builder output = Settings.builder();
        initializeSettings(output, input, properties);
        Environment environment = new Environment(output.build());

        output = Settings.builder(); // start with a fresh output
        boolean settingsFileFound = false;
        Set<String> foundSuffixes = new HashSet<>();
        // 支持yml/yaml/json格式的配置文件
        for (String allowedSuffix : ALLOWED_SUFFIXES) {
            // 取得配置文件路径
            Path path = environment.configFile().resolve("elasticsearch" + allowedSuffix);
            if (Files.exists(path)) {
                if (!settingsFileFound) {
                    try {
                        // 加载给定或默认路径下的elasticsearch.yml
                        output.loadFromPath(path);
                    } catch (IOException e) {
                        throw new SettingsException("Failed to load settings from " + path.toString(), e);
                    }
                }
                settingsFileFound = true;
                foundSuffixes.add(allowedSuffix);
            }
        }
        //找到多个配置文件则报错
        if (foundSuffixes.size() > 1) {
            throw new SettingsException("multiple settings files found with suffixes: "
                + Strings.collectionToDelimitedString(foundSuffixes, ","));
        }

        // re-initialize settings now that the config file has been loaded
        // 既然配置文件已经加载，请重新初始化设置
        initializeSettings(output, input, properties);
        finalizeSettings(output, terminal);

        environment = new Environment(output.build());

        // we put back the path.logs so we can use it in the logging configuration file
        // 将日志文件的路径加载进Settings中，这样就保证了elasticsearch.yml文件中配置的日志路径path.logs生效
        output.put(Environment.PATH_LOGS_SETTING.getKey(), cleanPath(environment.logsFile().toAbsolutePath().toString()));
        return new Environment(output.build());
    }

    /**
     * Initializes the builder with the given input settings, and applies settings from the specified map (these settings typically come
     * from the command line).
     *
     * @param output the settings builder to apply the input and default settings to
     * @param input the input settings
     * @param esSettings a map from which to apply settings
     */
    static void initializeSettings(final Settings.Builder output, final Settings input, final Map<String, String> esSettings) {
        output.put(input);
        output.putProperties(esSettings, Function.identity());
        output.replacePropertyPlaceholders();
    }

    /**
     * Finish preparing settings by replacing forced settings, prompts, and any defaults that need to be added.
     * The provided terminal is used to prompt for settings needing to be replaced.
     */
    private static void finalizeSettings(Settings.Builder output, Terminal terminal) {
        // allow to force set properties based on configuration of the settings provided
        List<String> forcedSettings = new ArrayList<>();
        for (String setting : output.internalMap().keySet()) {
            if (setting.startsWith("force.")) {
                forcedSettings.add(setting);
            }
        }
        for (String forcedSetting : forcedSettings) {
            String value = output.remove(forcedSetting);
            output.put(forcedSetting.substring("force.".length()), value);
        }
        output.replacePropertyPlaceholders();

        // put the cluster name
        if (output.get(ClusterName.CLUSTER_NAME_SETTING.getKey()) == null) {
            output.put(ClusterName.CLUSTER_NAME_SETTING.getKey(), ClusterName.CLUSTER_NAME_SETTING.getDefault(Settings.EMPTY).value());
        }

        replacePromptPlaceholders(output, terminal);
    }

    private static void replacePromptPlaceholders(Settings.Builder settings, Terminal terminal) {
        List<String> secretToPrompt = new ArrayList<>();
        List<String> textToPrompt = new ArrayList<>();
        for (Map.Entry<String, String> entry : settings.internalMap().entrySet()) {
            switch (entry.getValue()) {
                case SECRET_PROMPT_VALUE:
                    secretToPrompt.add(entry.getKey());
                    break;
                case TEXT_PROMPT_VALUE:
                    textToPrompt.add(entry.getKey());
                    break;
            }
        }
        for (String setting : secretToPrompt) {
            String secretValue = promptForValue(setting, terminal, true);
            if (Strings.hasLength(secretValue)) {
                settings.put(setting, secretValue);
            } else {
                // TODO: why do we remove settings if prompt returns empty??
                settings.remove(setting);
            }
        }
        for (String setting : textToPrompt) {
            String textValue = promptForValue(setting, terminal, false);
            if (Strings.hasLength(textValue)) {
                settings.put(setting, textValue);
            } else {
                // TODO: why do we remove settings if prompt returns empty??
                settings.remove(setting);
            }
        }
    }

    private static String promptForValue(String key, Terminal terminal, boolean secret) {
        if (terminal == null) {
            throw new UnsupportedOperationException("found property [" + key + "] with value ["
                + (secret ? SECRET_PROMPT_VALUE : TEXT_PROMPT_VALUE)
                + "]. prompting for property values is only supported when running elasticsearch in the foreground");
        }

        if (secret) {
            return new String(terminal.readSecret("Enter value for [" + key + "]: "));
        }
        return terminal.readText("Enter value for [" + key + "]: ");
    }
}
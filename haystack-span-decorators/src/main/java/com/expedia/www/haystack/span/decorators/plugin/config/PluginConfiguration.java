package com.expedia.www.haystack.span.decorators.plugin.config;

import com.typesafe.config.Config;

public class PluginConfiguration {
    private String name;
    private String jarName;
    private Config config;

    public PluginConfiguration(String directory, String name, String jarName, Config config) {
        this.name = name;
        this.jarName = jarName;
        this.config = config;
    }

    public PluginConfiguration() {

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJarName() {
        return jarName;
    }

    public void setJarName(String jarName) {
        this.jarName = jarName;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }
}

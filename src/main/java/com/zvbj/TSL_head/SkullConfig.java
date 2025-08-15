package com.zvbj.TSL_head;

import java.util.List;

public class SkullConfig {
    private final String nameTemplate;
    private final List<String> loreTemplate;

    public SkullConfig(String nameTemplate, List<String> loreTemplate) {
        this.nameTemplate = nameTemplate;
        this.loreTemplate = loreTemplate;
    }

    public String getNameTemplate() {
        return nameTemplate;
    }

    public List<String> getLoreTemplate() {
        return loreTemplate;
    }
}
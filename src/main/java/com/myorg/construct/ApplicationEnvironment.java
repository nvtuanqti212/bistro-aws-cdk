package com.myorg.construct;

import software.amazon.awscdk.Tags;
import software.constructs.IConstruct;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/14/2024, Tuesday
 * @description:
 **/
public class ApplicationEnvironment {
    private final String applicationName;
    private final String environmentName;

    /*
    * Constructor.
    * Params:
    * * applicationName – the name of the application that you want to deploy.
    * * environmentName – the name of the environment the application shall be deployed into.
    * */
    public ApplicationEnvironment(String applicationName, String environmentName){
        this.applicationName = applicationName;
        this.environmentName = environmentName;
    }


    public String getApplicationName() {
        return applicationName;
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    private String sanitize(String environmentName) {
        return environmentName.replaceAll("[^a-zA-Z0-9-]", "");
    }

    @Override
    public String toString() {
        return sanitize(environmentName + "-" + applicationName);
    }

    /**
     * Prefixes a string with the application name and environment name.
     */
    public String prefix(String string) {
        return this + "-" + string;
    }

    /**
     * Prefixes a string with the application name and environment name. Returns only the last <code>characterLimit</code>
     * characters from the name.
     */
    public String prefix(String string, int characterLimit) {
        String name = this + "-" + string;
        if (name.length() <= characterLimit) {
            return name;
        }
        return name.substring(name.length() - characterLimit);
    }

    public void tag(IConstruct construct) {
        Tags.of(construct).add("environment", environmentName);
        Tags.of(construct).add("application", applicationName);
    }
}

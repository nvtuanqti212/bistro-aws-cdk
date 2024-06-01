package com.myorg;

import com.myorg.construct.ApplicationEnvironment;
import com.myorg.util.DataUtil;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;

import static com.myorg.constant.AWSParameter.ACCOUNT_ID;
import static com.myorg.util.AWSUtils.makeEnv;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 6/1/2024, Saturday
 * @description:
 **/
public class MessagingApp {
    public static void main(final String[] args){
        App app = new App();
        String environmentName = (String) app
                .getNode()
                .tryGetContext("environmentName");
        DataUtil.requireNonEmptyOrNull(environmentName, "context variable 'environmentName' must not be null");

        String region = (String) app
                .getNode()
                .tryGetContext("region");
        DataUtil.requireNonEmptyOrNull(region, "context variable 'region' must not be null");

        String applicationName = (String) app.getNode().tryGetContext("applicationName");
        DataUtil.requireNonEmptyOrNull(applicationName, "context variable 'applicationName' must not be null");

        Environment awsEnvironment = makeEnv(ACCOUNT_ID, region);

        ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(
                applicationName,
                environmentName
        );

        new MessagingStack(app, "messaging", awsEnvironment, applicationEnvironment);

        app.synth();
    }
}

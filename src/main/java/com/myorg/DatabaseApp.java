package com.myorg;

import com.myorg.construct.ApplicationEnvironment;
import com.myorg.construct.Database;
import com.myorg.util.AWSUtils;
import com.myorg.util.DataUtil;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import static com.myorg.constant.InputParameters.ACCOUNT_ID;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/23/2024, Thursday
 * @description:
 **/
public class DatabaseApp {
    public static void main(final String[] args) {
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

        Environment awsEnvironment = AWSUtils.makeEnv(ACCOUNT_ID, region);

        ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(
                applicationName,
                environmentName
        );

        Stack databaseStack = new Stack(
                app,
                "DatabaseStack",
                StackProps.builder()
                        .stackName(applicationEnvironment.prefix("Database"))
                        .env(awsEnvironment)
                        .build()
        );

        Database database = new Database(
                databaseStack,
                "Database",
                awsEnvironment,
                applicationEnvironment,
                new Database.DatabaseInputParameters()
        );

        app.synth();
    }
}

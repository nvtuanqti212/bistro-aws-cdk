package com.myorg;

import com.myorg.construct.ApplicationEnvironment;
import com.myorg.construct.DynamoDBTable;
import com.myorg.util.AWSUtils;
import com.myorg.util.DataUtil;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import static com.myorg.constant.InputParameters.ACCOUNT_ID;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/29/2024, Wednesday
 * @description:
 **/
public class DynamoDbApp {
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

        Environment awsEnvironment = AWSUtils.makeEnv(ACCOUNT_ID, region);

        ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(
                applicationName,
                environmentName
        );
        DataUtil.requireNonEmptyOrNull(applicationName, "context variable 'applicationName' must not be null");

        Stack dynamoDbStack = new Stack(app, "DynamoDbStack", StackProps.builder()
                .stackName(applicationEnvironment.prefix("DynamoDb"))
                .env(awsEnvironment)
                .build());

        new DynamoDBTable(
                dynamoDbStack,
                "BreadcrumbTable",
                applicationEnvironment,
                new DynamoDBTable.DynamoDBInputParameters("user_action")
        );

        app.synth();
    }
}

package com.myorg;

import com.myorg.construct.ApplicationEnvironment;
import com.myorg.construct.Database;
import com.myorg.construct.Network;
import com.myorg.construct.Service;
import com.myorg.util.AWSUtils;
import com.myorg.util.DataUtil;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.constructs.Construct;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.myorg.constant.InputParameters.ACCOUNT_ID;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/22/2024, Wednesday
 * @description:
 **/
public class ServiceApp {
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

        String springProfile = (String) app.getNode().tryGetContext("springProfile");
        DataUtil.requireNonEmptyOrNull(springProfile, "context variable 'springProfile' must not be null");

        String dockerRepositoryName = (String) app.getNode().tryGetContext("dockerRepositoryName");
        DataUtil.requireNonEmptyOrNull(dockerRepositoryName, "context variable 'dockerRepositoryName' must not be null");

        String dockerImageTag = (String) app.getNode().tryGetContext("dockerImageTag");
        DataUtil.requireNonEmptyOrNull(dockerImageTag, "context variable 'dockerImageTag' must not be null");

        Environment awsEnvironment = AWSUtils.makeEnv(ACCOUNT_ID, region);

        ApplicationEnvironment applicationEnvironment = new ApplicationEnvironment(
                applicationName,
                environmentName
        );

        long timestamp = System.currentTimeMillis();
        Stack parametersStack = new Stack(app, "ServiceParameters-" + timestamp, StackProps.builder()
                .stackName(applicationEnvironment.prefix("Service-Parameters-" + timestamp))
                .env(awsEnvironment)
                .build());

        Stack serviceStack = new Stack(
                app,
                "ServiceStack",
                StackProps.builder()
                        .stackName(applicationEnvironment.prefix("Service"))
                        .env(awsEnvironment)
                        .build()
        );

        Service.DockerImageSource dockerImageSource =
                new Service.DockerImageSource(dockerRepositoryName, dockerImageTag);

        Network.NetworkOutputParameters networkOutputParameters = Network.getOutputParametersFromParameterStore(
                serviceStack, applicationEnvironment.getEnvironmentName()
        );

        Database.DatabaseOutputParameters databaseOutputParameters = Database.getOutputParametersFromParameterStore(parametersStack, applicationEnvironment);
        MessagingStack.MessagingOutputParameters messagingOutputParameters = MessagingStack.getOutputParametersFromParameterStore(parametersStack, applicationEnvironment);

        Service.ServiceInputParameters serviceInputParameters = new Service.ServiceInputParameters(
                dockerImageSource,
                Collections.singletonList(databaseOutputParameters.getDatabaseSecurityGroupId()),
                environmentVariables(serviceStack, springProfile, databaseOutputParameters)
        ).withHealthCheckIntervalSeconds(20)
                .withHealthCheckTimeoutSeconds(15)
                .withUnhealthyThresholdCount(3)
                .withHealthyThresholdCount(2)
                .withHealthCheckPath("/actuator")
                .withTaskRolePolicyStatements(
                        List.of(
                                PolicyStatement.Builder.create()
                                        .sid("AllowDynamoTableAccess")
                                        .effect(Effect.ALLOW)
                                        .resources(
                                                List.of(String.format("arn:aws:dynamodb:%s:%s:table/%s", region, ACCOUNT_ID, applicationEnvironment.prefix("user_action")))
                                        )
                                        .actions(List.of(
                                                "dynamodb:BatchGetItem",
                                                "dynamodb:BatchWriteItem",
                                                "dynamodb:ConditionCheckItem",
                                                "dynamodb:PutItem",
                                                "dynamodb:DescribeTable",
                                                "dynamodb:DeleteItem",
                                                "dynamodb:GetItem",
                                                "dynamodb:Scan",
                                                "dynamodb:Query",
                                                "dynamodb:UpdateItem"
                                        ))
                                        .build(),
                                PolicyStatement.Builder.create()
                                        .sid("AllowSQSAccess")
                                        .effect(Effect.ALLOW)
                                        .resources(List.of(
                                                String.format("arn:aws:sqs:%s:%s:%s", region, ACCOUNT_ID, messagingOutputParameters.getTodoSharingQueueName())
                                        ))
                                        .actions(List.of(
                                                "sqs:DeleteMessage",
                                                "sqs:GetQueueUrl",
                                                "sqs:ListDeadLetterSourceQueues",
                                                "sqs:ListQueues",
                                                "sqs:ListQueueTags",
                                                "sqs:ReceiveMessage",
                                                "sqs:SendMessage",
                                                "sqs:ChangeMessageVisibility",
                                                "sqs:GetQueueAttributes"))
                                        .build()
                        )
                );

        Service service = new Service(
                serviceStack,
                "service",
                awsEnvironment,
                applicationEnvironment,
                serviceInputParameters,
                networkOutputParameters
        );

        app.synth();
    }

    static Map<String, String> environmentVariables(
            Construct scope,
            String springProfile,
            Database.DatabaseOutputParameters databaseOutputParameters
    ) {
        Map<String, String> vars = new HashMap<>();

        String databaseSecretArn = databaseOutputParameters.getDatabaseSecretArn();
        ISecret databaseSecret = Secret.fromSecretCompleteArn(scope, "databaseSecret", databaseSecretArn);

        vars.put("SPRING_PROFILES_ACTIVE", springProfile);
        vars.put("MYSQL_HOST", databaseOutputParameters.getEndpointAddress());
        vars.put("MYSQL_PORT", databaseOutputParameters.getEndpointPort());
        vars.put("MYSQL_DATABASE", databaseOutputParameters.getDbName());
        vars.put("MYSQL_USERNAME", databaseSecret.secretValueFromJson("username").unsafeUnwrap());
        vars.put("MYSQL_PASSWORD", databaseSecret.secretValueFromJson("password").unsafeUnwrap());
        vars.put("MYSQL_DDL_AUTO", "update");
        return vars;
    }
}

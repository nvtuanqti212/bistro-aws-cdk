package com.myorg;

import com.myorg.construct.ApplicationEnvironment;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.IQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import static com.myorg.constant.AWSParameter.PARAMETER_QUEUE_NAME;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 6/1/2024, Saturday
 * @description:
 **/
public class MessagingStack extends Stack {

    private final ApplicationEnvironment applicationEnvironment;
    private final IQueue emailBistroQueue;
    private final IQueue emailBistroDlq;


    public MessagingStack(
            final Construct scope,
            final String id,
            Environment awsEnvironment,
            ApplicationEnvironment applicationEnvironment){
        super(scope, id, StackProps.builder()
                .stackName(applicationEnvironment.prefix("Messaging"))
                .env(awsEnvironment).build());

        this.applicationEnvironment = applicationEnvironment;

        this.emailBistroDlq = Queue.Builder.create(this, "emailQueueDlq")
                .retentionPeriod(Duration.days(14))
                .build();

        this.emailBistroQueue = Queue.Builder.create(this, "emailQueue")
                .queueName(applicationEnvironment.prefix("email"))
                .visibilityTimeout(Duration.seconds(30))
                . retentionPeriod(Duration.days(14))
                .deadLetterQueue(DeadLetterQueue.builder()
                        .maxReceiveCount(3)
                        .queue(emailBistroDlq)
                        .build())
                .build();

        createOutputParameters();
    }

    private void createOutputParameters() {
        StringParameter.Builder.create(this, "bistroEmailQueueName")
                .parameterName(createParameterName(applicationEnvironment, PARAMETER_QUEUE_NAME))
                .stringValue(this.emailBistroQueue.getQueueName())
                .build();
    }

    private static String createParameterName(ApplicationEnvironment applicationEnvironment, String parameterName) {
        return applicationEnvironment.getEnvironmentName() + "-" + applicationEnvironment.getApplicationName() + "-Messaging-" + parameterName;
    }

    public static String getTodoSharingQueueName(Construct scope, ApplicationEnvironment applicationEnvironment) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_QUEUE_NAME, createParameterName(applicationEnvironment, PARAMETER_QUEUE_NAME))
                .getStringValue();
    }

    public static MessagingOutputParameters getOutputParametersFromParameterStore(Construct scope, ApplicationEnvironment applicationEnvironment) {
        return new MessagingOutputParameters(
                getTodoSharingQueueName(scope, applicationEnvironment)
        );
    }

    public static class MessagingOutputParameters {
        private final String todoSharingQueueName;

        public MessagingOutputParameters(String todoSharingQueueName) {
            this.todoSharingQueueName = todoSharingQueueName;
        }

        public String getTodoSharingQueueName() {
            return todoSharingQueueName;
        }
    }
}

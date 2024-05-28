package com.myorg.construct;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.dynamodb.*;
import software.constructs.Construct;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/28/2024, Tuesday
 * @description:
 **/
public class DynamoDBTable extends Construct {

    public DynamoDBTable(
            final Construct scope,
            final String id,
            final ApplicationEnvironment applicationEnvironment,
            final DynamoDBInputParameters inputParameters
    ){
        super(scope, id);

        new Table(scope,
                "BistroDynamoDB",
                TableProps.builder()
                        .partitionKey(Attribute.builder()
                                .name("id")
                                .type(AttributeType.STRING)
                                .build())
                        .tableName(applicationEnvironment.prefix(inputParameters.tableName))
                        .billingMode(BillingMode.PROVISIONED)
                        .tableClass(TableClass.STANDARD)
                        .writeCapacity(5)
                        .readCapacity(5)
                        .encryption(TableEncryption.AWS_MANAGED)
                        .removalPolicy(RemovalPolicy.DESTROY)
                        .build()
                );
    }


    public record DynamoDBInputParameters(String tableName){

    }
}

package com.myorg.construct;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.services.ec2.CfnSecurityGroup;
import software.amazon.awscdk.services.ec2.CfnSecurityGroupIngress;
import software.amazon.awscdk.services.rds.CfnDBInstance;
import software.amazon.awscdk.services.rds.CfnDBSubnetGroup;
import software.amazon.awscdk.services.secretsmanager.CfnSecretTargetAttachment;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.Collections;

import static com.myorg.constant.AWSParameter.*;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/14/2024, Tuesday
 * @description:
 *
 * The following parameters need to exist in the SSM parameter store for this stack to successfully deploy:
 *****<environmentName>-Network-vpcId: ID of the VPC to deploy the database into.
 *****<environmentName>-Network-isolatedSubnetOne: ID of the first isolated subnet to deploy the database into.
 *****<environmentName>-Network-isolatedSubnetTwo: ID of the first isolated subnet to deploy the database into.
 *****<environmentName>-Network-availabilityZoneOne: ID of the first AZ to deploy the database into.
 *****<environmentName>-Network-availabilityZoneTwo: ID of the second AZ to deploy the database into.
 * The stack exposes the following output parameters in the SSM parameter store to be used in other stacks:
 ***** <environmentName>-<applicationName>-Database-endpointAddress: URL of the database
 ***** <environmentName>-<applicationName>-Database-endpointPort: port to access the database
 ***** <environmentName>-<applicationName>-Database-databaseName: name of the database
 ***** <environmentName>-<applicationName>-Database-securityGroupId: ID of the database's security group
 ***** <environmentName>-<applicationName>-Database-secretArn: ARN of the secret that stores the fields "username" and "password"
 ***** <environmentName>-<applicationName>-Database-instanceId: ID of the database
 ***** The static getter methods provide a convenient access to retrieve these parameters from the parameter store for use in other stacks.
 **/
public class Database extends Construct {

    private CfnSecurityGroup databaseSecurityGroup;
    private CfnDBInstance dbInstance;
    private final ISecret databaseSecret;
    private final ApplicationEnvironment applicationEnvironment;

    public Database(
            final Construct scope,
            final String id,
            final Environment awsEnvironment,
            final ApplicationEnvironment applicationEnvironment,
            final DatabaseInputParameters databaseInputParameters) {

        super(scope, id);

        this.applicationEnvironment = applicationEnvironment;
        Network.NetworkOutputParameters networkOutputParameters = Network
                .getOutputParametersFromParameterStore(this, applicationEnvironment.getEnvironmentName());

        String username = sanitizeDbParameterName(applicationEnvironment.prefix("database"));


        databaseSecurityGroup = CfnSecurityGroup.Builder.create(this, DATABASE_SECURITY_GROUP)
                .vpcId(networkOutputParameters.getVpcId())
                .groupDescription("Security Group for the database instance")
                .groupName(applicationEnvironment.prefix("dbSecurityGroup"))
                .build();

        CfnSecurityGroupIngress dbIngressFromSelf = CfnSecurityGroupIngress.Builder.create(this, " dbIngressFromSelf")
                .ipProtocol("tcp")
                .fromPort(3306)
                .groupId(databaseSecurityGroup.getAttrGroupId())
                .sourceSecurityGroupId(databaseSecurityGroup.getAttrGroupId())
                .toPort(3306)
                .build();

        // This will generate a JSON object with the keys "username" and "password".
        databaseSecret = Secret.Builder.create(this, "databaseSecret")
                .secretName(applicationEnvironment.prefix("DatabaseSecret"))
                .description("Credentials to the RDS instance")
                .generateSecretString(SecretStringGenerator.builder()
                        .secretStringTemplate(String.format("{\"username\": \"%s\"}", username))
                        .generateStringKey("password")
                        .passwordLength(32)
                        .excludeCharacters("@/\\\" ")
                        .build())
                .build();

        CfnDBSubnetGroup subnetGroup = CfnDBSubnetGroup.Builder.create(this, "dbSubnetGroup")
                .dbSubnetGroupDescription("Subnet group for the RDS instance")
                .dbSubnetGroupName(applicationEnvironment.prefix("dbSubnetGroup"))
                .subnetIds(networkOutputParameters.getIsolatedSubnets())
                .build();

        dbInstance = CfnDBInstance.Builder.create(this, "rdsInstance")
                .dbInstanceIdentifier(applicationEnvironment.prefix("database"))
                .dbName(sanitizeDbParameterName(applicationEnvironment.prefix("database")))
                .allocatedStorage(String.valueOf(databaseInputParameters.storageInGb))
                .availabilityZone(networkOutputParameters.getAvailabilityZones().get(0))
                .dbInstanceClass(databaseInputParameters.instanceClass)
                .dbSubnetGroupName(subnetGroup.getDbSubnetGroupName())
                .engine("mysql")
                .engineVersion(databaseInputParameters.databaseInstanceVersion)
                .masterUsername(username)
                .masterUserPassword(databaseSecret.secretValueFromJson("password").unsafeUnwrap())
                .publiclyAccessible(false)
                .vpcSecurityGroups(Collections.singletonList(databaseSecurityGroup.getAttrGroupId()))
                .build();

        CfnSecretTargetAttachment.Builder.create(this, "secretTargetAttachment")
                .secretId(databaseSecret.getSecretArn())
                .targetId(dbInstance.getRef())
                .targetType("AWS::RDS::DBInstance")
                .build();

        createOutputParameters();

        applicationEnvironment.tag(this);
    }

    @NotNull
    private static String createParameterName(ApplicationEnvironment applicationEnvironment, String parameterName) {
        return applicationEnvironment.getEnvironmentName() + "-" + applicationEnvironment.getApplicationName() + "-Database-" + parameterName;
    }

    /**
     * Collects the output parameters of an already deployed {@link Database} construct from the parameter store. This requires
     * that a {@link Database} construct has been deployed previously. If you want to access the parameters from the same
     * stack that the {@link Database} construct is in, use the plain {@link #getOutputParametersFromParameterStore(Construct scope, ApplicationEnvironment environment)} method.
     *
     * @param scope       the construct in which we need the output parameters
     * @param environment the environment for which to load the output parameters. The deployed {@link Database}
     *                    construct must have been deployed into this environment.
     */
    public static DatabaseOutputParameters getOutputParametersFromParameterStore(Construct scope, ApplicationEnvironment environment) {
        return new DatabaseOutputParameters(
                getEndpointAddress(scope, environment),
                getEndpointPort(scope, environment),
                getDbName(scope, environment),
                getDatabaseSecretArn(scope, environment),
                getDatabaseSecurityGroupId(scope, environment),
                getDatabaseIdentifier(scope, environment));
    }

    private static String getDatabaseIdentifier(Construct scope, ApplicationEnvironment environment) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_INSTANCE_ID, createParameterName(environment, PARAMETER_INSTANCE_ID))
                .getStringValue();
    }

    private static String getEndpointAddress(Construct scope, ApplicationEnvironment environment) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_ENDPOINT_ADDRESS, createParameterName(environment, PARAMETER_ENDPOINT_ADDRESS))
                .getStringValue();
    }

    private static String getEndpointPort(Construct scope, ApplicationEnvironment environment) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_ENDPOINT_PORT, createParameterName(environment, PARAMETER_ENDPOINT_PORT))
                .getStringValue();
    }

    private static String getDbName(Construct scope, ApplicationEnvironment environment) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_DATABASE_NAME, createParameterName(environment, PARAMETER_DATABASE_NAME))
                .getStringValue();
    }

    private static String getDatabaseSecretArn(Construct scope, ApplicationEnvironment environment) {
        String secretArn = StringParameter.fromStringParameterName(scope, PARAMETER_SECRET_ARN, createParameterName(environment, PARAMETER_SECRET_ARN))
                .getStringValue();
        return secretArn;
    }

    private static String getDatabaseSecurityGroupId(Construct scope, ApplicationEnvironment environment) {
        String securityGroupId = StringParameter.fromStringParameterName(scope, PARAMETER_SECURITY_GROUP_ID, createParameterName(environment, PARAMETER_SECURITY_GROUP_ID))
                .getStringValue();
        return securityGroupId;
    }

    /**
     * Creates the outputs of this stack to be consumed by other stacks.
     */
    private void createOutputParameters() {

        StringParameter endpointAddress = StringParameter.Builder.create(this, "endpointAddress")
                .parameterName(createParameterName(this.applicationEnvironment, PARAMETER_ENDPOINT_ADDRESS))
                .stringValue(this.dbInstance.getAttrEndpointAddress())
                .build();

        StringParameter endpointPort = StringParameter.Builder.create(this, "endpointPort")
                .parameterName(createParameterName(this.applicationEnvironment, PARAMETER_ENDPOINT_PORT))
                .stringValue(this.dbInstance.getAttrEndpointPort())
                .build();

        StringParameter databaseName = StringParameter.Builder.create(this, "databaseName")
                .parameterName(createParameterName(this.applicationEnvironment, PARAMETER_DATABASE_NAME))
                .stringValue(this.dbInstance.getDbName())
                .build();

        StringParameter securityGroupId = StringParameter.Builder.create(this, "securityGroupId")
                .parameterName(createParameterName(this.applicationEnvironment, PARAMETER_SECURITY_GROUP_ID))
                .stringValue(this.databaseSecurityGroup.getAttrGroupId())
                .build();

        StringParameter secret = StringParameter.Builder.create(this, "secret")
                .parameterName(createParameterName(this.applicationEnvironment, PARAMETER_SECRET_ARN))
                .stringValue(this.databaseSecret.getSecretArn())
                .build();

        StringParameter instanceId = StringParameter.Builder.create(this, "instanceId")
                .parameterName(createParameterName(this.applicationEnvironment, PARAMETER_INSTANCE_ID))
                .stringValue(this.dbInstance.getDbInstanceIdentifier())
                .build();
    }

    private String sanitizeDbParameterName(String dbParameterName) {
        return dbParameterName
                // db name must have only alphanumerical characters
                .replaceAll("[^a-zA-Z0-9]", "")
                // db name must start with a letter
                .replaceAll("^[0-9]", "a");
    }

    public static class DatabaseInputParameters{
        private int storageInGb = 20;
        private String instanceClass = "db.t3.micro";
        private String databaseInstanceVersion="8.0.33";

        public DatabaseInputParameters withStorageInGb(int storageInGb) {
            this.storageInGb = storageInGb;
            return this;
        }

        public DatabaseInputParameters withInstanceClass(String instanceClass) {
            this.instanceClass = instanceClass;
            return this;
        }

        public DatabaseInputParameters withDatabaseInstanceVersion(String databaseInstanceVersion) {
            this.databaseInstanceVersion = databaseInstanceVersion;
            return this;
        }
    }

    public static class DatabaseOutputParameters {
        private final String endpointAddress;
        private final String endpointPort;
        private final String dbName;
        private final String databaseSecretArn;
        private final String databaseSecurityGroupId;
        private final String instanceId;

        public DatabaseOutputParameters(
                String endpointAddress,
                String endpointPort,
                String dbName,
                String databaseSecretArn,
                String databaseSecurityGroupId,
                String instanceId) {
            this.endpointAddress = endpointAddress;
            this.endpointPort = endpointPort;
            this.dbName = dbName;
            this.databaseSecretArn = databaseSecretArn;
            this.databaseSecurityGroupId = databaseSecurityGroupId;
            this.instanceId = instanceId;
        }

        /**
         * The URL of the Postgres instance.
         */
        public String getEndpointAddress() {
            return endpointAddress;
        }


        /**
         * The port of the Postgres instance.
         */
        public String getEndpointPort() {
            return endpointPort;
        }

        /**
         * The database name of the Postgres instance.
         */
        public String getDbName() {
            return dbName;
        }

        /**
         * The secret containing username and password.
         */
        public String getDatabaseSecretArn() {
            return databaseSecretArn;
        }

        /**
         * The database's security group.
         */
        public String getDatabaseSecurityGroupId() {
            return databaseSecurityGroupId;
        }

        /**
         * The database's identifier.
         */
        public String getInstanceId() {
            return instanceId;
        }
    }
}

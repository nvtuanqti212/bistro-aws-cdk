package com.myorg.stack;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationLoadBalancer;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.myorg.constant.AWSParameter.*;
import static java.util.Arrays.asList;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/12/2024, Sunday
 * @params - environmentName: Specify environments such as staging or production.
 **/
public class Network extends Construct {
    private final IVpc vpc;
    private final String environmentName;

    public Network(final Construct scope,
                   final String id,
                   final String environmentName) {
        super(scope, id);
        this.environmentName = environmentName;

        this.vpc = createVPC(environmentName);

        //TODO: create ecs cluster

        Tags.of(this).add("environment", environmentName);

        //TODO: Create loadbalancer
    }

    private IVpc createVPC(final String environmentName) {
        SubnetConfiguration publicSubnets = SubnetConfiguration.builder()
                .subnetType(SubnetType.PUBLIC)
                .name(prefixWithEnvironmentName("publicSubnet"))
                .build();

        SubnetConfiguration isolatedSubnets = SubnetConfiguration.builder()
                .subnetType(SubnetType.PRIVATE_ISOLATED)
                .name(prefixWithEnvironmentName("isolatedSubnet"))
                .build();

        return Vpc.Builder.create(this, "vpc")
                .natGateways(0)
                .maxAzs(2)
                .subnetConfiguration(Arrays.asList(
                        publicSubnets,
                        isolatedSubnets
                ))
                .build();
    }

    private String prefixWithEnvironmentName(String type) {
        return this.environmentName + "-" + type;
    }

    /*
    * Description: collects all output parameters of the Network construct and
    * combines them into an object of type NetworkOutputParameters
    * */
    public static NetworkOutputParameters getOutputParametersFromParameterStore(Construct scope, String environmentName) {
        return new NetworkOutputParameters(
                getVpcIdFromParameterStore(scope, environmentName),
                getIsolatedSubnetsFromParameterStore(scope, environmentName),
                getPublicSubnetsFromParameterStore(scope, environmentName),
                getAvailabilityZonesFromParameterStore(scope, environmentName)
        );
    }

    public static class NetworkInputParameters {
        private Optional<String> sslCertificateArn;

        /**
         * @param sslCertificateArn the ARN of the SSL certificate that the load balancer will use
         *                          to terminate HTTPS communication. If no SSL certificate is passed,
         *                          the load balancer will only listen to plain HTTP.
         * @deprecated use {@link #withSslCertificateArn(String)} instead
         */
        @Deprecated
        public NetworkInputParameters(String sslCertificateArn) {
            this.sslCertificateArn = Optional.ofNullable(sslCertificateArn);
        }

        public NetworkInputParameters() {
            this.sslCertificateArn = Optional.empty();
        }

        public NetworkInputParameters withSslCertificateArn(String sslCertificateArn){
            Objects.requireNonNull(sslCertificateArn);
            this.sslCertificateArn = Optional.of(sslCertificateArn);
            return this;
        }

        public Optional<String> getSslCertificateArn() {
            return sslCertificateArn;
        }
    }

    /*
    * Description: createParameterName() prefixes the
    * parameter name with the environment name to make it unique,
    *
    * For example: prod-Network-vpcId
    * */
    @NotNull
    private static String createParameterName(String environmentName, String parameterName) {
        return environmentName + "-Network-" + parameterName;
    }

    private static String getVpcIdFromParameterStore(Construct scope, String environmentName) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_VPC_ID, createParameterName(environmentName, PARAMETER_VPC_ID))
                .getStringValue();
    }

    private static List<String> getIsolatedSubnetsFromParameterStore(Construct scope, String environmentName) {

        String subnetOneId = StringParameter.fromStringParameterName(scope, PARAMETER_ISOLATED_SUBNET_ONE, createParameterName(environmentName, PARAMETER_ISOLATED_SUBNET_ONE))
                .getStringValue();

        String subnetTwoId = StringParameter.fromStringParameterName(scope, PARAMETER_ISOLATED_SUBNET_TWO, createParameterName(environmentName, PARAMETER_ISOLATED_SUBNET_TWO))
                .getStringValue();

        return asList(subnetOneId, subnetTwoId);
    }

    private static List<String> getAvailabilityZonesFromParameterStore(Construct scope, String environmentName) {

        String availabilityZoneOne = StringParameter.fromStringParameterName(scope, PARAMETER_AVAILABILITY_ZONE_ONE, createParameterName(environmentName, PARAMETER_AVAILABILITY_ZONE_ONE))
                .getStringValue();

        String availabilityZoneTwo = StringParameter.fromStringParameterName(scope, PARAMETER_AVAILABILITY_ZONE_TWO, createParameterName(environmentName, PARAMETER_AVAILABILITY_ZONE_TWO))
                .getStringValue();

        return asList(availabilityZoneOne, availabilityZoneTwo);
    }

    private static List<String> getPublicSubnetsFromParameterStore(Construct scope, String environmentName) {

        String subnetOneId = StringParameter.fromStringParameterName(scope, PARAMETER_PUBLIC_SUBNET_ONE, createParameterName(environmentName, PARAMETER_PUBLIC_SUBNET_ONE))
                .getStringValue();

        String subnetTwoId = StringParameter.fromStringParameterName(scope, PARAMETER_PUBLIC_SUBNET_TWO, createParameterName(environmentName, PARAMETER_PUBLIC_SUBNET_TWO))
                .getStringValue();

        return asList(subnetOneId, subnetTwoId);
    }


    public static class NetworkOutputParameters {

        private final String vpcId;
        private final List<String> isolatedSubnets;
        private final List<String> publicSubnets;
        private final List<String> availabilityZones;

        public NetworkOutputParameters(
                String vpcId,
                List<String> isolatedSubnets,
                List<String> publicSubnets,
                List<String> availabilityZones
        ) {
            this.vpcId = vpcId;
            this.isolatedSubnets = isolatedSubnets;
            this.publicSubnets = publicSubnets;
            this.availabilityZones = availabilityZones;
        }

        public String getVpcId() {
            return this.vpcId;
        }
        public List<String> getIsolatedSubnets() {
            return this.isolatedSubnets;
        }
        public List<String> getPublicSubnets() {
            return this.publicSubnets;
        }
        public List<String> getAvailabilityZones() {
            return this.availabilityZones;
        }
    }
}

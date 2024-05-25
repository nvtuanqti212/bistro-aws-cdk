package com.myorg.construct;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.AddCapacityOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

import java.util.*;

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
    private final ICluster ecsCluster;
    private IApplicationListener httpListener;
    private IApplicationListener httpsListener;
    private ISecurityGroup loadbalancerSecurityGroup;
    private IApplicationLoadBalancer loadBalancer;

    public Network(final Construct scope,
                   final String id,
                   final Environment environment,
                   final String environmentName,
                   final NetworkInputParameters networkInputParameters) {
        super(scope, id);
        this.environmentName = environmentName;

        this.vpc = createVPC(environmentName);

        // We're preparing an ECS cluster in the network stack and using it in the ECS stack.
        // If the cluster were in the ECS stack, it would interfere with deleting the ECS stack,
        // because an ECS service would still depend on it.
        this.ecsCluster = Cluster.Builder.create(this, "bistroCluster")
                .vpc(this.vpc)
                .clusterName(prefixWithEnvironmentName("ecsCluster"))
                .build();

        createLoadBalancer(vpc, networkInputParameters.getSslCertificateArn());

        Tags.of(this).add("environment", environmentName);
    }
    public IVpc getVpc() {
        return vpc;
    }

    public IApplicationListener getHttpListener() {
        return httpListener;
    }

    /**
     * The load balancer's HTTPS listener. May be null if the load balancer is configured for HTTP only!
     */
    @Nullable
    public IApplicationListener getHttpsListener() {
        return httpsListener;
    }

    public ISecurityGroup getLoadbalancerSecurityGroup() {
        return loadbalancerSecurityGroup;
    }

    public IApplicationLoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public ICluster getEcsCluster() {
        return ecsCluster;
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

    private void createLoadBalancer(
            final IVpc vpc,
            final Optional<String> sslCertificateArn
    ) {
        loadbalancerSecurityGroup = SecurityGroup.Builder.create(this, "loadbalancerSecurityGroup")
                .securityGroupName(prefixWithEnvironmentName("loadbalancerSecurityGroup"))
                .description("Public access to the load balancer.")
                .vpc(vpc)
                .build();

        CfnSecurityGroupIngress ingressFromPublic = CfnSecurityGroupIngress.Builder.create(this, "ingressToLoadbalancer")
                .groupId(loadbalancerSecurityGroup.getSecurityGroupId())
                .description(" Allow all inbound traffic on the load balancer listener port")
                .cidrIp("0.0.0.0/0")
                .ipProtocol("-1")
                .build();

        loadBalancer = ApplicationLoadBalancer.Builder.create(this, "loadBalancer")
                .vpc(this.vpc)
                .loadBalancerName(prefixWithEnvironmentName("loadBalancer"))
                .internetFacing(true)
                .securityGroup(loadbalancerSecurityGroup)
                .build();

        IApplicationTargetGroup dummyTargetGroup = ApplicationTargetGroup.Builder.create(this, "defaultTargetGroup")
                .vpc(this.vpc)
                .port(8080)
                .protocol(ApplicationProtocol.HTTP)
                .targetGroupName(prefixWithEnvironmentName("no-op-targetGroup"))
                .targetType(TargetType.IP)
                .deregistrationDelay(Duration.seconds(5))
                .healthCheck(
                        HealthCheck.builder()
                                .healthyThresholdCount(2)
                                .interval(Duration.seconds(10))
                                .timeout(Duration.seconds(5))
                                .build()
                )
                .build();

        httpListener = loadBalancer.addListener("httpListener", BaseApplicationListenerProps.builder()
                .port(80)
                .protocol(ApplicationProtocol.HTTP)
                .open(true)
                .build());

        httpListener.addTargetGroups("http-defaultTargetGroup",
                AddApplicationTargetGroupsProps.builder()
                        .targetGroups(Collections.singletonList(dummyTargetGroup))
                        .build());

        if (sslCertificateArn.isPresent()) {
            IListenerCertificate certificate = ListenerCertificate.fromArn(sslCertificateArn.get());
            httpsListener = loadBalancer.addListener("httpsListener", BaseApplicationListenerProps.builder()
                    .port(443)
                    .protocol(ApplicationProtocol.HTTPS)
                    .certificates(Collections.singletonList(certificate))
                    .open(true)
                    .build());

            httpsListener.addTargetGroups("https-defaultTargetGroup", AddApplicationTargetGroupsProps.builder()
                    .targetGroups(Collections.singletonList(dummyTargetGroup))
                    .build());

            ListenerAction redirectAction = ListenerAction.redirect(
                    RedirectOptions.builder()
                            .protocol("HTTPS")
                            .port("443")
                            .build()
            );
            ApplicationListenerRule applicationListenerRule = new ApplicationListenerRule(
                    this,
                    "HttpListenerRule",
                    ApplicationListenerRuleProps.builder()
                            .listener(httpListener)
                            .priority(1)
                            .conditions(List.of(ListenerCondition.pathPatterns(List.of("*"))))
                            .action(redirectAction)
                            .build()
            );
        }

        createOutputParameters();
    }

    /**
     * Stores output parameters of this stack in the parameter store so they can be retrieved by other stacks
     * or constructs as necessary.
     */
    private void createOutputParameters(){
        StringParameter vpcId = StringParameter.Builder.create(this, "vpcId")
                .parameterName(createParameterName(environmentName, PARAMETER_VPC_ID))
                .stringValue(this.vpc.getVpcId())
                .build();

        StringParameter httpListener = StringParameter.Builder.create(this, "httpListener")
                .parameterName(createParameterName(environmentName, PARAMETER_HTTP_LISTENER))
                .stringValue(this.httpListener.getListenerArn())
                .build();

        if (this.httpsListener != null) {
            StringParameter httpsListener = StringParameter.Builder.create(this, "httpsListener")
                    .parameterName(createParameterName(environmentName, PARAMETER_HTTPS_LISTENER))
                    .stringValue(this.httpsListener.getListenerArn())
                    .build();
        } else {
            StringParameter httpsListener = StringParameter.Builder.create(this, "httpsListener")
                    .parameterName(createParameterName(environmentName, PARAMETER_HTTPS_LISTENER))
                    .stringValue("null")
                    .build();
        }

        StringParameter loadbalancerSecurityGroup = StringParameter.Builder.create(this, "loadBalancerSecurityGroupId")
                .parameterName(createParameterName(environmentName, PARAMETER_LOADBALANCER_SECURITY_GROUP_ID))
                .stringValue(this.loadbalancerSecurityGroup.getSecurityGroupId())
                .build();

        StringParameter cluster = StringParameter.Builder.create(this, "ecsClusterName")
                .parameterName(createParameterName(environmentName, PARAMETER_ECS_CLUSTER_NAME))
                .stringValue(this.ecsCluster.getClusterName())
                .build();

        StringParameter availabilityZoneOne = StringParameter.Builder.create(this, "availabilityZoneOne")
                .parameterName(createParameterName(environmentName, PARAMETER_AVAILABILITY_ZONE_ONE))
                .stringValue(vpc.getAvailabilityZones().get(0))
                .build();

        StringParameter availabilityZoneTwo = StringParameter.Builder.create(this, "availabilityZoneTwo")
                .parameterName(createParameterName(environmentName, PARAMETER_AVAILABILITY_ZONE_TWO))
                .stringValue(vpc.getAvailabilityZones().get(1))
                .build();

        StringParameter isolatedSubnetOne = StringParameter.Builder.create(this, "isolatedSubnetOne")
                .parameterName(createParameterName(environmentName, PARAMETER_ISOLATED_SUBNET_ONE))
                .stringValue(this.vpc.getIsolatedSubnets().get(0).getSubnetId())
                .build();

        StringParameter isolatedSubnetTwo = StringParameter.Builder.create(this, "isolatedSubnetTwo")
                .parameterName(createParameterName(environmentName, PARAMETER_ISOLATED_SUBNET_TWO))
                .stringValue(this.vpc.getIsolatedSubnets().get(1).getSubnetId())
                .build();

        StringParameter publicSubnetOne = StringParameter.Builder.create(this, "publicSubnetOne")
                .parameterName(createParameterName(environmentName, PARAMETER_PUBLIC_SUBNET_ONE))
                .stringValue(this.vpc.getPublicSubnets().get(0).getSubnetId())
                .build();

        StringParameter publicSubnetTwo = StringParameter.Builder.create(this, "publicSubnetTwo")
                .parameterName(createParameterName(environmentName, PARAMETER_PUBLIC_SUBNET_TWO))
                .stringValue(this.vpc.getPublicSubnets().get(1).getSubnetId())
                .build();

        StringParameter loadBalancerArn = StringParameter.Builder.create(this, "loadBalancerArn")
                .parameterName(createParameterName(environmentName, PARAMETER_LOAD_BALANCER_ARN))
                .stringValue(this.loadBalancer.getLoadBalancerArn())
                .build();

        StringParameter loadBalancerDnsName = StringParameter.Builder.create(this, "loadBalancerDnsName")
                .parameterName(createParameterName(environmentName, PARAMETER_LOAD_BALANCER_DNS_NAME))
                .stringValue(this.loadBalancer.getLoadBalancerDnsName())
                .build();

        StringParameter loadBalancerCanonicalHostedZoneId = StringParameter.Builder.create(this, "loadBalancerCanonicalHostedZoneId")
                .parameterName(createParameterName(environmentName, PARAMETER_LOAD_BALANCER_HOSTED_ZONE_ID))
                .stringValue(this.loadBalancer.getLoadBalancerCanonicalHostedZoneId())
                .build();
    }

    /*
     * Description: collects all output parameters of the Network construct and
     * combines them into an object of type NetworkOutputParameters
     * */
    public static NetworkOutputParameters getOutputParametersFromParameterStore(Construct scope, String environmentName) {
        return new NetworkOutputParameters(
                getVpcIdFromParameterStore(scope, environmentName),
                getHttpListenerArnFromParameterStore(scope, environmentName),
                getHttpsListenerArnFromParameterStore(scope, environmentName),
                getLoadbalancerSecurityGroupIdFromParameterStore(scope, environmentName),
                getEcsClusterNameFromParameterStore(scope, environmentName),
                getIsolatedSubnetsFromParameterStore(scope, environmentName),
                getPublicSubnetsFromParameterStore(scope, environmentName),
                getAvailabilityZonesFromParameterStore(scope, environmentName),
                getLoadBalancerArnFromParameterStore(scope, environmentName),
                getLoadBalancerDnsNameFromParameterStore(scope, environmentName),
                getLoadBalancerCanonicalHostedZoneIdFromParameterStore(scope, environmentName)
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

        public NetworkInputParameters withSslCertificateArn(String sslCertificateArn) {
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

    private static String getHttpListenerArnFromParameterStore(Construct scope, String environmentName) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_HTTP_LISTENER, createParameterName(environmentName, PARAMETER_HTTP_LISTENER))
                .getStringValue();
    }

    private static Optional<String> getHttpsListenerArnFromParameterStore(Construct scope, String environmentName) {
        String value = StringParameter.fromStringParameterName(scope, PARAMETER_HTTPS_LISTENER, createParameterName(environmentName, PARAMETER_HTTPS_LISTENER))
                .getStringValue();
        if ("null".equals(value)) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(value);
        }
    }

    private static String getLoadbalancerSecurityGroupIdFromParameterStore(Construct scope, String environmentName) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_LOADBALANCER_SECURITY_GROUP_ID, createParameterName(environmentName, PARAMETER_LOADBALANCER_SECURITY_GROUP_ID))
                .getStringValue();
    }

    private static String getEcsClusterNameFromParameterStore(Construct scope, String environmentName) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_ECS_CLUSTER_NAME, createParameterName(environmentName, PARAMETER_ECS_CLUSTER_NAME))
                .getStringValue();
    }

    private static String getLoadBalancerArnFromParameterStore(Construct scope, String environmentName) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_LOAD_BALANCER_ARN, createParameterName(environmentName, PARAMETER_LOAD_BALANCER_ARN))
                .getStringValue();
    }

    private static String getLoadBalancerDnsNameFromParameterStore(Construct scope, String environmentName) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_LOAD_BALANCER_DNS_NAME, createParameterName(environmentName, PARAMETER_LOAD_BALANCER_DNS_NAME))
                .getStringValue();
    }

    private static String getLoadBalancerCanonicalHostedZoneIdFromParameterStore(Construct scope, String environmentName) {
        return StringParameter.fromStringParameterName(scope, PARAMETER_LOAD_BALANCER_HOSTED_ZONE_ID, createParameterName(environmentName, PARAMETER_LOAD_BALANCER_HOSTED_ZONE_ID))
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
        private final String httpListenerArn;
        private final Optional<String> httpsListenerArn;
        private final String loadbalancerSecurityGroupId;
        private final String ecsClusterName;
        private final List<String> isolatedSubnets;
        private final List<String> publicSubnets;
        private final List<String> availabilityZones;
        private final String loadBalancerArn;
        private final String loadBalancerDnsName;
        private final String loadBalancerCanonicalHostedZoneId;

        public NetworkOutputParameters(
                String vpcId,
                String httpListenerArn,
                Optional<String> httpsListenerArn,
                String loadbalancerSecurityGroupId,
                String ecsClusterName,
                List<String> isolatedSubnets,
                List<String> publicSubnets,
                List<String> availabilityZones,
                String loadBalancerArn,
                String loadBalancerDnsName,
                String loadBalancerCanonicalHostedZoneId
        ) {
            this.vpcId = vpcId;
            this.httpListenerArn = httpListenerArn;
            this.httpsListenerArn = httpsListenerArn;
            this.loadbalancerSecurityGroupId = loadbalancerSecurityGroupId;
            this.ecsClusterName = ecsClusterName;
            this.isolatedSubnets = isolatedSubnets;
            this.publicSubnets = publicSubnets;
            this.availabilityZones = availabilityZones;
            this.loadBalancerArn = loadBalancerArn;
            this.loadBalancerDnsName = loadBalancerDnsName;
            this.loadBalancerCanonicalHostedZoneId = loadBalancerCanonicalHostedZoneId;
        }

        /**
         * The VPC ID.
         */
        public String getVpcId() {
            return this.vpcId;
        }

        /**
         * The ARN of the HTTP listener.
         */
        public String getHttpListenerArn() {
            return this.httpListenerArn;
        }

        /**
         * The ARN of the HTTPS listener.
         */
        public Optional<String> getHttpsListenerArn() {
            return this.httpsListenerArn;
        }

        /**
         * The ID of the load balancer's security group.
         */
        public String getLoadbalancerSecurityGroupId() {
            return this.loadbalancerSecurityGroupId;
        }

        /**
         * The name of the ECS cluster.
         */
        public String getEcsClusterName() {
            return this.ecsClusterName;
        }

        /**
         * The IDs of the isolated subnets.
         */
        public List<String> getIsolatedSubnets() {
            return this.isolatedSubnets;
        }

        /**
         * The IDs of the public subnets.
         */
        public List<String> getPublicSubnets() {
            return this.publicSubnets;
        }

        /**
         * The names of the availability zones of the VPC.
         */
        public List<String> getAvailabilityZones() {
            return this.availabilityZones;
        }

        /**
         * The ARN of the load balancer.
         */
        public String getLoadBalancerArn() {
            return this.loadBalancerArn;
        }

        /**
         * The DNS name of the load balancer.
         */
        public String getLoadBalancerDnsName() {
            return loadBalancerDnsName;
        }

        /**
         * The hosted zone ID of the load balancer.
         */
        public String getLoadBalancerCanonicalHostedZoneId() {
            return loadBalancerCanonicalHostedZoneId;
        }
    }
}

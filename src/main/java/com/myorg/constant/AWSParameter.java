package com.myorg.constant;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/12/2024, Sunday
 * @description:
 **/
public class AWSParameter {
    //Network VPC parameters
    public static final String PARAMETER_VPC_ID = "vpcId";
    public static final String PARAMETER_HTTP_LISTENER = "httpListenerArn";
    public static final String PARAMETER_HTTPS_LISTENER = "httpsListenerArn";
    public static final String PARAMETER_LOADBALANCER_SECURITY_GROUP_ID = "loadBalancerSecurityGroupId";
    public static final String PARAMETER_ECS_CLUSTER_NAME = "ecsClusterName";
    public static final String PARAMETER_ISOLATED_SUBNET_ONE = "isolatedSubnetIdOne";
    public static final String PARAMETER_ISOLATED_SUBNET_TWO = "isolatedSubnetIdTwo";
    public static final String PARAMETER_PUBLIC_SUBNET_ONE = "publicSubnetIdOne";
    public static final String PARAMETER_PUBLIC_SUBNET_TWO = "publicSubnetIdTwo";
    public static final String PARAMETER_AVAILABILITY_ZONE_ONE = "availabilityZoneOne";
    public static final String PARAMETER_AVAILABILITY_ZONE_TWO = "availabilityZoneTwo";
    public static final String PARAMETER_LOAD_BALANCER_ARN = "loadBalancerArn";
    public static final String PARAMETER_LOAD_BALANCER_DNS_NAME = "loadBalancerDnsName";
    public static final String PARAMETER_LOAD_BALANCER_HOSTED_ZONE_ID = "loadBalancerCanonicalHostedZoneId";

    //database parameter
    public static final String PARAMETER_ENDPOINT_ADDRESS = "endpointAddress";
    public static final String PARAMETER_ENDPOINT_PORT = "endpointPort";
    public static final String PARAMETER_DATABASE_NAME = "databaseName";
    public static final String PARAMETER_SECURITY_GROUP_ID = "securityGroupId";
    public static final String PARAMETER_SECRET_ARN = "secretArn";
    public static final String PARAMETER_INSTANCE_ID = "instanceId";
    public static final String DATABASE_SECURITY_GROUP = "databaseSecurityGroup";
}

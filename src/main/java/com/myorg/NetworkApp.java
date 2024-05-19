package com.myorg;

import com.myorg.construct.Network;
import com.myorg.util.AWSUtils;
import com.myorg.util.DataUtil;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import static com.myorg.constant.InputParameters.*;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/13/2024, Monday
 * @description:
 **/
public class NetworkApp {
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


        Environment awsEnvironment = AWSUtils.makeEnv(ACCOUNT_ID, region);

        Stack networkStack = new Stack(
                app,
                "NetworkStack",
                StackProps.builder()
                        .env(awsEnvironment)
                        .build());

        Network.NetworkInputParameters inputParameters = new Network.NetworkInputParameters();

        Network network = new Network(networkStack, "NetworkCDK", awsEnvironment, environmentName, new Network.NetworkInputParameters());
        app.synth();
    }
}

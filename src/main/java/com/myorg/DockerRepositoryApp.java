package com.myorg;

import com.myorg.construct.DockerRepository;
import com.myorg.util.AWSUtils;
import com.myorg.util.DataUtil;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import static com.myorg.constant.InputParameters.ACCOUNT_ID;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/19/2024, Sunday
 * @description:
 **/
public class DockerRepositoryApp {
    public static void main(final String[] args) {
        App app = new App();

        String region = (String) app
                .getNode()
                .tryGetContext("region");
        DataUtil.requireNonEmptyOrNull(region, "context variable 'region' must not be null");

        String applicationName = (String) app
                .getNode()
                .tryGetContext("applicationName");
        DataUtil.requireNonEmptyOrNull(applicationName, "context variable 'applicationName' must not be null");

        Environment awsEnvironment = AWSUtils.makeEnv(ACCOUNT_ID, region);

        Stack dockerRepositoryStack = new Stack(
                app,
                "DockerRepositoryStack",
                StackProps.builder()
                        .stackName(applicationName + "-DockerRepository")
                        .env(awsEnvironment)
                        .build());

        DockerRepository dockerRepository = new DockerRepository(
                dockerRepositoryStack,
                "DockerRepository",
                awsEnvironment,
                new DockerRepository.DockerRepositoryInputParameters(applicationName, ACCOUNT_ID)
        );

        app.synth();
    }
}

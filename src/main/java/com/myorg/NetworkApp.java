package com.myorg;

import com.myorg.stack.Network;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Stack;

import static com.myorg.constant.InputParameters.*;

/**
 * @author : Nguyen Van Quoc Tuan
 * @date: 5/13/2024, Monday
 * @description:
 **/
public class NetworkApp {
    public static void main(final String[] args) {
        App app = new App();


        Stack networkStack = new Stack(
                app,
                "NetworkStack");

        Network.NetworkInputParameters inputParameters = new Network.NetworkInputParameters();

        Network network = new Network(networkStack, "NetworkCDK", ENVIRONMENT_TEST);
        app.synth();
    }
}

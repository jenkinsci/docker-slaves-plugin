package it.dockins.dockerslaves.drivers;

import hudson.Extension;
import hudson.model.Item;
import it.dockins.dockerslaves.spi.DockerDriverFactory;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;

public class CliDockerDriverFactory extends DockerDriverFactory {

    @DataBoundConstructor
    public CliDockerDriverFactory() {
    }

    @Override
    public DockerDriver newDockerDriver(DockerServerEndpoint dockerHost, Item context) throws IOException, InterruptedException {
        return new CliDockerDriver(dockerHost, context);
    }

    @Extension
    public static class DescriptorImpl extends DockerDriverFactory.Descriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker CLI Driver";
        }
    }
}
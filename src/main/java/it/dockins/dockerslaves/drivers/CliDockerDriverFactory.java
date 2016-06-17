package it.dockins.dockerslaves.drivers;

import hudson.Extension;
import it.dockins.dockerslaves.spi.DockerDriverFactory;
import it.dockins.dockerslaves.spi.DockerHostConfig;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;

public class CliDockerDriverFactory extends DockerDriverFactory {

    @DataBoundConstructor
    public CliDockerDriverFactory() {
    }

    @Override
    public DockerDriver newDockerDriver(DockerHostConfig dockerHost) throws IOException, InterruptedException {
        return new CliDockerDriver(dockerHost);
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
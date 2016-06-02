package it.dockins.dockerslaves;

import hudson.Extension;
import hudson.model.Job;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;
import it.dockins.dockerslaves.spi.DockerHostSource;
import it.dockins.dockerslaves.spi.DockerHostSourceDescriptor;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DefaultDockerHostSource extends DockerHostSource {

    private final DockerServerEndpoint dockerServerEndpoint;

    @DataBoundConstructor
    public DefaultDockerHostSource(DockerServerEndpoint dockerServerEndpoint) {
        this.dockerServerEndpoint = dockerServerEndpoint;
    }

    public DockerServerEndpoint getDockerServerEndpoint() {
        return dockerServerEndpoint;
    }

    @Override
    public DockerServerEndpoint getDockerHost(Job job) throws IOException, InterruptedException {
        return dockerServerEndpoint;
    }

    @Extension
    public static class DescriptorImp extends DockerHostSourceDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker daemon / swarm";
        }
    }
}

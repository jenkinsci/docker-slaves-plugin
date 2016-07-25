package it.dockins.dockerslaves;

import hudson.Extension;
import hudson.model.Job;
import it.dockins.dockerslaves.spi.DockerHostConfig;
import jenkins.model.Jenkins;
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

    private final String hyperAccessKey;
    private final String hyperSecretKey;

    public DefaultDockerHostSource() {
        this(new DockerServerEndpoint(null, null), null, null);
    }

    @DataBoundConstructor
    public DefaultDockerHostSource(DockerServerEndpoint dockerServerEndpoint, String hyperAccessKey, String hyperSecretKey) {
        this.dockerServerEndpoint = dockerServerEndpoint;
        this.hyperAccessKey = hyperAccessKey;
        this.hyperSecretKey = hyperSecretKey;
    }

    public DockerServerEndpoint getDockerServerEndpoint() {
        return dockerServerEndpoint;
    }

    @Override
    public DockerHostConfig getDockerHost(Job job) throws IOException, InterruptedException {
        return new DockerHostConfig(dockerServerEndpoint, hyperAccessKey, hyperSecretKey, job);
    }

    @Extension
    public static class DescriptorImp extends DockerHostSourceDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Standalone Docker daemon / Docker Swarm cluster";
        }
    }
}

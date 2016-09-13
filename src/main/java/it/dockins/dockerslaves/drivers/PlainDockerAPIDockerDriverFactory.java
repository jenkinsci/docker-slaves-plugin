package it.dockins.dockerslaves.drivers;

import hudson.Extension;
import hudson.model.Job;
import hudson.util.ListBoxModel;
import it.dockins.dockerslaves.DefaultDockerHostSource;
import it.dockins.dockerslaves.spi.DockerDriver;
import it.dockins.dockerslaves.spi.DockerDriverFactory;
import it.dockins.dockerslaves.spi.DockerDriverFactoryDescriptor;
import it.dockins.dockerslaves.spi.DockerHostConfig;
import it.dockins.dockerslaves.spi.DockerHostSource;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * A ${@link DockerDriverFactory} relying on plain good old Docker API usage.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class PlainDockerAPIDockerDriverFactory extends DockerDriverFactory {

    private final DockerHostSource dockerHostSource;


    @DataBoundConstructor
    public PlainDockerAPIDockerDriverFactory(DockerHostSource dockerHostSource) {
        this.dockerHostSource = dockerHostSource;
    }

    public PlainDockerAPIDockerDriverFactory(DockerServerEndpoint dockerHost) {
        this(new DefaultDockerHostSource(dockerHost));
    }

    public DockerHostSource getDockerHostSource() {
        return dockerHostSource;
    }

    @Override
    public DockerDriver forJob(Job context) throws IOException, InterruptedException {
        return new CliDockerDriver(dockerHostSource.getDockerHost(context));
    }

    @Extension
    public static class DescriptorImp extends DockerDriverFactoryDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Docker CLI (require docker executable on PATH)";
        }
    }

}

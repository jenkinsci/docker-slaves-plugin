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

    private final CLIENT client;


    @DataBoundConstructor
    public PlainDockerAPIDockerDriverFactory(DockerHostSource dockerHostSource, CLIENT client) {
        this.dockerHostSource = dockerHostSource;
        this.client = client;
    }

    public PlainDockerAPIDockerDriverFactory() {
        this(new DefaultDockerHostSource(), CLIENT.CLI);
    }

    public DockerHostSource getDockerHostSource() {
        return dockerHostSource;
    }

    public CLIENT getClient() {
        return client;
    }

    @Override
    public DockerDriver forJob(Job context) throws IOException, InterruptedException {
        return client.forDockerHost(dockerHostSource.getDockerHost(context));
    }

    public enum CLIENT {
        CLI {
            String getDisplayName() {
                return "Docker CLI (require docker executable on PATH)";
            }

            DockerDriver forDockerHost(DockerHostConfig dockerHost) throws IOException, InterruptedException {
                return new CliDockerDriver(dockerHost);
            }
        };

        abstract String getDisplayName();

        abstract DockerDriver forDockerHost(DockerHostConfig dockerHost) throws IOException, InterruptedException;
    }

    @Extension
    public static class DescriptorImp extends DockerDriverFactoryDescriptor {

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Use plain Docker API";
        }

        public ListBoxModel doFillClientItems() {
            final ListBoxModel options = new ListBoxModel();
            for (CLIENT client : CLIENT.values()) {
                options.add(client.getDisplayName(), client.name());
            }
            return options;
        }
    }

}

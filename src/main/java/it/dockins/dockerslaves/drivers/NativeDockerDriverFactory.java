package it.dockins.dockerslaves.drivers;

import com.github.dockerjava.netty.DockerCmdExecFactoryImpl;
import hudson.Extension;
import hudson.model.Item;
import it.dockins.dockerslaves.spi.DockerDriverFactory;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;

public class NativeDockerDriverFactory extends DockerDriverFactory {

    @DataBoundConstructor
    public NativeDockerDriverFactory() {
    }

    @Override
    public DockerDriver newDockerDriver(DockerServerEndpoint dockerHost, Item context) throws IOException, InterruptedException {
        return new NativeDockerDriver(dockerHost, context);
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends Descriptor {
        static Class clazz = DockerCmdExecFactoryImpl.class;

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Native driver (based on Netty)";
        }
    }
}
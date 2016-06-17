package it.dockins.dockerslaves.drivers;

import com.github.dockerjava.netty.DockerCmdExecFactoryImpl;
import hudson.Extension;
import it.dockins.dockerslaves.spi.DockerDriverFactory;
import it.dockins.dockerslaves.spi.DockerHostConfig;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;

public class NativeDockerDriverFactory extends DockerDriverFactory {

    @DataBoundConstructor
    public NativeDockerDriverFactory() {
    }

    @Override
    public DockerDriver newDockerDriver(DockerHostConfig dockerHost) throws IOException, InterruptedException {
        return new NativeDockerDriver(dockerHost);
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
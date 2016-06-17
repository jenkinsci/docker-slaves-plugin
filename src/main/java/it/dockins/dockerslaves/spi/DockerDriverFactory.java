package it.dockins.dockerslaves.spi;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import it.dockins.dockerslaves.drivers.DockerDriver;

import java.io.IOException;

public abstract class DockerDriverFactory extends AbstractDescribableImpl<DockerDriverFactory> implements ExtensionPoint {

    public abstract DockerDriver newDockerDriver(DockerHostConfig dockerHost) throws IOException, InterruptedException;

    public static class Descriptor extends hudson.model.Descriptor<DockerDriverFactory> {
    }
}

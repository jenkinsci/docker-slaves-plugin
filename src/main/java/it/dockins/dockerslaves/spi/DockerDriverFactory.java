package it.dockins.dockerslaves.spi;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Item;
import it.dockins.dockerslaves.drivers.DockerDriver;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

import java.io.IOException;

public abstract class DockerDriverFactory extends AbstractDescribableImpl<DockerDriverFactory> implements ExtensionPoint {

    public abstract DockerDriver newDockerDriver(DockerServerEndpoint dockerHost, Item context) throws IOException, InterruptedException;

    public static class Descriptor extends hudson.model.Descriptor<DockerDriverFactory> {
    }
}

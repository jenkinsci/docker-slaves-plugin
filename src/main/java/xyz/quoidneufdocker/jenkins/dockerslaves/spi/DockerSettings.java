package xyz.quoidneufdocker.jenkins.dockerslaves.spi;

import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerSettings {

    private String workspaceVolume;

    private DockerServerEndpoint dockerHost;

}

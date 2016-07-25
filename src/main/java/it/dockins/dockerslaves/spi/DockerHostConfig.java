package it.dockins.dockerslaves.spi;

import hudson.EnvVars;
import hudson.model.Item;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.KeyMaterial;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

/**
 * Configuration options used to access a specific (maybe dedicated to a build) Docker Host.
 * <p>
 * Itent here is to allow some infrastructure plugin to prepare a dedicated Docker Host per build,
 * using some higher level isolation, so the build is safe to do whatever it needs with it's docker
 * daemon without risk to impact other builds.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class DockerHostConfig implements Closeable {

    /** Docker Host's daemon endpoint */
    private final DockerServerEndpoint endpoint;

    /**Hyper's ACCESS_KEY and SECRET_KEY*/
    public final String hyperAccessKey;
    public final String hyperSecretKey;

    /** Docker API access keys  */
    private final KeyMaterial keys;

    public DockerHostConfig(DockerServerEndpoint endpoint, String hyperAccessKey, String hyperSecretKey, Item context) throws IOException, InterruptedException {
        this.endpoint = endpoint;
        this.hyperAccessKey = hyperAccessKey;
        this.hyperSecretKey = hyperSecretKey;
        keys = endpoint.newKeyMaterialFactory(context, Jenkins.getActiveInstance().getChannel()).materialize();
    }

    public DockerServerEndpoint getEndpoint() {
        return endpoint;
    }

    public String getHyperAccessKey() {
        return hyperAccessKey;
    }

    public String getHyperSecretKey() {
        return hyperSecretKey;
    }

    public EnvVars getEnvironment() {
        return keys.env();
    }

    @Override
    public void close() throws IOException {
        keys.close();
    }
}

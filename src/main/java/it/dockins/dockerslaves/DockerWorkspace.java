package it.dockins.dockerslaves;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import jenkins.slaves.WorkspaceLocator;

/**
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class DockerWorkspace extends WorkspaceLocator {

    @Override
    public FilePath locate(TopLevelItem item, Node node) {
        return null;
    }
}

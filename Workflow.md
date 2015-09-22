# Workflow

This document is a design doc.

We'd like the docker-slaves plugin to be usable with [workflow plugin](https://github.com/jenkinsci/workflow-plugin)

### Proposed syntax :
```
onDockerNode( "maven:3-jdk8", database:"mysql", webserver:"jetty:9" ) {
   // some build steps
}
```

### Implementation notes 
plugin will implement `org.jenkinsci.plugins.workflow.support.steps.ExecutorStep` tio offer an alternative to `node()`

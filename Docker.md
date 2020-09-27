# Container-slaves Docker implementation

Plugin is relying on Docker for container management.

Remoting is established with a fixed image `jenkinsci/slave`.
To prevent classes version mismatch, we use `docker cp` at startup to inject the remoting jar bundled with Jenkins into the
container.

`TMPDIR` and `java.io.tmpdir` are set to `/home/jenkins/.tmp` so every build file should be created within `/home/jenkins`.

`/home/jenkins` is set as VOLUME, so it can be reused for a subsequent build, or browsed after the build
as long as the container isn't removed.

Build commands are run inside arbitrary containers, ran as user `jenkins` (uid:1000, gid:1000) so there's no permission issue accessing the
workspace and other files inside `/home/jenkins`. This user is automatically created in container user do configure for the build.



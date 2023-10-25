package eu.nets.maven.classpath;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.nets.maven.classpath.BazelMojo.artifactKey;
import static eu.nets.maven.classpath.BazelMojo.reactorArtifacts;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;
import static org.codehaus.plexus.util.FileUtils.forceMkdir;

@Mojo(name = "workspace",
        defaultPhase = GENERATE_RESOURCES,
        aggregator = true,
        threadSafe = true)
public class WorkspaceMojo extends AbstractMojo {

    @Component
    private BuildContext context;

    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "classpath.outputDirectory", defaultValue = "${basedir}")
    private File outputDirectory;

    @Parameter(property = "classpath.file", defaultValue = "WORKSPACE-maven.bzl")
    private String file;

    @Parameter(property = "classpath.sort", defaultValue = "true")
    private boolean sort;

    @Parameter(defaultValue = "false")
    private boolean skip;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution.
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    /**
     * The reactor projects.
     */
    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    private List<MavenProject> reactorProjects;

    public void execute() throws MojoExecutionException {
        getLog().info("WORKSPACE!!");

        List<Artifact> artifacts = new ArrayList<>();

        for (MavenProject p : reactorProjects) {
            System.out.println("reactorProject.getArtifact() = " + p.getArtifact());

            ArtifactResolutionRequest req = new ArtifactResolutionRequest()
                    .setArtifact(p.getArtifact())
                    .setLocalRepository(getLocalRepository())
                    .setRemoteRepositories(project.getRemoteArtifactRepositories())
                    .setResolveRoot(false)
                    .setResolveTransitively(true);

            ArtifactResolutionResult result = repoSystem.resolve(req);

            artifacts.addAll(result.getArtifacts());
        }

        // Remove all artifacts that are a part of this reactor. Remove anything that matches groupId:artifactId, as
        // attached/test artifacts doesn't seem to be a part of this list.
        Set<String> reactorArtifacts = reactorArtifacts(reactorProjects);

        artifacts.removeIf(a -> reactorArtifacts.contains(artifactKey(a)));

        try {
            forceMkdir(outputDirectory);

            try (PrintWriter w = new PrintWriter(new File(outputDirectory, file))) {
                w.println("maven_artifacts = [");
                artifacts.stream()
                        .sorted(Comparator.comparing(Object::toString))
                        .distinct()
                        .forEach(a -> w.println("    \"" + artifactLine(a) + "\","));
                w.println("]");
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }


    private String artifactLine(Artifact a) {
        String s = artifactKey(a);

        if (!a.getType().equals("jar")) {
            s += ":" + a.getType();
        }

        s += ":" + a.getBaseVersion();

        return s;
    }

    private ArtifactRepository getLocalRepository() throws MojoExecutionException {
        try {
            return repoSystem.createDefaultLocalRepository();
        } catch (InvalidRepositoryException e) {
            throw new MojoExecutionException(e);
        }
    }
}

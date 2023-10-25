package eu.nets.maven.classpath;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;
import static org.codehaus.plexus.util.FileUtils.forceMkdir;

@Mojo(name = "bazel",
        defaultPhase = GENERATE_RESOURCES,
        requiresDependencyCollection = RUNTIME,
        threadSafe = true)
public class BazelMojo extends AbstractMojo {

    @Component
    private BuildContext context;

    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "classpath.outputDirectory", defaultValue = "${basedir}")
    private File outputDirectory;

    @Parameter(property = "classpath.file", defaultValue = "BUILD-maven.bzl")
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
        if (skip) {
            getLog().debug("Skipping execution.");
            return;
        }

        getLog().info("BAZEL: " + file);
        getLog().info("BAZEL: " + reactorProjects.size());

        Artifact artifact = project.getArtifact();
        File outputFile = new File(outputDirectory, file);

        Set<Artifact> artifacts = resolveArtifacts(artifact);
        if (artifacts.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            outputFile.delete();
            return;
        }

        Set<String> reactorArtifacts = reactorArtifacts(reactorProjects);

        try {
            forceMkdir(outputDirectory);

            try (PrintWriter w = new PrintWriter(outputFile)) {
                Map<String, List<Artifact>> byScope = artifacts.stream()
                        .collect(Collectors.groupingBy(Artifact::getScope));

                w.println("# Note: this file is generated!");
                w.println();
                w.println("load(\"@rules_jvm_external//:defs.bzl\", \"artifact\")");

                // Make sure that all the bazel variables for the standard scopes will be declared in the output file.
                for (String scope : Arrays.asList("compile", "test", "system", "provided", "runtime")) {
                    Collection<Artifact> as = byScope.getOrDefault(scope, emptyList())
                            .stream()
                            .filter(a -> !reactorArtifacts.contains(artifactKey(a)))
                            .sorted(Comparator.comparing(Object::toString))
                            .collect(Collectors.toList());

                    w.println();

                    w.println("maven_" + scope + " = [");
                    as.forEach(a -> {
                        String key = a.getGroupId() + ":" + a.getArtifactId();

                        if (!a.getType().equals("jar")) {
                            key += ":" + a.getType();
                        }

                        w.println("    artifact(\"" + key + "\"),");
                    });
                    w.println("]");
                }

                w.println();
                w.println("scope_compile = maven_compile");
                w.println("scope_runtime = maven_compile + maven_runtime");
                w.println("scope_test = maven_compile + maven_system + maven_provided + maven_runtime + maven_test");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error while writing file.", e);
        }
    }

    private Set<Artifact> resolveArtifacts(Artifact artifact) throws MojoExecutionException {
        Set<Artifact> artifacts;
        try {
            ArtifactResolutionRequest req = new ArtifactResolutionRequest()
                    .setArtifact(artifact)
                    .setLocalRepository(repoSystem.createDefaultLocalRepository())
                    .setRemoteRepositories(project.getRemoteArtifactRepositories())
                    .setResolveRoot(false)
                    .setResolveTransitively(true);

            ArtifactResolutionResult result = repoSystem.resolve(req);

            artifacts = result.getArtifacts();
        } catch (InvalidRepositoryException e) {
            throw new MojoExecutionException(e);
        }
        return artifacts;
    }


    public static Set<String> reactorArtifacts(List<MavenProject> reactorProjects) {
        return reactorProjects.stream()
                .map(MavenProject::getArtifact)
                .map(BazelMojo::artifactKey)
                .collect(Collectors.toSet());
    }

    public static String artifactKey(Artifact a) {
        return a.getGroupId() + ":" + a.getArtifactId();
    }
}

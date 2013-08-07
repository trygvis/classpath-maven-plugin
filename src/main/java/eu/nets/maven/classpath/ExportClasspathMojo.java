package eu.nets.maven.classpath;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.sonatype.plexus.build.incremental.BuildContext;

import static java.util.Collections.sort;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.RUNTIME;
import static org.codehaus.plexus.util.FileUtils.forceMkdir;

@Mojo(name = "export-classpath", defaultPhase = GENERATE_RESOURCES, requiresDependencyCollection = RUNTIME)
public class ExportClasspathMojo extends AbstractMojo {

    @Component
    private BuildContext context;

    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "classpath.outputDirectory", defaultValue = "${basedir}")
    private File outputDirectory;

    @Parameter(property = "classpath.file", defaultValue = "classpath.txt")
    private String file;

    @Parameter(property = "classpath.sort", defaultValue = "true")
    private boolean sort;

    @Parameter(defaultValue = "false")
    private boolean skip;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug("Skipping execution.");
            return;
        }

        Artifact artifact = project.getArtifact();
        List<Artifact> artifacts = new ArrayList<Artifact>(project.getArtifacts());

        if (sort) {
            sort(artifacts);
        }

        write(artifact, artifacts);
    }

    private void write(Artifact artifact, List<Artifact> artifacts) throws MojoExecutionException {
        OutputStream stream = null;
        try {
            forceMkdir(outputDirectory);
            stream = context.newFileOutputStream(new File(outputDirectory, file));
            OutputStreamWriter writer = new OutputStreamWriter(stream, "UTF-8");
            TextFormat.write(artifact, artifacts, writer);
        } catch (IOException e) {
            throw new MojoExecutionException("Error while writing file.", e);
        } finally {
            IOUtil.close(stream);
        }
    }
}

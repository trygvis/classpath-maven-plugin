package eu.nets.maven.classpath;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.codehaus.plexus.util.StringUtils;

public class TextFormat {
    public static void write(Artifact artifact, Collection<Artifact> artifacts, Writer w) {
        write(0, artifact, artifacts, w);
    }

    private static void write(int indent, Artifact artifact, Collection<Artifact> artifacts, Writer w) {
        PrintWriter writer = new PrintWriter(w);
        String value = getKey(artifact);

        writer.print(StringUtils.repeat(" ", indent));
        writer.println(value);

        for (Artifact a : artifacts) {
            writer.print(StringUtils.repeat(" ", indent));
            writer.println(getKey(a));
        }

        writer.flush();
    }

    private static String getKey(Artifact a) {
        // if a.getVersion() is used, the resolved version is used. Might be a feature for someone.
        return a.getGroupId() + ":" +
                a.getArtifactId() + ":" +
                a.getBaseVersion() + ":" +
                a.getType();
    }
}

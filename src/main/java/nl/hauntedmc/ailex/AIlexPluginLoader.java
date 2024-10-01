package nl.hauntedmc.ailex;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import org.jetbrains.annotations.NotNull;

/**
 * Plugin loader for the AIlex plugin
 */
public class AIlexPluginLoader implements PluginLoader {

    /**
     * Called when the plugin is loaded
     * This method is responsible for registering all runtime dependencies for the plugin
     * @param classpathBuilder a mutable classpath builder that may be used to register custom runtime dependencies
     *                         for the plugin the loader was registered for.
     */
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addDependency(new Dependency(new DefaultArtifact("io.github.classgraph:classgraph:4.8.174"), null));
        resolver.addRepository(new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build());
        classpathBuilder.addLibrary(resolver);
    }
}

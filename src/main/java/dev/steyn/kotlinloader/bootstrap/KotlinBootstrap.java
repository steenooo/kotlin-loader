package dev.steyn.kotlinloader.bootstrap;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.configuration.file.FileConfiguration;

public class KotlinBootstrap {


    static final String JETBRAINS_GROUPID = "org.jetbrains";
    static final List<KotlinLibrary> LIBRARIES;

    static {
        LIBRARIES = Lists.newArrayList(
            new KotlinLibrary("coroutines", String.format("%s.kotlinx", JETBRAINS_GROUPID),
                "kotlinx-coroutines-jdk8"),
            new KotlinLibrary("stdlib", String.format("%s.kotlin", JETBRAINS_GROUPID),
                "kotlin-stdlib-jdk8"),
            new KotlinLibrary("reflect", String.format("%s.kotlin", JETBRAINS_GROUPID),
                "kotlin-reflect"));

    }

    public void init(KotlinLoaderPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        File libraries = new File(plugin.getDataFolder(), "libraries");
        if (!libraries.exists()) {
            libraries.mkdirs();
        }
        Repository repository = new Repository(config.getString("kotlin.repository"));
        List<MavenDependency> dependencies = LIBRARIES.stream().map(l -> l.toDependency(config))
            .collect(
                Collectors.toList());
        for (MavenDependency dependency : dependencies) {
            File file = repository.download(libraries, dependency);
            try {
                addFileToLoader(plugin.getClass().getClassLoader(), file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void addFileToLoader(ClassLoader loader, File file) throws Exception {
        Method m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
        m.setAccessible(true);
        m.invoke(loader, file.toURI().toURL());
    }


    static class Repository {

        private final String url;

        public Repository(String url) {
            this.url = url;
        }

        public File download(File folder, MavenDependency dependency) {
            File file = new File(folder,
                String.format("%s-%s.jar", dependency.artifactId, dependency.version));
            if (!file.exists()) {
                KotlinLoaderPlugin.getInstance().getLogger()
                    .info("Downloading " + dependency.toString() + "..");
                try {
                    file.createNewFile();
                    URL url = new URL(String.format("%s%s/%s/%s/%s",
                        this.url,
                        dependency.groupId.replace(".", "/"),
                        dependency.artifactId, dependency.version,
                        String.format("%s-%s.jar", dependency.artifactId, dependency.version)));
                    try (BufferedInputStream stream = new BufferedInputStream(url.openStream())) {
                        try (FileOutputStream fileStream = new FileOutputStream(file)) {
                            ByteStreams.copy(stream, fileStream);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return file;
        }
    }


    static class MavenDependency {

        private final String groupId;
        private final String artifactId;
        private final String version;

        public MavenDependency(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        @Override
        public String toString() {
            return String.format("%s:%s:%s", groupId, artifactId, version);
        }
    }

    static class KotlinLibrary {

        private final String name;
        private final String groupId;
        private final String artifactId;

        public KotlinLibrary(String name, String groupId, String artifactId) {
            this.name = name;
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        public MavenDependency toDependency(String version) {
            return new MavenDependency(groupId, artifactId, version);
        }

        public MavenDependency toDependency(FileConfiguration configuration) {
            return toDependency(configuration.getString(String.format("kotlin.library.%s", name)));
        }
    }


}

package gg.summit.customarmor.db;

import gg.summit.customarmor.SummitCustomArmor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Downloads HikariCP and the MariaDB JDBC driver from Maven Central on first run,
 * then loads them into a child URLClassLoader so the plugin jar stays small.
 */
public class LibraryLoader {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    private static final Lib[] LIBS = {
        new Lib("com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar",           "HikariCP-5.1.0.jar"),
        new Lib("org/mariadb/jdbc/mariadb-java-client/3.4.1/mariadb-java-client-3.4.1.jar", "mariadb-java-client-3.4.1.jar"),
        new Lib("org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar",        "slf4j-api-1.7.36.jar"),
        new Lib("org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar",  "slf4j-simple-1.7.36.jar"),
    };

    private final SummitCustomArmor plugin;
    private URLClassLoader classLoader;

    public LibraryLoader(SummitCustomArmor plugin) {
        this.plugin = plugin;
    }

    /**
     * Ensures all library jars are present in plugins/SummitCustomArmor/libs/
     * and returns a URLClassLoader containing them.
     */
    public URLClassLoader load() throws Exception {
        File libDir = new File(plugin.getDataFolder(), "libs");
        libDir.mkdirs();

        URL[] urls = new URL[LIBS.length];
        for (int i = 0; i < LIBS.length; i++) {
            File jar = new File(libDir, LIBS[i].fileName);
            if (!jar.exists()) {
                plugin.getLogger().info("[DB] Downloading " + LIBS[i].fileName + " ...");
                download(MAVEN_CENTRAL + LIBS[i].path, jar);
                plugin.getLogger().info("[DB] Downloaded " + LIBS[i].fileName);
            }
            urls[i] = jar.toURI().toURL();
        }

        classLoader = new URLClassLoader(urls, plugin.getClass().getClassLoader());
        return classLoader;
    }

    public URLClassLoader getClassLoader() {
        return classLoader;
    }

    private void download(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        try (InputStream in = url.openStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            in.transferTo(out);
        }
    }

    private record Lib(String path, String fileName) {}
}

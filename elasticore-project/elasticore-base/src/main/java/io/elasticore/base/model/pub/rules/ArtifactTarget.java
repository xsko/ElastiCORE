package io.elasticore.base.model.pub.rules;

/**
 * Output identity of a generated artifact: where the file goes and what it
 * is called. Replaces the 2.x assumption that every artifact is a Java
 * class under src/main/java addressed by a "scheme://fqcn" string
 * (QualifiedClassMetaInfo supports only the java and proto schemes).
 *
 * Declaring path root and extension per artifact is what makes non-Java
 * outputs (.proto, .xml, .sql, .ts) first-class.
 */
public final class ArtifactTarget {

    private final String scheme;
    private final String pathRoot;
    private final String extension;
    private final String packageName;
    private final String fileName;

    /** True when the package name maps to nested directories (Java convention). */
    private final boolean packageAsDirectories;

    private ArtifactTarget(String scheme, String pathRoot, String extension,
                           String packageName, String fileName, boolean packageAsDirectories) {
        this.scheme = scheme;
        this.pathRoot = pathRoot;
        this.extension = extension;
        this.packageName = packageName;
        this.fileName = fileName;
        this.packageAsDirectories = packageAsDirectories;
    }

    /** Java source: src/main/java/<package as dirs>/<Name>.java */
    public static ArtifactTarget java(String packageName, String className) {
        return new ArtifactTarget("java", "src/main/java", "java", packageName, className, true);
    }

    /** Kotlin source: src/main/kotlin/<package as dirs>/<Name>.kt */
    public static ArtifactTarget kotlin(String packageName, String className) {
        return new ArtifactTarget("kotlin", "src/main/kotlin", "kt", packageName, className, true);
    }

    /** Proto definition: src/main/proto/<Name>.proto (package is an option, not a path) */
    public static ArtifactTarget proto(String javaPackage, String serviceName) {
        return new ArtifactTarget("proto", "src/main/proto", "proto", javaPackage, serviceName, false);
    }

    /** Resource file, e.g. a MyBatis mapper XML under src/main/resources. */
    public static ArtifactTarget resource(String pathRoot, String extension,
                                          String relativeDir, String fileName) {
        return new ArtifactTarget("resource", pathRoot, extension, relativeDir, fileName, true);
    }

    public static ArtifactTarget of(String scheme, String pathRoot, String extension,
                                    String packageName, String fileName, boolean packageAsDirectories) {
        return new ArtifactTarget(scheme, pathRoot, extension, packageName, fileName, packageAsDirectories);
    }

    public String scheme() {
        return scheme;
    }

    public String pathRoot() {
        return pathRoot;
    }

    public String extension() {
        return extension;
    }

    public String packageName() {
        return packageName;
    }

    public String fileName() {
        return fileName;
    }

    /** Path relative to the module root, using forward slashes. */
    public String relativePath() {
        StringBuilder sb = new StringBuilder(pathRoot);
        if (packageAsDirectories && packageName != null && !packageName.isEmpty()) {
            sb.append('/').append(packageName.replace('.', '/'));
        }
        return sb.append('/').append(fileName).append('.').append(extension).toString();
    }

    @Override
    public String toString() {
        return scheme + "://" + relativePath();
    }
}

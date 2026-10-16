package io.elasticore.base.extract;

import io.elasticore.base.SourceFileAccessFactory;
import io.elasticore.base.util.ConsoleLog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;


public class MultiFileBasedSourceFileAccessFactory implements SourceFileAccessFactory {

    protected String rootDir;

    // Module redirect: module alias -> absolute module root path
    private Map<String, String> moduleRootMap;
    // Package prefix -> module alias for redirect resolution
    private Map<String, String> packageModuleMap;

    public MultiFileBasedSourceFileAccessFactory(String rootDir) {
        this.rootDir = rootDir;
    }

    /**
     * Set module redirect mappings for cross-module code generation.
     * @param moduleRootMap module alias -> absolute path (e.g. "app-admin" -> "/path/to/app-admin")
     * @param packageModuleMap package prefix -> module alias (e.g. "com.acme.shop.domain.service" -> "app-admin")
     */
    public void setModuleRedirect(Map<String, String> moduleRootMap, Map<String, String> packageModuleMap) {
        this.moduleRootMap = moduleRootMap;
        this.packageModuleMap = packageModuleMap;
    }

    /**
     * Resolve the root directory for a given qualified class name.
     * If the class package matches a module redirect, returns the redirected module's root.
     */
    private String resolveRootDir(String qualifiedClassName) {
        if (packageModuleMap == null || moduleRootMap == null || qualifiedClassName == null) {
            return rootDir;
        }
        for (Map.Entry<String, String> entry : packageModuleMap.entrySet()) {
            String pkgPrefix = entry.getKey();
            if (qualifiedClassName.startsWith(pkgPrefix)) {
                String moduleAlias = entry.getValue();
                String modulePath = moduleRootMap.get(moduleAlias);
                if (modulePath != null) {
                    return modulePath;
                }
            }
        }
        return rootDir;
    }

    protected File getFile(String qualifiedClassName, boolean makeDirs) {
        QualifiedClassMetaInfo meta = QualifiedClassMetaInfo.of(qualifiedClassName);

        String srcFileName = meta.getSourcePath();

        String resolvedRoot = resolveRootDir(qualifiedClassName);
        String srcPath = resolvedRoot + "/" + srcFileName;

        File f = new File(srcPath);
        if(makeDirs) {
            File dir = f.getParentFile(); //
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
        return f;
    }

    @Override
    public String getFilePath(String qualifiedClassName) {
        // Validation writes nothing, a directory included: asking where a file
        // goes made its package directory, so a check of a missing file left
        // an empty one behind.
        File f = getFile(qualifiedClassName, !ConsoleLog.isValidationMode());
        return f.getAbsolutePath();
    }

    @Override
    public Writer getWriter(String qualifiedClassName) throws IOException {

        File f = getFile(qualifiedClassName ,true);
        //System.err.println("published: "+f.getAbsolutePath() +" "+f.exists());
        ConsoleLog.storeLog("PUBLISH", getFilePathInfo(f.getAbsolutePath())+" "+(f.exists()?"!":""));

        return FileBasedSourceFileAccessFactory.newPlatformLineWriter(f);
    }

    protected static String userHome = System.getProperty("user.dir");
    protected String getFilePathInfo(String path) {


        Path input = Paths.get(path);
        Path home = Paths.get(userHome);
        if (input.startsWith(home)) {
            Path relativePath = home.relativize(input);
            return "." + File.separator + relativePath.toString();
        } else {
            return path;
        }
    }

    @Override
    public Reader getReader(String qualifiedClassName) throws IOException {
        File f = getFile(qualifiedClassName, !ConsoleLog.isValidationMode());
        return new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
    }

    @Override
    public boolean delete(String qualifiedClassName) {
        File f = getFile(qualifiedClassName ,false);
        if(f.exists()) {
            return f.delete();
        }
        return false;
    }
}

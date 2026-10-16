package io.elasticore.base.extract;

import io.elasticore.base.SourceFileAccessFactory;
import io.elasticore.base.util.ConsoleLog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

@Deprecated
public class FileBasedSourceFileAccessFactory implements SourceFileAccessFactory {

    protected String baseDir;

    public FileBasedSourceFileAccessFactory(String baseDir) {
        this.baseDir = baseDir;
    }


    protected static String convertToFilePath(String qualifiedClassName, String extName) {
        return qualifiedClassName.replace('.', '/') + "." + extName;
    }

    protected File getFile(String qualifiedClassName, boolean makeDirs) {
        String srcFileName = convertToFilePath(qualifiedClassName, "java");

        String srcPath = baseDir + "/" + srcFileName;

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

        return newPlatformLineWriter(f);
    }

    /**
     * Creates a Writer that converts LF to the platform line separator (CRLF on Windows).
     */
    public static Writer newPlatformLineWriter(File f) throws IOException {
        Writer utf8Writer = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8);
        String ls = System.lineSeparator();
        if ("\n".equals(ls)) {
            return utf8Writer;
        }
        // Wrap UTF-8 writer to replace LF with platform line separator
        return new FilterWriter(utf8Writer) {
            @Override
            public void write(String str, int off, int len) throws IOException {
                String s = str.substring(off, off + len);
                s = s.replace("\r\n", "\n").replace("\n", ls);
                super.write(s, 0, s.length());
            }

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                write(new String(cbuf, off, len), 0, len);
            }

            @Override
            public void write(int c) throws IOException {
                if (c == '\n') {
                    super.write(ls, 0, ls.length());
                } else {
                    super.write(c);
                }
            }
        };
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

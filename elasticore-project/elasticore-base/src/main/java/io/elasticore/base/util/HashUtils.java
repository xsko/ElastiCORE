package io.elasticore.base.util;

import io.elasticore.base.Version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class HashUtils {

    private static final String STX = "// !----------";
    private static final String ETX = "------------!";



    private static final boolean SKIP_BLANK_LINE = true;


    public static String getHashCode(String content) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String currentDate = sdf.format(new Date());


        int hashCode = 0;
        if (content != null) {
            //hashCode = content.hashCode();
            hashCode = generateHashCode(content, SKIP_BLANK_LINE);
        }

        return hashCode + "H" + currentDate;
    }


    public static boolean checkHashCode(String content, String hashcode, String version) {
        if (content == null || hashcode == null)
            return false;

        int sp = hashcode.indexOf("H");
        int hashCode = Integer.valueOf(hashcode.substring(0, sp));

        int cntHashCode =0;
        if(version.indexOf("0.")>=0 ) {
            // for old version
            cntHashCode = generateHashCode(content , false);
        }else {
            cntHashCode = generateHashCode(content , SKIP_BLANK_LINE);
        }

        if (hashCode != cntHashCode)
            return false;

        return true;
    }


    public static String checkHashCodeErrMsg(String content, String hashcode, String version) {
        if (content == null || hashcode == null)
            return "NULL";

        int sp = hashcode.indexOf("H");
        int hashCode = Integer.valueOf(hashcode.substring(0, sp));

        int cntHashCode =0;
        if(version.indexOf("0.")>=0 ) {
            // for old version
            cntHashCode = generateHashCode(content , false);
        }else {
            cntHashCode = generateHashCode(content , SKIP_BLANK_LINE);
        }

        if (hashCode != cntHashCode)
            return hashCode+ " != "+cntHashCode;

        return null;
    }

    public static int generateHashCode(String input, boolean skipBlankline) {
        if(input==null)
            return -1;

        if(!skipBlankline)
            return input.hashCode();

        StringBuilder processedString = new StringBuilder();

        Scanner scanner = new Scanner(input);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                processedString.append(line).append("\n");
            }
        }
        scanner.close();
        return processedString.toString().hashCode();

    }


    public static String makeEcdCode(String content) {
        String ecd = "ecd:" + HashUtils.getHashCode(content) + "_V" + Version.MAJOR;
        return "//" + ecd + "\n";
    }

    public static String getVersion(String lineEcdCode) {
        int sp = lineEcdCode.indexOf("_V");
        String version = lineEcdCode.substring(sp+2);
        // A stamp inside a comment of another language - an XML mapper's
        // "<!-- //ecd:..._V3.0 -->" - ends where the version does.
        int end = version.indexOf(' ');
        return end < 0 ? version : version.substring(0, end);
    }

    /**
     * The stamp line for a file of the given extension: as a Java comment, or
     * inside the comment of a language that has no // - XML, whose
     * declaration may be preceded by nothing else, and SQL.
     */
    public static String makeEcdCode(String content, String extension) {
        String line = makeEcdCode(content);
        if ("xml".equals(extension)) return "<!-- " + line.trim() + " -->\n";
        if ("sql".equals(extension)) return "-- " + line;
        return line;
    }

    public static String extractEcdCode(String lineEcdCode) {
        String startToken = "//ecd:";
        String endToken = "_V";

        int startIndex = lineEcdCode.indexOf(startToken);
        int endIndex = lineEcdCode.indexOf(endToken);

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            startIndex += startToken.length();
            return lineEcdCode.substring(startIndex, endIndex);
        }

        return null;
    }


    /**
     *
     * @param fileReader
     * @return
     */
    public static Response checkContentModified(Reader fileReader) {
        String originalEcdCode = null;
        String content = null;
        String customizedScope = null;
        String customizedHeaderScope = null;
        try (BufferedReader reader = new BufferedReader(fileReader)) {

            String firstLine = reader.readLine();
            if (firstLine == null) {
                return new Response(null, NO_CONTENT, null ,customizedScope, customizedHeaderScope);
            }


            originalEcdCode = extractEcdCode(firstLine);

            String version = getVersion(firstLine);


            // Read remaining file content and combine into string
            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder customizedScopeContent = new StringBuilder();
            StringBuilder customizedScopeContent4Header = null; // for header area (ex import )
            String line;

            boolean hasImportCodeLine = false;
            boolean isCustomizedScope = false;
            while ((line = reader.readLine()) != null) {
                if(line.indexOf(STX)>=0){
                    isCustomizedScope = true;
                    hasImportCodeLine = false;
                }
                else if(isCustomizedScope && line.indexOf(ETX)>=0 && line.indexOf("//")>=0){

                    customizedScopeContent.append(line).append("\n");

                    if(hasImportCodeLine) {
                        customizedScopeContent4Header = customizedScopeContent;
                        customizedScopeContent = new StringBuilder();
                    }
                    isCustomizedScope = false;
                    hasImportCodeLine = false;

                    continue;
                }


                if(isCustomizedScope) {
                    if(line !=null && line.indexOf("import ")==0)
                        hasImportCodeLine = true;

                    customizedScopeContent.append(line).append("\n");
                }
                else
                    contentBuilder.append(line).append("\n");
            }
            content = contentBuilder.toString();

            customizedScope = customizedScopeContent.toString();
            if(customizedScopeContent4Header !=null)
                customizedHeaderScope = customizedScopeContent4Header.toString();

            boolean isUntrackedSource =false;

            int chkP =content.indexOf("// Initially generated by ElastiCORE;");
            if(chkP>=0 && chkP<200) {
                isUntrackedSource =true;
            }

            // The "Initially generated by ElastiCORE; no longer managed" marker means the user has
            // taken ownership of the file and may modify it freely. Always report as UNTRACKED so
            // validation and regeneration both skip the file, regardless of hash status.
            if(isUntrackedSource) {
                return new Response(content, UNTRACKED, originalEcdCode, customizedScope ,customizedHeaderScope ,firstLine);
            }

            if(originalEcdCode==null) {
                return new Response(content, NO_ECD, null ,null, customizedScope ,customizedHeaderScope);
            }


            if( checkHashCode(content, originalEcdCode ,version)) {
                return new Response(content, NO_MODIFIED, originalEcdCode, customizedScope ,customizedHeaderScope ,firstLine);
            }else {
                String errMSg = checkHashCodeErrMsg(content, originalEcdCode ,version);
                Response re =  new Response(content, MODIFIED, originalEcdCode ,customizedScope ,customizedHeaderScope);
                re.setErrMsg(errMSg);
                return re;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Response(content, ERROR, originalEcdCode ,customizedScope ,customizedHeaderScope);
    }

    public final static int ERROR = -99;

    public final static int NO_CONTENT = -1;

    public final static int NO_ECD = 0;

    public final static int NO_MODIFIED = 1;

    public final static int MODIFIED = 10;

    public final static int UNTRACKED = 20;



    public final static class Response {
        private final String preSourceContent;
        private final int status;
        private final String ecd;

        private final String customizedScopeContent;
        private final String customizedScopeContent4Header;

        private String oldEcdLine;

        private String errMSg;

        public Response(String preSourceContent, int status, String ecd , String customizedScopeContent, String customizedScopeContent4Header) {
            this(preSourceContent,status,ecd,customizedScopeContent,customizedScopeContent4Header,null);
        }

        public Response(String preSourceContent, int status, String ecd , String customizedScopeContent, String customizedScopeContent4Header, String oldEcdLine) {
            this.preSourceContent = preSourceContent;
            this.status = status;
            this.ecd = ecd;
            if(customizedScopeContent!=null && !customizedScopeContent.isEmpty())
                this.customizedScopeContent = customizedScopeContent;
            else
                this.customizedScopeContent = null;

            if(customizedScopeContent4Header!=null && !customizedScopeContent4Header.isEmpty())
                this.customizedScopeContent4Header = customizedScopeContent4Header;
            else
                this.customizedScopeContent4Header = null;

            this.oldEcdLine = oldEcdLine;
        }

        public void setErrMsg(String errMsg) {
            this.errMSg = errMsg;
        }

        public String getErrMsg() {
            return this.errMSg;
        }

        public void setOldEcdLine(String oldEcdLine) {
            this.oldEcdLine = oldEcdLine;
        }

        public String getOldEcdLine() {
            return this.oldEcdLine;
        }

        public String getPreSourceContent() {
            return this.preSourceContent;
        }

        public String getEcd() {
            return this.ecd;
        }

        public int getStatus() {
            return this.status;
        }

        public String getCustomizedScopeContent() {
            return this.customizedScopeContent;
        }

        public String getCustomizedScopeContent4Header() {
            return this.customizedScopeContent4Header;
        }

        public boolean hasCustomizedScopeContent() {
            boolean hasCustomizedContent = this.customizedScopeContent != null && !this.customizedScopeContent.isEmpty();
            boolean hasCustomizedScopeContent4Header = this.customizedScopeContent4Header != null && !this.customizedScopeContent4Header.isEmpty();

            return hasCustomizedContent || hasCustomizedScopeContent4Header;
        }


    }


}

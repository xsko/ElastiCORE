package io.elasticore.base.model.loader.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.ModelComponentItems;
import io.elasticore.base.model.core.BaseModelDomain;
import io.elasticore.base.model.core.Items;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.dto.DataTransferModels;
import io.elasticore.base.model.entity.BaseFieldType;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityModels;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.entity.TypeInfo;
import io.elasticore.base.model.enums.EnumModel;
import io.elasticore.base.model.enums.EnumModels;
import io.elasticore.base.model.loader.FileSource;
import io.elasticore.base.model.loader.MainModelLoader;
import io.elasticore.base.model.loader.ModelLoaderContext;
import io.elasticore.base.model.port.Port;
import io.elasticore.base.model.port.PortModels;
import io.elasticore.base.model.repo.Repository;
import io.elasticore.base.model.repo.RepositoryModels;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.FileOrder;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YamlFileMainModelLoader implements MainModelLoader {

    private static ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    private String templateFileDirPath;
    private List<FileSource> fileSources = new ArrayList<>();

    public YamlFileMainModelLoader(String filePath) {
        this.templateFileDirPath = filePath;
    }


    @Override
    public ECoreModel load(ModelLoaderContext context) {

        List<String> resFilePathList = context.getConfigList("resource");

        // Determine whether this domain is excluded by the -Pdomain filter.
        // When excluded, we must still load DSL (so cross-domain references resolve) but
        // skip HTTP refresh --reuse whatever is already cached under _resource/.
        boolean domainExcludedByFilter = false;
        {
            String domainFilter = System.getProperty("elasticore.domain");
            if (domainFilter != null && domainFilter.trim().length() > 0) {
                String needle = "," + domainFilter.trim() + ",";
                domainExcludedByFilter = (needle.indexOf("," + context.getDomainId() + ",") < 0);
            }
        }

        if (resFilePathList != null && resFilePathList.size()>0) {
            for(int i = 0; i < resFilePathList.size(); i++) {
                String uri = resFilePathList.get(i);

                boolean isHttp = uri.startsWith("http://") || uri.startsWith("https://");
                // Use local cache (no network call) when:
                //   (a) validation mode is active, or
                //   (b) this domain is excluded by the -Pdomain filter.
                // Both cases still need the DSL contents for model aggregation, but must not
                // touch the remote endpoint or modify the cached file.
                if (isHttp && (ConsoleLog.isValidationMode() || domainExcludedByFilter)) {
                    File resourceDir = new File(context.getBaseFilePath(), "_resource");
                    File cachedFile = new File(resourceDir, "model-" + (i + 1) + ".yml");
                    if (cachedFile.exists()) {
                        FileSource fileSource = getLoadData(cachedFile);
                        if (fileSource != null) {
                            fileSources.add(fileSource);
                        }
                        ConsoleLog.storeLog("RESOURCE_CACHE", cachedFile.getName());
                    } else {
                        String reason = domainExcludedByFilter ? "[DomainFilter]" : "[VALID]";
                        ConsoleLog.printWarn(reason + " Skipping remote resource (no cache): " + uri);
                    }
                    continue;
                }

                try {
                    File file = getOrDownload(uri, i, context.getBaseFilePath());
                    FileSource fileSource = getLoadData(file);
                    if(fileSource!=null) {
                        fileSources.add(fileSource);
                    }

                }catch (IOException e) {
                    throw new RuntimeException( "Failed to load resource " + uri, e);
                }
            }
        }


        File f = new File(templateFileDirPath);
        loadData(f, fileSources);

        io.elasticore.base.model.loader.ModelLoader<Entity> entityModelLoader = new EntityModelLoader();
        io.elasticore.base.model.loader.ModelLoader<EnumModel> enumerationModelLoader = new EnumerationModelLoader();
        io.elasticore.base.model.loader.ModelLoader<DataTransfer> dataTransferModelLoader = new DataTransferModelLoader();
        io.elasticore.base.model.loader.ModelLoader<Repository> repositoryModelLoader = new RepositoryModelLoader();
        io.elasticore.base.model.loader.ModelLoader<Port> portModelLoader = new PortModelLoader();
        io.elasticore.base.model.loader.ModelLoader<io.elasticore.base.model.domainservice.DomainService>
                domainServiceModelLoader = new DomainServiceModelLoader();





        for (FileSource fileSource : fileSources) {

            Map<String, Map> map = fileSource.getInfoMap();

            if (map == null) {
                continue;
            }

            entityModelLoader.loadModel(context, fileSource);
            enumerationModelLoader.loadModel(context, fileSource);
            dataTransferModelLoader.loadModel(context, fileSource);
            repositoryModelLoader.loadModel(context, fileSource);
            portModelLoader.loadModel(context, fileSource);
            domainServiceModelLoader.loadModel(context, fileSource);
        }

        entityModelLoader.completeLoad();
        enumerationModelLoader.completeLoad();
        dataTransferModelLoader.completeLoad();
        portModelLoader.completeLoad();

        // Type reference validation moved to ModelExtractor.validateGlobalTypeReferences()
        // It now runs after ALL domains/modules are loaded so cross-domain references resolve correctly.

        EntityModels entityModels = EntityModels.create(context.getDomainId(), "entityGrp", null, context.getEntityItems());
        EnumModels enumModels = EnumModels.create(context.getDomainId(), "enumGroup", null, context.getEnumModelItems());
        DataTransferModels dataTransferModels = DataTransferModels.create(context.getDomainId(), "dto", null, context.getDataTransferItems());
        PortModels portModels = PortModels.create(context.getDomainId(), "port", null, context.getPortItems());


        ECoreModel tmpModel = ECoreModel.builder()
                .entityModels(entityModels)
                .enumModels(enumModels)
                .internalDomainName(context.getDomainId())
                .dataTransferModels(dataTransferModels).build();
        BaseModelDomain.setCurrentModel(tmpModel);



        repositoryModelLoader.completeLoad();
        RepositoryModels repositoryModels = RepositoryModels.create(context.getDomainId(), "repoGroup", null, context.getRepositoryItems());

        //EnumModels enumModels = getEnumModels();
        //EntityModels entityModels = getEntityModels();

        return ECoreModel.builder()
                .entityModels(entityModels)
                .enumModels(enumModels)
                .dataTransferModels(dataTransferModels)
                .repositoryModels(repositoryModels)
                .portModels(portModels)
                .domainServiceModels(domainServiceModels(context))
                .metrics(context.getMetrics())
                .configMap(context.getConfigMap())
                .namespaceMap(context.getNsMap())
                .namespaceModuleRedirectMap(context.getModuleRedirectMap())
                .internalDomainName(context.getDomainId())
                .build();
    }

    /** The domain services this context loaded, or null when it loaded none. */
    private io.elasticore.base.model.domainservice.DomainServiceModels domainServiceModels(
            io.elasticore.base.model.loader.ModelLoaderContext context) {
        if (context.getDomainServiceItems() == null
                || context.getDomainServiceItems().size() == 0) {
            return null;
        }
        return io.elasticore.base.model.domainservice.DomainServiceModels.create(
                context.getDomainId(), "domainServiceGroup", null,
                context.getDomainServiceItems());
    }

    private File getOrDownload(String path, int index, String baseDir) throws IOException {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            // Create _resource directory relative to baseDir (env.yml location)
            File resourceDir = new File(baseDir, "_resource");
            if (!resourceDir.exists()) {
                resourceDir.mkdirs();
                ConsoleLog.print("[RESOURCE] Created directory: " + resourceDir.getAbsolutePath());
            }

            // Create model file with index-based naming
            File modelFile = new File(resourceDir, "model-" + (index + 1) + ".yml");
            boolean fileExisted = modelFile.exists();

            // Download to memory first
            byte[] downloadedContent;
            try (InputStream in = openStreamFollowRedirects(path);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                downloadedContent = baos.toByteArray();
            }

            // Compare with existing file - skip if content is identical
            if (fileExisted && isContentEqual(modelFile, downloadedContent)) {
                ConsoleLog.print("[RESOURCE] Unchanged, skipping: " + modelFile.getAbsolutePath());
                return modelFile;
            }

            // Write if new or different
            try (OutputStream out = new FileOutputStream(modelFile)) {
                out.write(downloadedContent);
            }

            if (fileExisted) {
                ConsoleLog.print("[RESOURCE] Updated: " + modelFile.getAbsolutePath());
            } else {
                ConsoleLog.print("[RESOURCE] Downloaded and saved: " + modelFile.getAbsolutePath());
            }

            return modelFile;
        } else {
            File file = new File(path);
            if (!file.exists()) throw new FileNotFoundException("File not found: " + path);
            return file;
        }
    }

    private boolean isContentEqual(File file, byte[] newContent) throws IOException {
        // Read existing file content
        byte[] existingContent;
        try (InputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            existingContent = baos.toByteArray();
        }

        // Normalize line endings: remove all \r to compare LF vs CRLF as equal
        byte[] normalizedExisting = removeCarriageReturns(existingContent);
        byte[] normalizedNew = removeCarriageReturns(newContent);

        return java.util.Arrays.equals(normalizedExisting, normalizedNew);
    }

    private byte[] removeCarriageReturns(byte[] content) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (byte b : content) {
            if (b != '\r') {
                result.write(b);
            }
        }
        return result.toByteArray();
    }

    private InputStream openStreamFollowRedirects(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(false); // Manual redirect handling

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == 307 || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            if (newUrl == null) throw new IOException("Redirect with no Location header");
            return openStreamFollowRedirects(newUrl); // Recursive redirect following
        }

        return conn.getInputStream();
    }



    private void loadData(File f, List<FileSource> fileSources) {

        if (f.isDirectory()) {
            File[] files = FileOrder.list(f);
            for (File chidFile : files) {
                loadData(chidFile, fileSources);
            }
        } else {

            FileSource fs = getLoadData(f);
            if (fs != null)
                fileSources.add(fs);
        }
    }


    private FileSource getLoadData(File f) {

        if (!f.getName().endsWith(".yml") && !f.getName().endsWith(".yaml")) {
            return null;
        }

        if(f.length()==0) {
            ConsoleLog.printWarn(" * EMPTY: "+f.getAbsolutePath());
            return null;
        }

        FileSource fileSource = null;

        try {
            Map map = mapper.readValue(f, LinkedHashMap.class);
            return FileSource.builder().filepath(f.getAbsolutePath()).infoMap(map).build();
        } catch (Throwable e) {
            // Only comments or blank lines - a model file switched off by commenting it
            // out, or a new one not written yet: it declares nothing, like an empty file.
            if (e instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException && holdsNothing(f)) {
                ConsoleLog.printWarn(" * EMPTY: "+f.getAbsolutePath());
                return null;
            }
            ConsoleLog.printWarn(" * LOAD ERROR: "+f.getAbsolutePath());
            e.printStackTrace();
            // The rule engine stops the build on it: everything the file declares is missing,
            // and the compile errors that follow point anywhere but here.
            String why = String.valueOf(e.getMessage()).replaceAll("\\s+", " ").trim();
            if (why.length() > 240) why = why.substring(0, 240) + " ...";
            io.elasticore.base.model.pub.rules.ModelCheck.failSaid(
                    "LOAD ERROR " + f.getAbsolutePath() + " - not read, nothing it declares was generated: " + why);
            return FileSource.builder().filepath(f.getAbsolutePath()).error(e).build();
        }

    }

    /** Whether the YAML file holds no document content at all - no token, only comments and blank lines. */
    static boolean holdsNothing(File f) {
        try (com.fasterxml.jackson.core.JsonParser parser = mapper.getFactory().createParser(f)) {
            return parser.nextToken() == null;
        } catch (Throwable notReadable) {
            return false;
        }
    }

    /**
     * Validates that all field type references resolve to known Entity, DTO, or Enum definitions.
     * Logs DSL_WARN for any unresolved references to prevent silent failures during code generation.
     */
    private void validateTypeReferences(ModelLoaderContext context) {
        Items<Entity> entities = context.getEntityItems();
        Items<EnumModel> enums = context.getEnumModelItems();
        Items<DataTransfer> dtos = context.getDataTransferItems();

        // Validate Entity fields
        for (Entity entity : entities.getItemList()) {
            validateComponentFields(entity.getIdentity().getName(), entity.getItems(), entities, enums, dtos, context.getDomainId());
        }

        // Validate DTO fields
        for (DataTransfer dto : dtos.getItemList()) {
            validateComponentFields(dto.getIdentity().getName(), dto.getItems(), entities, enums, dtos, context.getDomainId());
        }
    }

    /**
     * Validates fields of a single model component for unresolved type references.
     */
    private void validateComponentFields(String componentName, ModelComponentItems<Field> fieldItems,
                                         Items<Entity> entities, Items<EnumModel> enums, Items<DataTransfer> dtos,
                                         String domainId) {
        if (fieldItems == null) return;

        int size = fieldItems.size();
        for (int i = 0; i < size; i++) {
            Field field = fieldItems.get(i);
            if (field == null) continue;

            // Skip disabled fields (fields with value "--" become "undefined @disable")
            if (field.hasAnnotation("disable")) continue;

            TypeInfo ti = field.getTypeInfo();
            if (ti == null) continue;

            String typeToCheck = null;

            if (ti.getBaseFieldType() == BaseFieldType.UNKNOWN) {
                // Simple user-defined type reference (e.g., "OrderStatus", "Category")
                typeToCheck = ti.getBaseTypeName();
            } else if (ti.isList() && ti.getTypeParameterName() != null && !ti.isTypeParameterBaseType()) {
                // Generic list with user-defined element type (e.g., "List<OrderItem>")
                typeToCheck = ti.getTypeParameterName();
            }

            if (typeToCheck == null) continue;

            // Skip fully qualified class names (e.g., "java.util.HashMap")
            if (typeToCheck.contains(".")) continue;

            // Skip cross-domain references (e.g., "otherdomain:SomeEntity")
            if (typeToCheck.contains(":")) continue;

            boolean resolved = entities.findByName(typeToCheck) != null
                    || enums.findByName(typeToCheck) != null
                    || dtos.findByName(typeToCheck) != null;

            if (!resolved) {
                ConsoleLog.storeLog("DSL_WARN",
                        String.format("[%s] %s.%s: unresolved type '%s' - no matching Entity, DTO, or Enum found in domain '%s'",
                                domainId, componentName, field.getName(), typeToCheck, domainId));
            }
        }

        // Validate @fk annotations: check that target entity (and optional field) exists
        for (int i = 0; i < size; i++) {
            Field field = fieldItems.get(i);
            if (field == null) continue;
            if (!field.hasAnnotation(io.elasticore.base.model.entity.EntityAnnotation.FK)) continue;

            String fkValue = field.getAnnotationValue(io.elasticore.base.model.entity.EntityAnnotation.FK);
            if (fkValue == null || fkValue.trim().isEmpty()) {
                ConsoleLog.storeLog("DSL_WARN",
                        String.format("[%s] %s.%s: @fk annotation missing target (use @fk(TargetEntity) or @fk(TargetEntity.field))",
                                domainId, componentName, field.getName()));
                continue;
            }

            // Parse: "Target" or "Target.field" or "Target.field, groupName"
            int commaIdx = fkValue.indexOf(',');
            String targetRef = (commaIdx > 0) ? fkValue.substring(0, commaIdx).trim() : fkValue.trim();
            int dotIdx = targetRef.indexOf('.');
            String targetEntityName = (dotIdx > 0) ? targetRef.substring(0, dotIdx) : targetRef;
            String targetFieldName = (dotIdx > 0) ? targetRef.substring(dotIdx + 1).trim() : null;

            // Skip cross-domain references
            if (targetEntityName.contains(":")) continue;

            Entity targetEntity = entities.findByName(targetEntityName);
            if (targetEntity == null) {
                ConsoleLog.storeLog("DSL_WARN",
                        String.format("[%s] %s.%s: @fk target entity '%s' not found in domain '%s'",
                                domainId, componentName, field.getName(), targetEntityName, domainId));
                continue;
            }

            // Validate target field if specified
            if (targetFieldName != null && !targetFieldName.isEmpty()) {
                boolean fieldFound = false;
                ModelComponentItems<Field> targetFields = targetEntity.getItems();
                int tfSize = targetFields.size();
                for (int j = 0; j < tfSize; j++) {
                    Field tf = targetFields.get(j);
                    if (tf != null && targetFieldName.equals(tf.getName())) {
                        fieldFound = true;
                        break;
                    }
                }
                if (!fieldFound) {
                    ConsoleLog.storeLog("DSL_WARN",
                            String.format("[%s] %s.%s: @fk target field '%s.%s' not found",
                                    domainId, componentName, field.getName(), targetEntityName, targetFieldName));
                }
            }
        }
    }
}

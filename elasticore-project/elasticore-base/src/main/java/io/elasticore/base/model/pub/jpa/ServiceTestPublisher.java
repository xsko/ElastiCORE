package io.elasticore.base.model.pub.jpa;

import io.elasticore.base.CodePublisher;
import io.elasticore.base.ModelDomain;
import io.elasticore.base.model.*;
import io.elasticore.base.model.core.RelationshipManager;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityAnnotation;
import io.elasticore.base.model.entity.Field;
import io.elasticore.base.model.relation.ModelRelationship;
import io.elasticore.base.model.relation.RelationType;
import io.elasticore.base.util.CodeTemplate;
import io.elasticore.base.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Publisher for generating JUnit 5 test classes for ElastiCORE Service classes.
 * 
 * This publisher generates comprehensive unit tests for service layer methods including:
 * - CRUD operation tests
 * - Search functionality tests
 * - Edge case and error condition tests
 * - Performance validation tests
 * 
 * Generated tests use JUnit 5, Mockito, and Spring Boot Test framework.
 */
public class ServiceTestPublisher extends SrcFilePublisher {

    private CodeTemplate testCodeTmpl;
    private CodePublisher publisher;
    private ECoreModel model;
    private RelationshipManager relationshipManager;
    
    private String testPackageName;
    private String entityPackageName;
    private String dtoPackageName;
    private String repoPackageName;
    private String servicePackageName;

    public ServiceTestPublisher(CodePublisher publisher) {
        super(publisher);

        this.model = publisher.getECoreModelContext().getDomain().getModel();
        
        if (model.getEntityModels().getItems().size() == 0 || model.getDataTransferModels().getItems().size() == 0)
            return;

        this.relationshipManager = RelationshipManager.getInstance(publisher.getECoreModelContext().getDomain().getName());

        // Set up package names for test generation
        this.servicePackageName = model.getNamespace(ConstantParam.KEYNAME_SERVICE);
        if (this.servicePackageName == null) return;

        // Test package follows Maven convention: src/test/java
        this.testPackageName = this.servicePackageName;
        this.entityPackageName = model.getNamespace(ConstantParam.KEYNAME_ENTITY);
        this.dtoPackageName = model.getNamespace(ConstantParam.KEYNAME_DTO);
        this.repoPackageName = model.getNamespace(ConstantParam.KEYNAME_REPOSITORY);

        this.publisher = publisher;

        this.testCodeTmpl = createCodeTemplate(publisher, "template.serviceTest", "service_test.tmpl");
    }

    /**
     * Find the corresponding SearchDTO for an entity
     */
    private DataTransfer findSearchDTO(Entity entity) {
        String entityNm = entity.getIdentity().getName();
        List<ModelRelationship> relationshipList = relationshipManager
                .findByToNameAndType(entityNm, RelationType.SEARCHABLE);

        for (ModelRelationship r : relationshipList) {
            String dtoName = r.getFromName();
            return this.model.getDataTransferModels().findByName(dtoName);
        }
        return null;
    }

    /**
     * Find the mapper class name for a model component
     */
    private String findMapperClassName(DataModelComponent model) {
        List<ModelRelationship> relationshipList = relationshipManager
                .findByFromNameAndType(model.getIdentity().getName(), RelationType.MAPPER);
        for (ModelRelationship r : relationshipList) {
            return r.getToName();
        }
        return null;
    }

    /**
     * Check if the entity is pageable based on search DTO annotations
     */
    private boolean isPageable(DataTransfer searchDTO) {
        if (searchDTO == null) return false;
        
        io.elasticore.base.model.core.Annotation annotation = searchDTO.getMetaInfo()
                .getMetaAnnotation(io.elasticore.base.model.dto.DataTransferAnnotation.META_SEARCHABLE);
        if (annotation == null) return false;

        java.util.Properties props = annotation.getProperties();
        if (props == null) return false;
        
        return props.getProperty("pageSize") != null;
    }

    /**
     * Determine the search result DTO class name for test generation
     */
    private String getSearchResultDTOName(RelationshipManager relationshipManager, Entity entity) {
        List<ModelRelationship> relationshipList = relationshipManager
                .findByFromNameAndType(entity.getIdentity().getName(), RelationType.SEARCH_RESULT);
        
        for (ModelRelationship r : relationshipList) {
            return r.getToName();
        }
        return null;
    }

    /**
     * Main publish method that generates test classes for all eligible service classes
     */
    public void publish(ModelDomain domain) {
        if (this.testCodeTmpl == null) return;

        ModelComponentItems<Entity> entities = this.model.getEntityModels().getItems();

        while (entities.hasNext()) {
            Entity entity = entities.next();
            DataTransfer dto = findDTO(entity);
            if (dto == null) continue;

            DataTransfer searchDTO = findSearchDTO(entity);
            if (searchDTO == null) continue;

            publishTestClass(domain, entity, dto, searchDTO);
        }
    }

    /**
     * Generate test class for a specific entity service
     */
    public void publishTestClass(ModelDomain domain, Entity entity, DataTransfer dto, DataTransfer searchDto) {
        // Check if service generation is enabled for this entity
        boolean hasApiAnt = entity.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_SERVICE) ||
                           entity.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_EXPOSE);
        
        String isGen = entity.getMetaInfo().getInfoAnnotationValue(EntityAnnotation.META_SERVICE);
        if (isGen == null) {
            isGen = entity.getMetaInfo().getInfoAnnotationValue(EntityAnnotation.META_EXPOSE);
        }

        // Skip test generation if service is not generated
        if (!entity.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_EXPOSE) && !entity.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_SERVICE)) {
            return;
        }

        boolean isDefaultGen = !"false".equals(this.model.getConfig("defaultGeneration.service", "true"));
        if ("false".equals(isGen) || (!hasApiAnt && !isDefaultGen))
            return;

        if (entity.getPkField() == null)
            return;

        String domainName = domain.getName();
        String orgEntityClassName = entity.getIdentity().getName();
        String entityClassName = targetEntityName(entity);
        
        // Determine service class name based on annotations
        String serviceClassName;
        boolean isExposeOutput = false;
        boolean isListOutput = false;
        boolean isPageOutput = false;
        
        // If entity has @expose or @service annotation, generate test for extended Service class only
        if (entity.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_EXPOSE) ||
            entity.getMetaInfo().hasMetaAnnotation(EntityAnnotation.META_SERVICE)) {
            serviceClassName = orgEntityClassName + ConstantParam.POSTFIX_EXT_SERVICE; // Regular "Service"
            isExposeOutput = true;
        } else {
            // Otherwise generate test for CoreService
            serviceClassName = orgEntityClassName + ConstantParam.POSTFIX_SERVICE; // CoreService
            if (isPageable(searchDto)) {
                isPageOutput = true;
            } else {
                isListOutput = true;
            }
        }
        
        String testClassName = serviceClassName + "Test";
        String dtoClassName = dto.getIdentity().getName();
        String mapperName = findMapperClassName(dto);
        String searchDTOClassName = searchDto.getIdentity().getName();
        String pkType = entity.getPkField().getType();
        String pkName = entity.getPkField().getName();

        // Determine search result DTO name
        String searchResultDTOClassName = getSearchResultDTOName(this.relationshipManager, entity);
        if (searchResultDTOClassName == null) {
            searchResultDTOClassName = dtoClassName;
        }

        // Build primary key information for test methods
        StringBuilder pkDtoDefine = new StringBuilder();
        StringBuilder pkDtoInfo2 = new StringBuilder();
        
        List<Field> pkFields = new ArrayList<>();
        List<Field> fieldList = entity.getAllFieldListMap().getList();
        for (Field field : fieldList) {
            if (!field.isPrimaryKey()) continue;
            pkFields.add(field);
            
            if (pkDtoDefine.length() > 0) {
                pkDtoDefine.append(", ");
                pkDtoInfo2.append(", ");
            }
            
            pkDtoDefine.append(field.getTypeInfo().getDefaultTypeName()).append(" ").append(field.getName());
            pkDtoInfo2.append(field.getName());
        }
        
        // Detect composite key
        boolean isCompositeKey = pkFields.size() > 1;
        String idConstruction = "";
        String idVariableCreation = "";
        String mockDtoKeySetup = "";
        
        // Generate test data for all entity fields
        StringBuilder allTestData = new StringBuilder();
        Set<String> processedFields = new HashSet<>();
        
        for (Field field : fieldList) {
            if (processedFields.contains(field.getName())) continue;
            processedFields.add(field.getName());
            
            String fieldName = field.getName();
            String sampleValue = generateSampleValue(field);
            
            // Only add setter if sample value is not null (skip auto-generated fields)
            if (sampleValue != null) {
                allTestData.append("        entity.set")
                          .append(StringUtils.capitalize(fieldName))
                          .append("(").append(sampleValue).append(");\n");
            }
        }
        
        if (isCompositeKey) {
            // For composite keys, create variable declarations and method parameters
            StringBuilder constructorArgs = new StringBuilder();
            StringBuilder idVariables = new StringBuilder();
            StringBuilder methodParams = new StringBuilder();
            StringBuilder mockDtoSetup = new StringBuilder();
            
            for (int i = 0; i < pkFields.size(); i++) {
                Field pkField = pkFields.get(i);
                String fieldName = pkField.getName();
                String fieldType = pkField.getTypeInfo().getDefaultTypeName();
                String sampleValue = generateSampleValue(pkField);
                String capitalizedFieldName = StringUtils.capitalize(fieldName);
                
                if (i > 0) {
                    constructorArgs.append(", ");
                    idVariables.append(";\n        ");
                    methodParams.append(", ");
                }
                              
                // For entity constructor (used in factory method)
                constructorArgs.append(fieldName);
                
                // Variable declarations
                idVariables.append(fieldType).append(" ").append(fieldName)
                          .append(" = ").append(sampleValue);
                          
                // For service method parameters (individual fields)
                methodParams.append(fieldName);
                
                // Setup mockDto to return key values
                if (mockDtoSetup.length() > 0) {
                    mockDtoSetup.append("\n        ");
                }
                mockDtoSetup.append("//when(mockDto.get").append(capitalizedFieldName)
                           .append("()).thenReturn(").append(fieldName).append(");");
            }
            
            // For composite keys, store method parameters instead of identity construction
            idConstruction = methodParams.toString();
            idVariableCreation = idVariables.toString();
            mockDtoKeySetup = mockDtoSetup.toString();
            
            // Update pkDtoInfo2 to use direct variable names for entity constructor
            pkDtoInfo2 = new StringBuilder(constructorArgs.toString());
        } else {
            // For single key, use the existing logic
            Field pkField = pkFields.get(0);
            String sampleValue = generateSampleValue(pkField);
            idConstruction = "mockEntity.get" + StringUtils.capitalize(pkField.getName()) + "()";
            idVariableCreation = pkType + " " + pkField.getName() + " = " + sampleValue;
        }
        
        String testDataCreation = allTestData.toString();
        
        // Generate test data for DTO fields (same as entity fields but for DTO)
        StringBuilder allDtoTestData = new StringBuilder();
        Set<String> processedDtoFields = new HashSet<>();
        
        for (Field field : fieldList) {
            if (processedDtoFields.contains(field.getName())) continue;
            processedDtoFields.add(field.getName());
            
            String fieldName = field.getName();
            String sampleValue = generateSampleValue(field);
            
            // Only add setter if sample value is not null (skip auto-generated fields)
            if (sampleValue != null) {
                allDtoTestData.append("        dto.set")
                             .append(StringUtils.capitalize(fieldName))
                             .append("(").append(sampleValue).append(");\n");
            }
        }
        String testDtoDataCreation = allDtoTestData.toString();

        // Create template parameters
        CodeTemplate.Parameters params = CodeTemplate.newParameters();
        params
                .set("className", serviceClassName)
                .set("testClassName", testClassName)
                .set("domainName", StringUtils.capitalize(domainName))
                .set("entityClassName", entityClassName)
                .set("orgEntityClassName", orgEntityClassName)
                .set("dtoClassName", dtoClassName)
                .set("mapperName", mapperName)
                .set("searchDTOClassName", searchDTOClassName)
                .set("searchResultDTOClassName", searchResultDTOClassName)
                .set("pkType", pkType)
                .set("pkName", StringUtils.capitalize(pkName))
                .set("pkDtoDefine", pkDtoDefine.toString())
                .set("pkDtoInfo2", pkDtoInfo2.toString())
                .set("isCompositeKey", isCompositeKey)
                .set("idConstruction", idConstruction)
                .set("pkFieldParams", isCompositeKey ? idConstruction : "id")
                .set("setupMockDtoKeyValues", mockDtoKeySetup)
                .set("idVariableCreation", idVariableCreation)
                .set("testDataCreation", testDataCreation)
                .set("testDtoDataCreation", testDtoDataCreation)
                .set("isListOutput", isListOutput)
                .set("isPageOutput", isPageOutput)
                .set("isExposeOutput", isExposeOutput)
                .set("j2eePkgName", getPersistentPackageName(domain))
                .set("entityPackageName", entityPackageName)
                .set("dtoPackageName", dtoPackageName)
                .set("repositoryPackageName", repoPackageName)
                .set("packageName", testPackageName);

        String testCode = testCodeTmpl.toString(params);
        String qualifiedTestClassName = testPackageName + "." + testClassName;

        // Write test class to src/test/java directory
        this.writeTestSrcCode(this.publisher, qualifiedTestClassName, testCode);
    }

    /**
     * Write test source code to the test directory (src/test/java)
     */
    private void writeTestSrcCode(CodePublisher publisher, String qualifiedClassName, String sourceCode) {
        try {
            // Create a test-specific SourceFileAccessFactory that writes to src/test/java
            io.elasticore.base.SourceFileAccessFactory originalFactory = publisher.getSrcFileAccessFactory();
            
            // Get the base path from the original factory
            String originalPath = originalFactory.getFilePath(qualifiedClassName);
            
            // Replace src/main/java with src/test/java
            String testPath = originalPath.replace("src/main/java", "src/test/java").replace("src\\main\\java", "src\\test\\java");
            
            java.io.File testFile = new java.io.File(testPath);

            // Validation writes nothing. A test that is not there yet is one
            // generation would write - said like any other missing file,
            // where the check used to write it and pass.
            if (io.elasticore.base.util.ConsoleLog.isValidationMode()) {
                if (!(testFile.exists() && testFile.length() > 10)) {
                    io.elasticore.base.util.ConsoleLog.storeLog("VALIDATION_NEW", testPath);
                }
                return;
            }

            // Create the test directory structure
            java.io.File testDir = testFile.getParentFile();
            if (testDir != null && !testDir.exists()) {
                testDir.mkdirs();
            }
            if(testFile.exists() && testFile.length()>10) {
                return;
            }
            // Write the test file
            try (java.io.Writer writer = io.elasticore.base.extract.FileBasedSourceFileAccessFactory.newPlatformLineWriter(testFile)) {
                // Add ElastiCORE hash code for tracking
                String hCode = io.elasticore.base.util.HashUtils.makeEcdCode(sourceCode);
                writer.write(hCode);
                writer.write(sourceCode);
            }
            
            io.elasticore.base.util.ConsoleLog.storeLog("TEST_PUBLISH", testPath);
            
        } catch (Exception e) {
            io.elasticore.base.util.ConsoleLog.printWarn("Failed to write test class: " + qualifiedClassName + " - " + e.getMessage());
        }
    }

    /**
     * Generate sample value for a field based on its type
     */
    private String generateSampleValue(Field field) {
        // Skip non-base types (Entity references, Enums, etc.)
        if (!field.getTypeInfo().isBaseType()) {
            return null; // Don't set complex types
        }
        
        String fieldType = field.getTypeInfo().getDefaultTypeName();
        String fieldName = StringUtils.capitalize(field.getName());
        
        // Skip primary key fields that should not be explicitly set (auto-generated)
        if (field.isPrimaryKey() && field.hasAnnotation("genid")) {
            return null; // Don't set auto-generated IDs
        }
        
        switch (fieldType.toLowerCase()) {
            case "string":
                // Special handling for specific field names
                if (fieldName.toLowerCase().contains("phone")) {
                    return "\"010-1234-5678\"";
                } else if (fieldName.toLowerCase().contains("email")) {
                    return "\"test@example.com\"";
                } else if (fieldName.toLowerCase().contains("name")) {
                    return "\"Sample" + fieldName + "\"";
                } else if (fieldName.toLowerCase().contains("no") || fieldName.toLowerCase().contains("contract")) {
                    return "\"" + fieldName.toUpperCase() + "001\"";
                } else {
                    return "\"Sample" + fieldName + "\"";
                }
            case "integer":
            case "int":
                return "1";
            case "long":
                return "1L";
            case "boolean":
                return "true";
            case "double":
                return "1.0";
            case "float":
                return "1.0f";
            case "short":
                return "(short) 1";
            case "byte":
                return "(byte) 1";
            case "char":
                return "'A'";
            case "bigdecimal":
                return "new java.math.BigDecimal(\"1.0\")";
            case "biginteger":
                return "new java.math.BigInteger(\"1\")";
            case "localdate":
                return "java.time.LocalDate.now()";
            case "localdatetime":
                return "java.time.LocalDateTime.now()";
            case "localtime":
                return "java.time.LocalTime.now()";
            case "date":
                return "new java.util.Date()";
            case "timestamp":
                return "new java.sql.Timestamp(System.currentTimeMillis())";
            case "uuid":
                return "java.util.UUID.randomUUID()";
            default:
                // For complex types, return null (should be handled separately if needed)
                return "null";
        }
    }

}
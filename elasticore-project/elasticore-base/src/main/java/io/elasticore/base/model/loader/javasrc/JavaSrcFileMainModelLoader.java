package io.elasticore.base.model.loader.javasrc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.dto.DataTransfer;
import io.elasticore.base.model.dto.DataTransferModels;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.entity.EntityModels;
import io.elasticore.base.model.enums.EnumModels;
import io.elasticore.base.model.loader.FileSource;
import io.elasticore.base.model.loader.MainModelLoader;
import io.elasticore.base.model.loader.ModelLoaderContext;
import io.elasticore.base.model.repo.RepositoryModels;
import io.elasticore.base.util.ConsoleLog;
import io.elasticore.base.util.FileOrder;
import lombok.SneakyThrows;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JavaSrcFileMainModelLoader implements MainModelLoader {

    private DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

    private DocumentBuilder documentBuilder;

    private Document doc = null;

    private XPath xPath;


    private static ObjectMapper mapper = new ObjectMapper(new XmlFactory());

    private String templateFileDirPath;
    private List<FileSource> fileSources = new ArrayList<>();

    public JavaSrcFileMainModelLoader(String filePath) {
        this.templateFileDirPath = filePath;
    }

    private boolean isJavaSrcFile(File file) {
        if (!file.isFile()) return false;

        return file.getName().endsWith(".java");
    }

    private void loadJavaSrcFile(File f, List<FileSource> fileSources) {

        if (f.isDirectory()) {
            File[] files = FileOrder.list(f);
            for (File child : files) {
                loadJavaSrcFile(child, fileSources);
            }
        } else if (isJavaSrcFile(f)) {
            fileSources.add(FileSource.builder().filepath(f.getAbsolutePath()).build());
        }

    }

    @SneakyThrows
    @Override
    public ECoreModel load(ModelLoaderContext context) {

        List<String> srcDirPathList = context.getConfigList("srcdir");

        if (srcDirPathList == null || srcDirPathList.size() == 0) {
            throw new IllegalArgumentException("srcdir (source directory) config does not exist");
        }

        for (String srcDir : srcDirPathList) {
            File f = new File(srcDir);
            loadJavaSrcFile(f, fileSources);
        }

        io.elasticore.base.model.loader.ModelLoader<Entity> entityModelLoader = new JavaSrcEntityModelLoader();

        for (FileSource fileSource : fileSources) {
            entityModelLoader.loadModel(context, fileSource);
        }

        entityModelLoader.completeLoad();


        EntityModels entityModels = EntityModels.create(context.getDomainId(), "entityGrp", null, context.getEntityItems());
        EnumModels enumModels = EnumModels.create(context.getDomainId(), "enumGroup", null, context.getEnumModelItems());
        DataTransferModels dataTransferModels = DataTransferModels.create(context.getDomainId(), "dto", null, context.getDataTransferItems());
        RepositoryModels repositoryModels = RepositoryModels.create(context.getDomainId(), "repoGroup", null, context.getRepositoryItems());

        //EnumModels enumModels = getEnumModels();
        //EntityModels entityModels = getEntityModels();

        return ECoreModel.builder()
                .entityModels(entityModels)
                .enumModels(enumModels)
                .dataTransferModels(dataTransferModels)
                .repositoryModels(repositoryModels)
                .removedDeclarations(context.getRemovedDeclarations())
                .metrics(context.getMetrics())
                .configMap(context.getConfigMap())
                .namespaceMap(context.getNsMap())
                .namespaceModuleRedirectMap(context.getModuleRedirectMap())
                .build();
    }


}

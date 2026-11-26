package io.elasticore.base.model.loader.module;

import io.elasticore.base.model.ConstantParam;
import io.elasticore.base.model.RemovedDeclaration;
import io.elasticore.base.model.loader.FileSource;
import io.elasticore.base.model.loader.ModelLoaderContext;
import io.elasticore.base.util.ConsoleLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the sections a model file declares at the domain rather than on an
 * entity: metrics:, and the two sections 3.1 no longer reads.
 *
 *   metrics:
 *     revenue: sum(PurchaseOrder.total) where status != CANCELLED -- Revenue
 *
 * domainService: and processManager: generated a class that wired repository
 * ports, and a listener after commit, both of which are a few lines of plain
 * Spring code (issue 370). A project that still declares them must keep
 * building: each entry is kept only by its name and its uses or on list, so
 * the generator can say once that it is no longer read and what to write
 * instead. Nothing is generated from it and nothing is checked.
 */
public class DomainLevelModelLoader extends AbstractModelLoader implements ConstantParam {

    /** The domain-level section of process managers, no longer read since 3.1. */
    static final String KEYNAME_PROCESS_MANAGER = RemovedDeclaration.PROCESS_MANAGER;

    public boolean loadModel(ModelLoaderContext ctx, FileSource source) {
        return loadModel(ctx, source.getInfoMap());
    }

    public boolean loadModel(ModelLoaderContext ctx, Map<String, Map> map) {
        boolean metrics = map.containsKey(ConstantParam.KEYNAME_METRICS);
        if (metrics) loadMetrics(ctx, map.get(ConstantParam.KEYNAME_METRICS));
        boolean removed = false;
        if (map.containsKey(ConstantParam.KEYNAME_DOMAIN_SERVICE)) {
            removed(ctx, RemovedDeclaration.DOMAIN_SERVICE, "uses", map.get(ConstantParam.KEYNAME_DOMAIN_SERVICE));
            removed = true;
        }
        if (map.containsKey(KEYNAME_PROCESS_MANAGER)) {
            removed(ctx, RemovedDeclaration.PROCESS_MANAGER, "on", map.get(KEYNAME_PROCESS_MANAGER));
            removed = true;
        }
        return metrics || removed;
    }

    /**
     *   metrics:
     *     revenue:    sum(PurchaseOrder.total) where status != CANCELLED -- Revenue
     *     orderCount: count(PurchaseOrder) where status != CANCELLED     -- Orders
     *
     * Kept as written: what a line means is read where it is used (the
     * semantic model export), against the entities and enums of the model.
     */
    @SuppressWarnings("unchecked")
    private void loadMetrics(ModelLoaderContext ctx, Object section) {
        if (!(section instanceof Map)) {
            ConsoleLog.storeLog("DSL_WARN", String.format("[%s] metrics: is a list of name: line entries; it is left out.",
                    ctx.getDomainId()));
            return;
        }
        for (Map.Entry<Object, Object> e : ((Map<Object, Object>) section).entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            String name = String.valueOf(e.getKey()).trim();
            if (ctx.getMetrics().containsKey(name)) {
                ConsoleLog.storeLog("DSL_WARN", String.format("[%s] metric %s is declared twice; the first is kept.",
                        ctx.getDomainId(), name));
                continue;
            }
            ctx.getMetrics().put(name, String.valueOf(e.getValue()).trim());
        }
    }

    /**
     * One removed section: each entry by its name and the list it names, read
     * leniently - whatever shape it has, the load does not fail on it.
     */
    private void removed(ModelLoaderContext ctx, String section, String listKey, Object body) {
        if (!(body instanceof Map)) {
            ctx.getRemovedDeclarations().add(new RemovedDeclaration(section, null, null));
            return;
        }
        for (Object o : ((Map<?, ?>) body).entrySet()) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            String name = asText(entry.getKey());
            Object value = entry.getValue();
            List<String> names = value instanceof Map ? listOf((Map<?, ?>) value, listKey) : null;
            ctx.getRemovedDeclarations().add(new RemovedDeclaration(section, name, names));
        }
    }

    /** A list-valued key, written as a YAML list or as comma-separated names. */
    private List<String> listOf(Map<?, ?> body, String key) {
        List<String> out = new ArrayList<String>();
        Object declared = body.get(key);
        if (declared instanceof List) {
            for (Object each : (List<?>) declared) {
                String name = asText(each);
                if (name != null) out.add(name);
            }
        } else {
            String single = asText(declared);
            if (single != null) {
                for (String part : single.split(",", -1)) {
                    String name = part.trim();
                    if (!name.isEmpty()) out.add(name);
                }
            }
        }
        return out;
    }

    private String asText(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public void completeLoad() {
    }
}

package io.elasticore.base.model.pub.rules.ddd;

import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.FieldSpec;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.Syntax;
import io.elasticore.base.model.pub.rules.base.RuleText;
import io.elasticore.base.model.pub.rules.base.ServiceAccessRules;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisServiceFileRules;
import io.elasticore.base.model.pub.rules.mybatis.MyBatisSql;

import java.util.ArrayList;
import java.util.List;

/**
 * The repository ports of a MyBatis domain (ddd-mybatis-springboot,
 * hexagonal-mybatis-springboot), and the adapter behind them.
 *
 * The domain port is the JPA one, word for word: what an aggregate's life
 * consists of names no store. The query port takes the search DTO where the
 * JPA one takes a Specification - a MyBatis search is the mapper XML's WHERE
 * clause, and the application already speaks its search DTOs.
 *
 * The adapter is where the difference is. Spring Data's save() writes what
 * changed, cascades to the members, deletes the orphans, counts the version
 * up, calls the callbacks and publishes the events; here each of those is a
 * line of the adapter:
 *
 * <ul>
 *   <li>save: an INSERT when the root is new (no version yet, or no key, or a
 *       key nothing holds), otherwise an UPDATE that compares and counts up
 *       the version (409 when someone else wrote first); the root's
 *       Lifecycle before and after; its creation and modification stamps;
 *       its members compared with the rows stored - a member gone is
 *       deleted, one kept is updated, one new is inserted; then the events
 *       it raised are published.</li>
 *   <li>delete: the root's Lifecycle (its delete guard, the flag its
 *       members' checks read), its members, then its row.</li>
 *   <li>read: the row with the rows it joins, its collections read for a
 *       page of rows at once, and its Lifecycle told it was loaded.</li>
 * </ul>
 *
 * It is also the Lookup.Source of its root, so a reference by identity to
 * one is checked, and a @snapshot read, without an EntityManager.
 */
final class DddMyBatisPorts {

    private DddMyBatisPorts() {
    }

    static boolean applies(EntitySpec e, SourceCode c) {
        return MyBatisSql.applies(e) && c.syntax() == Syntax.JAVA;
    }

    // ---- the application's searches -----------------------------------------------

    static void queryPort(SourceCode c, EntitySpec e) {
        String root = e.getName();
        String srch = e.searchDtoClassName();
        c.addImport("%s.%s", e.pkg("entity"), root);
        c.addImport("%s.%s", e.pkg("dto"), srch);
        c.addImport("org.springframework.data.domain.Page");
        c.addImport("org.springframework.data.domain.Pageable");
        c.addImport("org.springframework.data.domain.Sort");
        c.addImport("java.util.List");
        String[][] methods = {
                {"Every %1$s.", "List<%1$s> findAll();"},
                {"Every %1$s, sorted.", "List<%1$s> findAll(Sort sort);"},
                {"Those the search matches - the owner's only when owner is given - sorted.",
                        "List<%1$s> findAll(%2$s search, Object owner, Sort sort);"},
                {"One page of those the search matches - the owner's only when owner is given.",
                        "Page<%1$s> findAll(%2$s search, Object owner, Pageable pageable);"},
                {"How many the search matches - the owner's only when owner is given.",
                        "long count(%2$s search, Object owner);"},
                {"Whether the search names a condition at all: an update by condition needs one.",
                        "boolean narrows(%2$s search);"},
        };
        for (int i = 0; i < methods.length; i++) {
            if (i > 0) c.varLine("bodyList", "");
            c.varLine("bodyList", "/** %s */", String.format(methods[i][0], root, srch))
             .varLine("bodyList", methods[i][1], root, srch);
        }
        String login = ServiceAccessRules.loginField(e);
        if (login != null) {
            c.varLine("bodyList", "")
             .varLine("bodyList", "/** Those whose %s is the one given: signing in reads it. */", login)
             .varLine("bodyList", "List<%s> findAllByLogin(String %s);", root, login);
        }
        for (DddRepositoryPortRules.Declared q : DddRepositoryPortRules.declared(e, c.syntax())) {
            if (q.domain) continue;
            q.importInto(c, false);
            c.varLine("bodyList", "")
             .varLine("bodyList", "/** %s */", q.doc)
             .varLine("bodyList", "%s", c.syntax().statement(q.signature("")));
        }
        c.var("packageName", DddRepositoryPortRules.queryPortPackage(e))
         .var("className", DddRepositoryPortRules.queryPortName(e))
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    // ---- what the application service is handed -------------------------------------

    static void serviceTakesPorts(SourceCode c, EntitySpec e) {
        String port = DddRepositoryPortRules.portName(e);
        String query = DddRepositoryPortRules.queryPortName(e);
        List<String> imports = new ArrayList<String>();
        imports.add(String.format("import %s.%s;", DddRepositoryPortRules.portPackage(e), port));
        imports.add(String.format("import %s.%s;", DddRepositoryPortRules.queryPortPackage(e), query));
        c.varLines("dependencyImportList", imports);

        List<String> lines = new ArrayList<String>();
        lines.add("/** The aggregate's own life - find one, save it, remove it - as the domain owns it. */");
        lines.add(String.format("protected final %s repository;", port));
        lines.add("");
        lines.add("/** Searching and paging: the application's business, through its own port. */");
        lines.add(String.format("protected final %s query;", query));
        lines.add("");
        lines.add("/** Finds what an aggregate refers to by identity, for its checks and snapshots. */");
        lines.add("protected final io.elasticore.springboot3.domain.Lookup lookup;");
        lines.add("");
        lines.add(String.format("public %sCoreService(%s repository, %s query, io.elasticore.springboot3.domain.Lookup lookup) {",
                e.getName(), port, query));
        lines.add("    this.repository = repository;");
        lines.add("    this.query = query;");
        lines.add("    this.lookup = lookup;");
        lines.add("}");
        c.varLines("dependencyList", lines);
        c.var("cmdRef", "repository")
         .var("qryRef", "query")
         .var("emRef", "lookup");
    }

    static void extensionHandsPortsOn(SourceCode c, EntitySpec e) {
        String query = DddRepositoryPortRules.queryPortPackage(e) + "." + DddRepositoryPortRules.queryPortName(e);
        c.var("extendImport", "%s.%s", DddRepositoryPortRules.portPackage(e), DddRepositoryPortRules.portName(e))
         .var("extendCtorParams", String.format("%s repository, %s query, io.elasticore.springboot3.domain.Lookup lookup",
                 DddRepositoryPortRules.portName(e), query))
         .var("extendSuperArgs", "repository, query, lookup");
    }

    // ---- the adapter ----------------------------------------------------------------

    static void adapter(SourceCode c, EntitySpec e) {
        c.template("mybatis_repositoryAdapter");
        Syntax syntax = c.syntax();
        String root = e.getName();
        String var = e.varName();
        FieldSpec pk = e.pkField();
        String key = syntax.nullableType(pk.typeName());
        String helperClass = e.domainClassName() + "RepositoryHelper";
        String mapper = "helper.get" + root + "()";
        String mapperClass = root + "Repository";
        String srch = e.searchDtoClassName();
        boolean auditor = MyBatisSql.recordsAuditor(e);

        c.addImport("java.util.Optional");
        c.addImport("java.util.List");
        c.addImport("java.util.ArrayList");
        c.addImport("java.util.Collections");
        c.addImport("org.springframework.stereotype.Repository");
        c.addImport("org.springframework.context.ApplicationEventPublisher");
        c.addImport("org.springframework.dao.OptimisticLockingFailureException");
        c.addImport("org.springframework.data.domain.Page");
        c.addImport("org.springframework.data.domain.PageImpl");
        c.addImport("org.springframework.data.domain.Pageable");
        c.addImport("org.springframework.data.domain.Sort");
        c.addImport("io.elasticore.springboot3.domain.Lifecycle");
        c.addImport("io.elasticore.springboot3.domain.Lookup");
        c.addImport("io.elasticore.springboot3.mybatis.Rows");
        c.addImport("%s.%s", e.pkg("entity"), root);
        c.addImport("%s.%s", e.pkg("dto"), srch);
        // The rows it reads with the root and the members it writes with it.
        for (FieldSpec f : MyBatisSql.fields(e)) {
            boolean read = MyBatisSql.backField(e, f) != null || MyBatisSql.isMemberReference(e, f);
            EntitySpec target = read ? e.related(f.coreItemType()) : null;
            if (target != null && target.pkg("entity") != null) c.addImport("%s.%s", target.pkg("entity"), target.getName());
        }
        String repositoryPkg = e.pkg("repository");
        String adapterPkg = DddRepositoryPortRules.adapterPackage(e);
        if (repositoryPkg != null && !repositoryPkg.equals(adapterPkg)) {
            c.addImport("%s.%s", repositoryPkg, helperClass);
            c.addImport("%s.%s", repositoryPkg, mapperClass);
        }
        String portPkg = DddRepositoryPortRules.portPackage(e);
        if (portPkg != null && !portPkg.equals(adapterPkg)) c.addImport("%s.%s", portPkg, DddRepositoryPortRules.portName(e));
        String queryPkg = DddRepositoryPortRules.queryPortPackage(e);
        if (queryPkg != null && !queryPkg.equals(adapterPkg)) {
            c.addImport("%s.%s", queryPkg, DddRepositoryPortRules.queryPortName(e));
        }

        Lines b = new Lines(c);
        b.add("private final %s helper;", helperClass)
         .add("private final ApplicationEventPublisher events;");
        if (auditor) {
            c.addImport("org.springframework.beans.factory.ObjectProvider");
            c.addImport("org.springframework.data.domain.AuditorAware");
            b.add("private final ObjectProvider<AuditorAware<?>> auditorAware;");
        }
        b.add("")
         .add("public %s(%s helper, ApplicationEventPublisher events%s) {", DddRepositoryPortRules.adapterName(e),
                 helperClass, auditor ? ", ObjectProvider<AuditorAware<?>> auditorAware" : "")
         .add("    this.helper = helper;")
         .add("    this.events = events;");
        if (auditor) b.add("    this.auditorAware = auditorAware;");
        b.add("}");

        // ---- the domain's port
        b.add("")
         .add("@Override")
         .add("public Optional<%s> findById(%s %s) {", root, pk.typeName(), pk.name())
         .add("    return %s.findById(%s).map(this::loaded);", mapper, pk.name())
         .add("}")
         .add("")
         .add("/**")
         .add(" * Writes the aggregate: its row - an INSERT when it is new, an UPDATE that")
         .add(" * compares the version otherwise - and its members against the rows stored;")
         .add(" * then publishes what it raised.")
         .add(" */")
         .add("@Override")
         .add("public %s save(%s %s) {", root, root, var)
         .add("    Lifecycle life = Lifecycle.of(%s);", var)
         .add("    boolean created = isNew(%s);", var);
        List<FieldSpec> singles = memberReferences(e);
        if (!singles.isEmpty()) {
            b.add("    Object[] membersBefore = created ? null : storedMembers(%s);", var);
        }
        b.add("    if (created) {");
        String genId = MyBatisSql.genIdExpression(pk);
        if (genId != null) {
            b.add("        if (%s == null) Rows.set(%s, \"%s\", %s);", syntax.getter(var, pk.name()), var, pk.name(), genId);
        }
        FieldSpec version = MyBatisSql.version(e);
        boolean objectVersion = version != null && !version.hasDslAny("primitive", "prim");
        b.add("        life.beforeInsert();");
        if (!singles.isEmpty()) b.add("        writeMemberReferences(%s);", var);
        stamps(b, syntax, e, var, true);
        b.add("        %s.insert(%s);", mapper, var);
        if (objectVersion) {
            b.add("        if (%s == null) Rows.set(%s, \"%s\", %s);", syntax.getter(var, version.name()), var,
                    version.name(), zero(version));
        }
        b.add("        writeMembers(%s, true);", var)
         .add("        life.afterInsert();")
         .add("    } else {")
         .add("        life.beforeUpdate();");
        if (!singles.isEmpty()) b.add("        writeMemberReferences(%s);", var);
        stamps(b, syntax, e, var, false);
        if (MyBatisSql.hasColumnsToUpdate(e)) {
            b.add("        if (%s.update(%s) == 0)", mapper, var)
             .add("            throw new OptimisticLockingFailureException(\"%s \" + %s + \" was changed by someone else; read it again\");",
                     root, syntax.getter(var, pk.name()));
            bumpVersion(b, syntax, var, version, "        ");
        } else {
            b.add("        if (!%s.existsById(%s))", mapper, syntax.getter(var, pk.name()))
             .add("            throw new jakarta.persistence.EntityNotFoundException(\"%s \" + %s + \" is not there\");",
                     root, syntax.getter(var, pk.name()));
        }
        b.add("        writeMembers(%s, false);", var);
        if (!singles.isEmpty()) b.add("        dropReplacedMembers(%s, membersBefore);", var);
        b.add("    }")
         .add("    publish(life);")
         .add("    return %s;", var)
         .add("}")
         .add("")
         .add("/** Removes it - its delete guard first, then its members, then its row; then publishes what it raised. */")
         .add("@Override")
         .add("public void delete(%s %s) {", root, var)
         .add("    Lifecycle life = Lifecycle.of(%s);", var)
         .add("    life.beforeDelete();");
        FieldSpec deleteVersion = MyBatisSql.version(e);
        if (MyBatisSql.aggregatesBound() && e.aggregateRoot() && deleteVersion != null) {
            // A stale copy is refused before anything is deleted, as JPA's remove
            // of one is; a row already gone is not. The row stays locked till the end.
            String id = syntax.getter(var, pk.name());
            String ver = syntax.getter(var, deleteVersion.name());
            b.add("    if (%s%s.lockVersion(%s, %s) == 0 && %s.existsById(%s))",
                    primitive(deleteVersion) ? "" : ver + " != null && ", mapper, id, ver, mapper, id)
             .add("        throw new OptimisticLockingFailureException(\"%s \" + %s + \" was changed by someone else; read it again\");",
                    root, id);
        }
        // The members the stored row holds, which may not be the ones held in memory.
        if (!singles.isEmpty()) b.add("    Object[] membersBefore = storedMembers(%s);", var);
        for (FieldSpec f : MyBatisSql.collections(e)) {
            if (!MyBatisSql.isMemberCollection(e, f)) continue;
            FieldSpec back = MyBatisSql.backField(e, f);
            b.add("    helper.get%s().deleteAllBy%s(%s);", e.related(f.coreItemType()).getName(),
                    RuleText.capital(back.name()), syntax.getter(var, pk.name()));
        }
        b.add("    %s.deleteById(%s);", mapper, syntax.getter(var, pk.name()));
        // A member held by a to-one relation goes after the row that holds its key:
        // the stored one, and the one held in memory when it is another.
        for (int i = 0; i < singles.size(); i++) {
            FieldSpec f = singles.get(i);
            EntitySpec target = e.related(f.coreItemType());
            String held = syntax.getter(var, f.name());
            String heldKey = syntax.getter(held, target.pkField().name());
            String memberKey = syntax.nullableType(target.pkField().typeName());
            b.add("    if (membersBefore[%d] != null) helper.get%s().deleteById((%s) membersBefore[%d]);", i,
                    target.getName(), memberKey, i)
             .add("    if (%s != null && %s != null && !%s.equals(membersBefore[%d])) helper.get%s().deleteById(%s);",
                     held, heldKey, heldKey, i, target.getName(), heldKey);
        }
        b.add("    publish(life);")
         .add("}")
         .add("")
         .add("@Override")
         .add("public boolean existsById(%s %s) {", pk.typeName(), pk.name())
         .add("    return %s.existsById(%s);", mapper, pk.name())
         .add("}");

        // ---- the application's searches
        b.add("")
         .add("@Override")
         .add("public List<%s> findAll() {", root)
         .add("    return loaded(%s.findAll(null));", mapper)
         .add("}")
         .add("")
         .add("@Override")
         .add("public List<%s> findAll(Sort sort) {", root)
         .add("    return loaded(%s.findAll(%s.orderBy(sort)));", mapper, mapperClass)
         .add("}")
         .add("")
         .add("@Override")
         .add("public List<%s> findAll(%s search, Object owner, Sort sort) {", root, srch)
         .add("    return loaded(%s.findBySearch(search, owner, %s.orderBy(sort), null, null));", mapper, mapperClass)
         .add("}")
         .add("")
         .add("/** A count and a page of rows: two statements, and none for a page past the last. */")
         .add("@Override")
         .add("public Page<%s> findAll(%s search, Object owner, Pageable pageable) {", root, srch)
         .add("    if (pageable == null || pageable.isUnpaged()) {")
         .add("        List<%s> all = findAll(search, owner, pageable == null ? Sort.unsorted() : pageable.getSort());", root)
         .add("        return new PageImpl<>(all, pageable == null ? Pageable.unpaged() : pageable, all.size());")
         .add("    }")
         .add("    long total = %s.countBySearch(search, owner);", mapper)
         .add("    List<%s> rows = total <= pageable.getOffset() ? Collections.<%s>emptyList()", root, root)
         .add("            : loaded(%s.findBySearch(search, owner, %s.orderBy(pageable.getSort()),", mapper, mapperClass)
         .add("                    pageable.getOffset(), pageable.getPageSize()));")
         .add("    return new PageImpl<>(rows, pageable, total);")
         .add("}")
         .add("")
         .add("@Override")
         .add("public long count(%s search, Object owner) {", srch)
         .add("    return %s.countBySearch(search, owner);", mapper)
         .add("}")
         .add("")
         .add("@Override")
         .add("public boolean narrows(%s search) {", srch)
         .add("    return %s.hasCondition(search);", mapperClass)
         .add("}");
        String login = ServiceAccessRules.loginField(e);
        if (login != null) {
            b.add("")
             .add("@Override")
             .add("public List<%s> findAllByLogin(String %s) {", root, login)
             .add("    return loaded(%s.findAllByLogin(%s));", mapper, login)
             .add("}");
        }
        for (DddRepositoryPortRules.Declared q : DddRepositoryPortRules.declared(e, syntax)) {
            q.importInto(c, false);
            String call = String.format("%s.%s(%s)", mapper, q.name, q.args);
            String body;
            boolean rows = root.equals(q.itemType());
            if (io.elasticore.base.model.pub.rules.mybatis.MyBatisQueries.deletesRowByRow(e, q.name)) {
                // Each row deleted whole, as delete() deletes one - as Spring Data JPA's derived delete does.
                String r = q.returnType.trim();
                body = String.format("List<%s> rows = loaded(%s.%sRows(%s));%nfor (%s row : rows) delete(row);", root,
                        mapper, q.name, q.args, root);
                if (!"void".equals(r)) body += String.format("%nreturn %srows.size();", "long".equals(r) || "Long".equals(r) ? "(long) " : "");
            } else if ("void".equals(q.returnType)) {
                body = call + ";";
            } else if (!rows) {
                body = "return " + call + ";";
            } else if (q.returnType.startsWith("Optional<")) {
                body = "return " + call + ".map(this::loaded);";
            } else if (q.returnType.startsWith("Page<")) {
                body = String.format("Page<%s> page = %s;%n        loaded(page.getContent());%n        return page;", root, call);
            } else if (q.returnType.startsWith("List<")) {
                body = "return loaded(" + call + ");";
            } else if (q.returnType.matches("(java\\.util\\.)?(Set|Collection|List)<.*")) {
                body = String.format("%s found = %s;%n        loaded(new ArrayList<>(found));%n        return found;",
                        q.returnType, call);
            } else {
                body = String.format("%s row = %s;%n        return row == null ? null : loaded(row);", root, call);
            }
            b.add("")
             .add("@Override")
             .add("%s {", q.signature("public"));
            for (String line : body.split("\\r?\\n")) b.add("    %s", line.trim());
            b.add("}");
        }

        // ---- Lookup.Source
        b.add("")
         .add("/** Whose rows this finds, for the application's Lookup. */")
         .add("@Override")
         .add("public Class<?> type() {")
         .add("    return %s.class;", root)
         .add("}")
         .add("")
         .add("@Override")
         .add("public Object find(Object id) {")
         .add("    return id == null ? null : %s.findById((%s) id).map(this::loaded).orElse(null);", mapper, key)
         .add("}");

        // ---- what save, delete and read do underneath
        b.add("")
         .add("/** New: never written - %s.", isNewSays(e, version))
         .add(" */")
         .add("private boolean isNew(%s %s) {", root, var);
        if (objectVersion) {
            b.add("    return %s == null;", syntax.getter(var, version.name()));
        } else if (MyBatisSql.generatedKey(pk) && !pk.hasDslAny("primitive", "prim")) {
            b.add("    return %s == null;", syntax.getter(var, pk.name()));
        } else {
            b.add("    %s key = %s;", key, syntax.getter(var, pk.name()))
             .add("    return key == null || !%s.existsById(key);", mapper);
        }
        b.add("}");

        writeMembers(b, c, e, var);
        if (!singles.isEmpty()) memberReferences(b, c, e, var, singles);
        loaded(b, c, e);

        b.add("")
         .add("/** Publishes what the aggregate raised, once it is written, as Spring Data does on save. */")
         .add("private void publish(Lifecycle life) {")
         .add("    java.util.Collection<Object> raised = life.pendingEvents();")
         .add("    if (raised == null || raised.isEmpty()) return;")
         .add("    for (Object event : new ArrayList<>(raised)) events.publishEvent(event);")
         .add("    life.eventsPublished();")
         .add("}");
        if (auditor) {
            b.add("")
             .add("/** Who is writing: the application's AuditorAware, when it has one. */")
             .add("private Object currentAuditor() {")
             .add("    AuditorAware<?> aware = auditorAware.getIfAvailable();")
             .add("    return aware == null ? null : aware.getCurrentAuditor().orElse(null);")
             .add("}");
        }

        for (String line : b.lines) c.varLine("bodyList", "%s", line);
        c.var("packageName", adapterPkg)
         .var("className", DddRepositoryPortRules.adapterName(e))
         .var("portName", DddRepositoryPortRules.portName(e) + ", " + DddRepositoryPortRules.queryPortName(e)
                 + ", Lookup.Source")
         .var("classAnnotationList", c.renderClassAnnotations())
         .var("importList", RuleText.stripTrailing(c.renderImports()));
    }

    private static String isNewSays(EntitySpec e, FieldSpec version) {
        FieldSpec pk = e.pkField();
        if (version != null && !version.hasDslAny("primitive", "prim")) return "no version yet";
        if (MyBatisSql.generatedKey(pk) && !pk.hasDslAny("primitive", "prim")) return "no key yet";
        return "no key yet, or one no row holds";
    }

    private static String zero(FieldSpec version) {
        String t = RuleText.simpleName(version.typeName());
        return "Long".equals(t) || "long".equals(t) ? "0L" : "Short".equals(t) || "short".equals(t) ? "(short) 0" : "0";
    }

    private static String one(FieldSpec version) {
        String t = RuleText.simpleName(version.typeName());
        return "Long".equals(t) || "long".equals(t) ? "1L" : "Short".equals(t) || "short".equals(t) ? "(short) 1" : "1";
    }

    private static boolean primitive(FieldSpec version) {
        String t = RuleText.simpleName(version.typeName());
        return version.hasDslAny("primitive", "prim") || "long".equals(t) || "int".equals(t) || "short".equals(t);
    }

    /** The version an UPDATE counted up, counted up in memory too, as JPA's merge hands it back. */
    private static void bumpVersion(Lines b, Syntax syntax, String var, FieldSpec version, String indent) {
        if (version == null) return;
        String get = syntax.getter(var, version.name());
        String next = "Short".equals(RuleText.simpleName(version.typeName())) || "short".equals(version.typeName())
                ? "(short) (" + get + " + 1)" : get + " + 1";
        if (primitive(version)) {
            b.add("%sRows.set(%s, \"%s\", %s);", indent, var, version.name(), next);
        } else {
            b.add("%sRows.set(%s, \"%s\", %s == null ? %s : %s);", indent, var, version.name(), get, one(version), next);
        }
    }

    /** A member just inserted holds the version the row was given (COALESCE(version, 0)). */
    private static void insertedVersion(Lines b, Syntax syntax, String var, FieldSpec version, String indent) {
        if (version == null || primitive(version)) return;
        b.add("%sif (%s == null) Rows.set(%s, \"%s\", %s);", indent, syntax.getter(var, version.name()), var,
                version.name(), zero(version));
    }

    /** The creation or modification stamps of a row, as the layered MyBatis service writes them. */
    private static void stamps(Lines b, Syntax syntax, EntitySpec e, String var, boolean create) {
        StringBuilder created = new StringBuilder();
        StringBuilder updated = new StringBuilder();
        MyBatisServiceFileRules.stamps(syntax, e, var, "\n", created, updated);
        for (String line : (create ? created : updated).toString().split("\n")) {
            if (!line.trim().isEmpty()) b.add("        %s", setterToRows(line.trim(), var));
        }
    }

    /**
     * A stamp set through the row's field rather than its setter: the domain
     * rules make most setters the aggregate's own (protected), and a stamp is
     * the store's to write, not a change the aggregate makes.
     */
    private static String setterToRows(String statement, String var) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "^(.*?)" + java.util.regex.Pattern.quote(var) + "\\.set(\\w)(\\w*)\\((.*)\\);$").matcher(statement);
        if (!m.matches()) return statement;
        String field = Character.toLowerCase(m.group(2).charAt(0)) + m.group(3);
        return String.format("%sRows.set(%s, \"%s\", %s);", m.group(1), var, field, m.group(4));
    }

    /** The members of the aggregate against the rows stored: gone ones deleted, kept ones updated, new ones inserted. */
    private static void writeMembers(Lines b, SourceCode c, EntitySpec e, String var) {
        Syntax syntax = c.syntax();
        FieldSpec pk = e.pkField();
        b.add("")
         .add("/**")
         .add(" * Writes the members of the aggregate - JPA's cascade and orphan removal,")
         .add(" * said out loud: every one when the root is new; otherwise each row stored")
         .add(" * that the root no longer holds is deleted, each it still holds updated,")
         .add(" * and each it did not hold before inserted.")
         .add(" */")
         .add("private void writeMembers(%s %s, boolean created) {", e.getName(), var);
        for (FieldSpec f : MyBatisSql.collections(e)) {
            if (!MyBatisSql.isMemberCollection(e, f)) continue;
            FieldSpec back = MyBatisSql.backField(e, f);
            EntitySpec target = e.related(f.coreItemType());
            String item = target.getName();
            FieldSpec mpk = target.pkField();
            String mkey = syntax.nullableType(mpk.typeName());
            String mm = "helper.get" + item + "()";
            String list = syntax.getter(var, f.name());
            FieldSpec backId = MyBatisSql.joinedBy(target, back);
            String backSet = backId != null
                    ? String.format("Rows.set(m, \"%s\", %s);", backId.name(), syntax.getter(var, pk.name()))
                    : String.format("Rows.set(m, \"%s\", %s);", back.name(), var);
            b.add("    {")
             .add("        List<%s> members = %s == null ? Collections.<%s>emptyList() : %s;", item, list, item, list)
             .add("        java.util.Set<%s> stored = new java.util.HashSet<>();", mkey)
             .add("        if (!created) {")
             .add("            for (%s row : %s.findAllBy%sIn(Collections.singletonList(%s)))", item, mm,
                     RuleText.capital(back.name()), syntax.getter(var, pk.name()))
             .add("                stored.add(%s);", syntax.getter("row", mpk.name()))
             .add("        }")
             .add("        java.util.Set<%s> kept = new java.util.HashSet<>();", mkey)
             .add("        for (%s m : members) {", item)
             .add("            if (%s != null && stored.contains(%s)) kept.add(%s);", syntax.getter("m", mpk.name()),
                     syntax.getter("m", mpk.name()), syntax.getter("m", mpk.name()))
             .add("        }")
             .add("        for (%s key : stored) {", mkey)
             .add("            if (!kept.contains(key)) %s.deleteById(key);", mm)
             .add("        }")
             .add("        for (%s m : members) {", item)
             .add("            %s", backSet)
             .add("            if (kept.contains(%s)) {", syntax.getter("m", mpk.name()));
            StringBuilder created = new StringBuilder();
            StringBuilder updated = new StringBuilder();
            MyBatisServiceFileRules.stamps(syntax, target, "m", "\n", created, updated);
            for (String line : updated.toString().split("\n")) {
                if (!line.trim().isEmpty()) b.add("                %s", setterToRows(line.trim(), "m"));
            }
            FieldSpec mVersion = MyBatisSql.version(target);
            if (MyBatisSql.hasColumnsToUpdate(target)) {
                b.add("                if (%s.update(m) == 0)", mm)
                 .add("                    throw new OptimisticLockingFailureException(\"%s \" + %s + \" was changed by someone else; read it again\");",
                         item, syntax.getter("m", mpk.name()));
                bumpVersion(b, syntax, "m", mVersion, "                ");
            }
            b.add("            } else {");
            if (MyBatisSql.generatedKey(mpk) && !mpk.hasDslAny("primitive", "prim")) {
                b.add("                Rows.set(m, \"%s\", null);", mpk.name());
            }
            String genId = MyBatisSql.genIdExpression(mpk);
            if (genId != null) {
                b.add("                if (%s == null) Rows.set(m, \"%s\", %s);", syntax.getter("m", mpk.name()), mpk.name(), genId);
            }
            if (mVersion != null && !primitive(mVersion)) {
                b.add("                Rows.set(m, \"%s\", null);", mVersion.name());
            }
            for (String line : created.toString().split("\n")) {
                if (!line.trim().isEmpty()) b.add("                %s", setterToRows(line.trim(), "m"));
            }
            b.add("                %s.insert(m);", mm);
            insertedVersion(b, syntax, "m", mVersion, "                ");
            b.add("            }")
             .add("        }")
             .add("    }");
        }
        b.add("}");
    }

    /** The to-one relations of the root to members of its aggregate, written with it. */
    private static List<FieldSpec> memberReferences(EntitySpec e) {
        List<FieldSpec> out = new ArrayList<FieldSpec>();
        for (FieldSpec f : MyBatisSql.fields(e)) {
            EntitySpec target = e.related(f.coreItemType());
            if (MyBatisSql.isMemberReference(e, f) && target != null && target.pkField() != null) out.add(f);
        }
        return out;
    }

    /**
     * A member the root holds by a to-one relation: written before the root,
     * which then holds its key - inserted when it is new, updated in place
     * otherwise - and deleted once the root lets it go.
     */
    private static void memberReferences(Lines b, SourceCode c, EntitySpec e, String var, List<FieldSpec> singles) {
        Syntax syntax = c.syntax();
        String root = e.getName();
        b.add("")
         .add("/** The members the root holds by a to-one relation, before the root is written: it holds their keys. */")
         .add("private void writeMemberReferences(%s %s) {", root, var);
        for (FieldSpec f : singles) {
            EntitySpec target = e.related(f.coreItemType());
            FieldSpec mpk = target.pkField();
            String item = target.getName();
            String mm = "helper.get" + item + "()";
            String getKey = syntax.getter("m", mpk.name());
            b.add("    {")
             .add("        %s m = %s;", item, syntax.getter(var, f.name()))
             .add("        if (m != null) {")
             .add("            if (%s != null && %s.existsById(%s)) {", getKey, mm, getKey);
            StringBuilder created = new StringBuilder();
            StringBuilder updated = new StringBuilder();
            MyBatisServiceFileRules.stamps(syntax, target, "m", "\n", created, updated);
            for (String line : updated.toString().split("\n")) {
                if (!line.trim().isEmpty()) b.add("                %s", setterToRows(line.trim(), "m"));
            }
            FieldSpec mVersion = MyBatisSql.version(target);
            if (MyBatisSql.hasColumnsToUpdate(target)) {
                b.add("                if (%s.update(m) == 0)", mm)
                 .add("                    throw new OptimisticLockingFailureException(\"%s \" + %s + \" was changed by someone else; read it again\");",
                         item, getKey);
                bumpVersion(b, syntax, "m", mVersion, "                ");
            }
            b.add("            } else {");
            if (MyBatisSql.generatedKey(mpk) && !mpk.hasDslAny("primitive", "prim")) {
                b.add("                Rows.set(m, \"%s\", null);", mpk.name());
            }
            String genId = MyBatisSql.genIdExpression(mpk);
            if (genId != null) b.add("                if (%s == null) Rows.set(m, \"%s\", %s);", getKey, mpk.name(), genId);
            if (mVersion != null && !primitive(mVersion)) {
                b.add("                Rows.set(m, \"%s\", null);", mVersion.name());
            }
            for (String line : created.toString().split("\n")) {
                if (!line.trim().isEmpty()) b.add("                %s", setterToRows(line.trim(), "m"));
            }
            b.add("                %s.insert(m);", mm);
            insertedVersion(b, syntax, "m", mVersion, "                ");
            b.add("            }")
             .add("        }")
             .add("    }");
        }
        b.add("}")
         .add("")
         .add("/** The keys of the members the stored row holds, before this save changes them. */")
         .add("private Object[] storedMembers(%s %s) {", root, var)
         .add("    %s stored = helper.get%s().findStoredById(%s).orElse(null);", root, root, syntax.getter(var, e.pkField().name()))
         .add("    if (stored == null) return new Object[%d];", singles.size())
         .add("    return new Object[]{");
        for (int i = 0; i < singles.size(); i++) {
            FieldSpec f = singles.get(i);
            EntitySpec target = e.related(f.coreItemType());
            String held = syntax.getter("stored", f.name());
            b.add("        %s == null ? null : %s%s", held, syntax.getter(held, target.pkField().name()),
                    i < singles.size() - 1 ? "," : "");
        }
        b.add("    };")
         .add("}")
         .add("")
         .add("/** A member the root held before and holds no longer: deleted, as JPA's orphan removal deletes it. */")
         .add("private void dropReplacedMembers(%s %s, Object[] before) {", root, var);
        for (int i = 0; i < singles.size(); i++) {
            FieldSpec f = singles.get(i);
            EntitySpec target = e.related(f.coreItemType());
            String held = syntax.getter(var, f.name());
            String key = syntax.nullableType(target.pkField().typeName());
            b.add("    if (before[%d] != null && (%s == null || !before[%d].equals(%s)))", i, held, i,
                    syntax.getter(held, target.pkField().name()))
             .add("        helper.get%s().deleteById((%s) before[%d]);", target.getName(), key, i);
        }
        b.add("}");
    }

    /** The rows with their collections, read for all of them at once, each told it was loaded. */
    private static void loaded(Lines b, SourceCode c, EntitySpec e) {
        Syntax syntax = c.syntax();
        String root = e.getName();
        FieldSpec pk = e.pkField();
        String key = syntax.nullableType(pk.typeName());
        b.add("")
         .add("/**")
         .add(" * Hands each row the rows of its collections, read for all of them at once -")
         .add(" * one statement per collection, whatever the number of rows - and tells it")
         .add(" * it was loaded. A row this transaction handed out before is the same")
         .add(" * object, which may have changed since: it is left as it is.")
         .add(" */")
         .add("private List<%s> loaded(List<%s> read) {", root, root)
         .add("    List<%s> rows = new ArrayList<>();", root)
         .add("    for (%s row : read) if (io.elasticore.springboot3.mybatis.LoadedRows.first(row)) rows.add(row);", root)
         .add("    if (rows.isEmpty()) return read;");
        List<FieldSpec> collections = MyBatisSql.collections(e);
        if (!collections.isEmpty()) {
            b.add("    java.util.Map<%s, %s> byKey = new java.util.LinkedHashMap<>();", key, root)
             .add("    for (%s row : rows) byKey.put(%s, row);", root, syntax.getter("row", pk.name()))
             .add("    List<%s> keys = new ArrayList<>(byKey.keySet());", key);
            for (FieldSpec f : collections) {
                FieldSpec back = MyBatisSql.backField(e, f);
                EntitySpec target = e.related(f.coreItemType());
                String item = target.getName();
                FieldSpec backId = MyBatisSql.joinedBy(target, back);
                b.add("    {")
                 .add("        java.util.Map<%s, List<%s>> found = new java.util.HashMap<>();", key, item)
                 .add("        for (%s row : rows) found.put(%s, new ArrayList<>());", root, syntax.getter("row", pk.name()))
                 .add("        for (int from = 0; from < keys.size(); from += 500) {")
                 .add("            for (%s m : helper.get%s().findAllBy%sIn(keys.subList(from, Math.min(keys.size(), from + 500)))) {",
                         item, item, RuleText.capital(back.name()))
                 .add("                %s row = byKey.get(%s);", root, backId != null
                         ? syntax.getter("m", backId.name())
                         : syntax.getter(syntax.getter("m", back.name()), pk.name()))
                 .add("                if (row == null) continue;");
                if (backId == null) b.add("                Rows.set(m, \"%s\", row);", back.name());
                b.add("                found.get(%s).add(m);", syntax.getter("row", pk.name()))
                 .add("            }")
                 .add("        }")
                 .add("        for (%s row : rows) Rows.set(row, \"%s\", found.get(%s));", root, f.name(),
                         syntax.getter("row", pk.name()))
                 .add("    }");
            }
        }
        b.add("    for (%s row : rows) Lifecycle.of(row).afterLoad();", root)
         .add("    return read;")
         .add("}")
         .add("")
         .add("private %s loaded(%s row) {", root, root)
         .add("    loaded(Collections.singletonList(row));")
         .add("    return row;")
         .add("}");
    }

    /** Lines of a class body, written with format arguments. */
    private static final class Lines {
        final List<String> lines = new ArrayList<String>();

        Lines(SourceCode c) {
        }

        Lines add(String format, Object... args) {
            lines.add(args.length == 0 ? format : String.format(format, args));
            return this;
        }
    }
}

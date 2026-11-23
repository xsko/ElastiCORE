package io.elasticore.base.model.pub.rules.verify;

import io.elasticore.base.model.ECoreModel;
import io.elasticore.base.model.entity.Entity;
import io.elasticore.base.model.pub.rules.EntitySpec;
import io.elasticore.base.model.pub.rules.SourceCode;
import io.elasticore.base.model.pub.rules.base.SearchSortList;
import io.elasticore.base.util.ConsoleLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a search may sort by, rule by rule (SortablePaths): for each condition that keeps a value in the list or out
 * of it, a model where it is in and one where it is out, and the list asserted whole - a value too many is a value
 * whose order the search gives away, one too few a sort the search refuses.
 *
 * <p>Two small domains. {@code spa} answers with the DTOs an {@code @expose} entity gets, whose relations are
 * deferred (the key flattened, the row's own DTO nested); {@code spb} answers with DTOs written by hand
 * ({@code @searchResult}, nested DTOs by type and by {@code @dtype}, four relations deep).
 */
public class SortablePathsTest {

    /** The entity's own DTOs: deferred relations, hidden values of every kind. */
    private static final String AUTO = String.join(
            "\n",
            "entity:",
            "  SpAddress:",
            "    meta: entity @embeddable",
            "    fields:",
            "      city: string(40)",
            "      gateCode: string(10) @writeonly",
            "",
            "  SpBase:",
            "    meta: entity",
            "    fields:",
            "      baseId: long @id @sequence",
            "      kind: string(10)",
            "",
            "  SpChild:",
            "    meta: entity @extend(SpBase) @expose(20) @access(read=user, update=admin)",
            "    fields:",
            "      label: string(20)",
            "",
            "  SpGuarded:",
            "    meta: entity @root @expose(20) @access(read=user, update=admin)",
            "    fields:",
            "      guardedId: long @id @sequence",
            "      note: string(20)",
            "",
            "  SpOpen:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      openId: long @id @sequence",
            "      title: string(20)",
            "      secret: string(255) @password",
            "      guarded: SpGuarded @ManyToOne",
            "",
            "  SpPublic:",
            "    meta: entity @root @expose(20) @access(read=public, update=admin)",
            "    fields:",
            "      publicId: long @id @sequence",
            "      name: string(20)",
            "",
            "  SpOrder:",
            "    meta: entity @root @expose(20)",
            "    fields:",
            "      orderId: long @id @sequence",
            "      placedAt: datetime",
            "      cardNo: string(30) @writeonly",
            "      pin: string(10) @nooutput",
            "      pw: string(255) @password",
            "      ign: string(10) @jsonignore",
            "      phone: string(20) @env:maskphone",
            "      hiddenRef: string(20) @env:hideref",
            "      writeOnly: string(20) @env:wonly",
            "      note: string(20) @dto:com.fasterxml.jackson.annotation.JsonProperty(\"note\")",
            "      guarded: SpGuarded @ManyToOne",
            "      open: SpOpen @ManyToOne",
            "      pub: SpPublic @ManyToOne",
            "      child: SpChild @ManyToOne",
            "      brief: SpOpen @ManyToOne @dtype(SpOpenBrief)",
            "      address: SpAddress @Embedded",
            "      opens: List<SpOpen> @OneToMany @dtype(List<SpOpenDTO>)",
            "",
            "dto:",
            "  SpOpenBrief:",
            "    meta: dto",
            "    fields:",
            "      openId: long",
            "      guardedNote: string @ref(guarded.note)",
            "");

    /** DTOs written by hand: @ref paths, nested DTOs, a value object the DTO does not carry, @view. */
    private static final String DECLARED = String.join(
            "\n",
            "entity:",
            "  SbAddress:",
            "    meta: entity @embeddable",
            "    fields:",
            "      city: string(40)",
            "",
            "  SbMember:",
            "    meta: entity",
            "    fields:",
            "      memberId: long @id @sequence",
            "      login: string(20)",
            "      secret: string(255) @password",
            "",
            "  SbE:",
            "    meta: entity",
            "    fields:",
            "      eId: long @id @sequence",
            "      name: string(20)",
            "",
            "  SbD:",
            "    meta: entity",
            "    fields:",
            "      dId: long @id @sequence",
            "      name: string(20)",
            "      e: SbE @ManyToOne",
            "",
            "  SbC:",
            "    meta: entity",
            "    fields:",
            "      cId: long @id @sequence",
            "      name: string(20)",
            "      d: SbD @ManyToOne",
            "",
            "  SbB:",
            "    meta: entity",
            "    fields:",
            "      bId: long @id @sequence",
            "      name: string(20)",
            "      c: SbC @ManyToOne",
            "",
            "  SbGuarded:",
            "    meta: entity @access(read=user, update=admin)",
            "    fields:",
            "      gId: long @id @sequence",
            "      name: string(20)",
            "",
            "  SbOrder:",
            "    meta: entity",
            "    fields:",
            "      orderId: long @id @sequence",
            "      total: int",
            "      internal: string(20) @view(c)",
            "      visible: string(20) @view(cr)",
            "      member: SbMember @ManyToOne",
            "      members: List<SbMember> @OneToMany",
            "      address: SbAddress @Embedded",
            "      b: SbB @ManyToOne",
            "      guarded: SbGuarded @ManyToOne",
            "",
            "dto:",
            "  SbOrderView:",
            "    meta: dto @searchResult(SbOrder) @jsonview",
            "    fields:",
            "      memberLogin: string @ref(member.login)",
            "      memberSecret: string @ref(member.secret)",
            "      membersSecret: string @ref(members.secret)",
            "      address: string",
            "      b: SbBView",
            "      guarded: SbGuardedView",
            "",
            "  SbBView:",
            "    meta: dto",
            "    fields:",
            "      bId: long",
            "      name: string",
            "      c: SbC @dtype(SbCView)",
            "",
            "  SbCView:",
            "    meta: dto",
            "    fields:",
            "      cId: long",
            "      name: string",
            "      d: SbDView",
            "",
            "  SbDView:",
            "    meta: dto",
            "    fields:",
            "      dId: long",
            "      name: string",
            "      e: SbEView",
            "",
            "  SbEView:",
            "    meta: dto",
            "    fields:",
            "      eId: long",
            "      name: string",
            "",
            "  SbGuardedView:",
            "    meta: dto",
            "    fields:",
            "      gId: long",
            "      name: string",
            "");

    @TempDir
    static Path dir;

    private static ECoreModel auto;
    private static ECoreModel declared;

    @BeforeAll
    static void load() throws Exception {
        domain("spa", AUTO);
        domain("spb", DECLARED);
        BlueprintLoader loader = BlueprintLoader.load(dir.resolve("blueprint").toString());
        auto = loader.modelsOf("spa").get(0);
        declared = loader.modelsOf("spb").get(0);
    }

    @AfterAll
    static void forget() throws Exception {
        // An in-process load leaves the model process-wide; other tests expect none.
        java.lang.reflect.Field tmp =
                io.elasticore.base.model.core.BaseECoreModelContext.class.getDeclaredField("tmpCtx");
        tmp.setAccessible(true);
        tmp.set(null, null);
    }

    @BeforeEach
    void quiet() {
        SourceCode.resetWarnings();
        ConsoleLog.clearKey("DSL_WARN");
    }

    @AfterEach
    void reset() {
        SpecFactory.valueObjectsInDto(false);
        SourceCode.resetWarnings();
        ConsoleLog.clearKey("DSL_WARN");
    }

    // ---- the entity's own DTO -----------------------------------------------------------------

    /** What SpOrderDTO shows, whatever the run does with value objects. */
    private static final List<String> AUTO_SHOWN = Arrays.asList(
            "orderId",
            "placedAt",
            // dto: annotations that neither mask nor leave the value out of the JSON
            "note",
            // a row only users may read: its key alone, flattened beside it
            "guarded.guardedId",
            // a row anyone may read (no @access): the key, and what its DTO shows - not its password,
            // and of the row it refers to only the key
            "open.openId",
            "open.title",
            "open.guarded.guardedId",
            // @access(read=public, ...): read by anyone
            "pub.publicId",
            "pub.name",
            // @extend: the parent's key is the row's key; the row is read by users only
            "child.baseId",
            // a deferred relation nests the row's own DTO, whatever @dtype says
            "brief.openId",
            "brief.title",
            "brief.guarded.guardedId");

    /**
     * Hidden: @writeonly, @nooutput, @password, @jsonignore, the runtime's @Mask, a native JsonIgnore, a native
     * WRITE_ONLY, a list; a row only users may read shows its key alone; an embedded value the DTO does not carry.
     */
    @Test
    public void theEntitysOwnDtoShowsItsValuesAndItsRelationsKeys() {
        List<String> paths = SortablePaths.of(entity(auto, "SpOrder"), auto, null);
        assertEquals("orderId", paths.get(0), paths.toString());
        assertSame(AUTO_SHOWN, paths);
    }

    /** Where the run puts value objects in DTOs, a part of the embedded value sorts - not a @writeonly one. */
    @Test
    public void anEmbeddedValueTheDtoCarriesSortsByItsShownParts() {
        SpecFactory.valueObjectsInDto(true);
        List<String> expected = new ArrayList<String>(AUTO_SHOWN);
        expected.add("address.city");
        assertSame(expected, SortablePaths.of(entity(auto, "SpOrder"), auto, null));
    }

    /** The default order sorts: its terms, signs taken off, a hidden value too - with a warning. */
    @Test
    public void theDefaultOrderSortsAndAHiddenOneIsSaid() {
        List<String> expected = new ArrayList<String>(AUTO_SHOWN);
        expected.add("cardNo");
        List<String> paths = SortablePaths.of(entity(auto, "SpOrder"), auto, "placedAt-, cardNo+ ,,orderId");
        assertSame(expected, paths);
        List<String> warned = warnings();
        assertEquals(1, warned.size(), warned.toString());
        assertTrue(
                warned.get(0).contains("@sort(placedAt-, cardNo+ ,,orderId) of SpOrder orders by cardNo"),
                warned.toString());
    }

    /** A row's own search: its key, its shown values and its relation's key. */
    @Test
    public void aRowsOwnSearchSortsByWhatItsDtoShows() {
        assertSame(
                Arrays.asList("openId", "title", "guarded.guardedId"),
                SortablePaths.of(entity(auto, "SpOpen"), auto, null));
    }

    // ---- DTOs written by hand -----------------------------------------------------------------

    /**
     * A @ref path sorts unless it ends at a @password (through a relation or a list); a DTO nested by type or by
     * @dtype sorts by what it shows, three relations deep and no deeper; a row only users may read is not followed;
     * a value object the DTO carries as something else is not taken apart; a @view without r is hidden.
     */
    @Test
    public void aDeclaredDtoSortsByItsRefsAndTheDtosItNests() {
        SpecFactory.valueObjectsInDto(true);
        List<String> paths = SortablePaths.of(entity(declared, "SbOrder"), declared, null);
        assertEquals("orderId", paths.get(0), paths.toString());
        assertSame(
                Arrays.asList(
                        "orderId",
                        "total",
                        "visible",
                        "member.login",
                        "b.bId",
                        "b.name",
                        "b.c.cId",
                        "b.c.name",
                        "b.c.d.dId",
                        "b.c.d.name"),
                paths);
    }

    // ---- the project's own sort interface -----------------------------------------------------

    /** A sort interface of the project's own cannot be given the list: none is listed, and the run says so. */
    @Test
    public void theProjectsOwnSortInterfaceListsNothingAndIsSaid() {
        List<String> props = Collections.singletonList("orderId");
        assertEquals(
                props,
                SearchSortList.listed(new EntitySpec("SpOrder")
                        .sortableInterface(SearchSortList.RUNTIME_SORTABLE)
                        .sortableProperties(props)));
        assertTrue(warnings().isEmpty(), warnings().toString());

        assertNull(SearchSortList.listed(
                new EntitySpec("SpOrder").sortableInterface("kr.co.acme.Sorted").sortableProperties(props)));
        List<String> warned = warnings();
        assertEquals(1, warned.size(), warned.toString());
        assertTrue(
                warned.get(0)
                        .contains("SpOrderSrchDTO: env.yml interface.sortable names the project's own sort"
                                + " interface (kr.co.acme.Sorted)"),
                warned.toString());
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** The same paths, the order aside (the key's place is asserted on its own). */
    private static void assertSame(List<String> expected, List<String> actual) {
        assertNotNull(actual, "no list");
        assertEquals(new TreeSet<String>(expected), new TreeSet<String>(actual), actual.toString());
        assertEquals(expected.size(), actual.size(), actual.toString());
    }

    private static Entity entity(ECoreModel model, String name) {
        Entity e = model.getEntityModels().findByName(name);
        assertNotNull(e, name);
        return e;
    }

    @SuppressWarnings("unchecked")
    private static List<String> warnings() {
        Map<String, List<String>> logs = (Map<String, List<String>>) ((Object[]) ConsoleLog.snapshot())[0];
        List<String> warned = logs.get("DSL_WARN");
        return warned == null ? Collections.<String>emptyList() : warned;
    }

    private static void domain(String name, String model) throws Exception {
        Path bp = dir.resolve("blueprint/" + name);
        Files.createDirectories(bp.resolve("model"));
        Files.write(
                bp.resolve("env.yml"),
                String.join(
                                "\n",
                                "elasticore:",
                                "  enable: true",
                                "  config:",
                                "    domainName: " + name,
                                "    framework: springboot",
                                "    mode: jpa",
                                "    j2ee: jakarta",
                                "    annotations:",
                                "      maskphone: \"@dto:io.elasticore.springboot3.mask.Mask(io.elasticore.springboot3.mask.MaskType.PHONE)\"",
                                "      hideref: \"@dto:com.fasterxml.jackson.annotation.JsonIgnore\"",
                                "      wonly: \"@dto:com.fasterxml.jackson.annotation.JsonProperty(access ="
                                        + " com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)\"",
                                "  namespace:",
                                "    base: io.acme." + name,
                                "")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(bp.resolve("model/model.yml"), model.getBytes(StandardCharsets.UTF_8));
    }
}

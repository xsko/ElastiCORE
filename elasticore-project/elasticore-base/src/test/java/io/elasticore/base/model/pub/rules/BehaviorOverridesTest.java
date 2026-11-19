package io.elasticore.base.model.pub.rules;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorOverridesTest {

    @Test
    void javaMethodsAreReadAndCommentsAreNot() {
        String source = String.join(
                "\n",
                "public class Member extends MemberBase {",
                "    // void inLineComment(String x)",
                "    /* void inBlock(String x)",
                "       void stillInBlock() */",
                "    @Override",
                "    public void withdraw(String confirmText) {",
                "        super.withdraw(confirmText);",
                "    }",
                "    protected   void   approve  () {}",
                "    public String name() { return null; }",
                "}");
        assertEquals(new TreeSet<String>(Arrays.asList("approve", "withdraw")), BehaviorOverrides.in(source, false));
    }

    @Test
    void kotlinOverridesAreReadAndOtherFunctionsAreNot() {
        String source = String.join(
                "\n",
                "open class Member : MemberBase() {",
                "    override fun withdraw(confirmText: String) { super.withdraw(confirmText) }",
                "    override   fun   approve  () {}",
                "    fun helper(x: String) {}",
                "    // override fun commented()",
                "}");
        assertEquals(new TreeSet<String>(Arrays.asList("approve", "withdraw")), BehaviorOverrides.in(source, true));
        assertTrue(BehaviorOverrides.in("void withdraw(String x) {}", true).isEmpty());
        assertTrue(BehaviorOverrides.in("override fun withdraw() {}", false).isEmpty());
    }

    @Test
    void noSourceNoOverrides() {
        assertTrue(BehaviorOverrides.in(null, false).isEmpty());
        assertTrue(BehaviorOverrides.in("", true).isEmpty());
    }

    @Test
    void overridesReadsTheNamesTheRunnerGave() {
        EntitySpec e = new EntitySpec("Member");
        assertFalse(BehaviorOverrides.overrides(e, "withdraw"));
        e.config(BehaviorOverrides.CONFIG, "");
        assertFalse(BehaviorOverrides.overrides(e, "withdraw"));
        e.config(BehaviorOverrides.CONFIG, "approve,withdraw");
        assertTrue(BehaviorOverrides.overrides(e, "withdraw"));
        assertTrue(BehaviorOverrides.overrides(e, "approve"));
        assertFalse(BehaviorOverrides.overrides(e, "draw"));
        assertFalse(BehaviorOverrides.overrides(e, "reject"));
    }
}

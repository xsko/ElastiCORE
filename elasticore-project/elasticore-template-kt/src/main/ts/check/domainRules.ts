/**
 * A hand-written consumer of the generated browser model.
 *
 * Type-checked with the generated files on every build, which is what makes
 * it a gate: a changed signature or a DTO field that
 * stopped existing fails here rather than in somebody's application.
 *
 * It asserts types, not behaviour - nothing runs it. Behaviour is held down
 * where it can be: the same condition grammar produces Java and Kotlin, and
 * both are executed against a database in their own samples, so what is left
 * for this side is the spelling. That is asserted in the generator's own
 * tests, which check that a decimal comes out as Number(...) and a collection
 * as .length.
 *
 * Not in src/main/ts itself, because the generation task empties that
 * directory of *.ts on every run.
 */
import type { OrderDTO } from '../OrderDTO';
import { hasLines, highValue } from './orderRules';
import { isValid, violations } from '../OrderInvariants';
import * as OrderApi from '../OrderApi';
import type { OrderItemDTO } from '../OrderItemDTO';

/** A named rule (orderRules.ts) answers about a value the browser already holds. */
export function isWorthShipping(order: OrderDTO): boolean {
    // Composition is composition: a function is already a value here, so the
    // JVM's Predicate.and() has nothing to translate to.
    return highValue(order) && hasLines(order);
}

/** An invariant tells a form what to say, before the request that would fail. */
export function whyNotSubmittable(order: OrderDTO): string[] {
    return isValid(order) ? [] : violations(order);
}

/**
 * The aggregate's members travel inside their root, and only there.
 *
 * There is no OrderItemApi to import - the server exposes no endpoint for a
 * member, so the generator writes no client for one. The boundary is not
 * something the browser is asked to respect; it is something it cannot reach
 * past. This function is how that is stated in code: the members are read off
 * the root, and written by sending the root.
 */
export async function linesOf(orderId: string): Promise<OrderItemDTO[]> {
    const order = await OrderApi.findById(orderId);
    return order?.items ?? [];
}

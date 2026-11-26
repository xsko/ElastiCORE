import assert from 'node:assert/strict';
import { test } from 'node:test';

import type { OrderDTO } from '../OrderDTO';
// highValue and hasLines are written by hand (orderRules.ts); the invariants are generated.
import { hasLines, highValue } from './orderRules';
import { isValid, violations } from '../OrderInvariants';

/**
 * The generated browser model, executed.
 *
 * Type-checking proved the generated TypeScript is well-formed and that a
 * consumer still compiles against it. It could not prove the rules answer
 * correctly, and on this side that gap is not academic: a decimal is declared
 * a number, as Jackson writes it, but a server set to write decimals as
 * strings sends "150.00" - and a comparison that is wrong about the type is
 * one TypeScript accepts and JavaScript answers false to, whatever the amount.
 * The one defect this language invites is precisely the one a type check
 * cannot see.
 *
 * So these run. The same rules are executed against a database in the Java
 * and Kotlin samples; this is the third language saying the same answers.
 */

// The DTO decides what a line needs - unitPrice is required, and this
// fixture learned that from the type check rather than from a reviewer.
const order = (over: Partial<OrderDTO> = {}): OrderDTO => ({
    orderDate: '2026-08-28 10:00:00',
    ...over,
});

test('a decimal is compared as a number', () => {
    assert.equal(highValue(order({ totalAmount: 150.00 })), true);

    assert.equal(highValue(order({ totalAmount: 100.00 })), false,
        'the bound is exclusive, as the condition wrote it');
});

test('and still as a number where the server sends decimals as strings', () => {
    // The assertion this whole file exists for. Written by hand as
    // order.totalAmount > 100 this is a string against a number: JavaScript
    // answers false however large the amount, and nothing reports it.
    const asString = (amount: string) => order({ totalAmount: amount as unknown as number });
    assert.equal(highValue(asString('150.00')), true);
    assert.equal(highValue(asString('9999999.99')), true,
        'a large amount must not be lost to a string comparison');
});

test('an unset field is not a match, and is not an exception either', () => {
    assert.equal(highValue(order()), false);
    assert.equal(hasLines(order()), false);
    assert.equal(highValue(null), false);
    assert.equal(highValue(undefined), false);
});

test('a collection is counted, not truthiness-tested', () => {
    assert.equal(hasLines(order({ items: [] })), false,
        'an empty array is truthy; only its length answers the question');

    assert.equal(hasLines(order({ items: [{ itemSeq: 1, quantity: 1, unitPrice: 10.00 }] })), true);
});

test('two rules combine, which is what makes them worth naming', () => {
    const worthShipping = (o: OrderDTO) => highValue(o) && hasLines(o);

    assert.equal(worthShipping(order({ totalAmount: 150.00 })), false);
    assert.equal(
        worthShipping(order({ totalAmount: 150.00, items: [{ itemSeq: 1, quantity: 1, unitPrice: 10.00 }] })),
        true);
});

test('an invariant reports every broken promise, not the first', () => {
    const broken = violations(order({ totalAmount: 150.00 }));

    assert.deepEqual(broken, ['an order has at least one line'],
        'in the domain’s own words, which is the point of generating it');
    assert.equal(isValid(order({ totalAmount: 150.00 })), false);
});

test('an invariant that is kept reports nothing', () => {
    const complete = order({ totalAmount: 150.00, items: [{ itemSeq: 1, quantity: 1, unitPrice: 10.00 }] });

    assert.deepEqual(violations(complete), []);
    assert.equal(isValid(complete), true);
});

test('nothing to check is not a violation', () => {
    assert.deepEqual(violations(null), []);
    assert.equal(isValid(undefined), true,
        'a form with no order yet has no complaint to show');
});

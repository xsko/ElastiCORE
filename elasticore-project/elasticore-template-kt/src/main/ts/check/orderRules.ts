import type { OrderDTO } from '../OrderDTO';

/**
 * The order's named rules, asked in the browser - written by hand, beside
 * the server's OrderRules (issue 370: @specification is no longer read).
 *
 * A decimal may arrive as a string ("150.00") when the server writes decimals
 * that way; a comparison written as order.totalAmount > 100 would then compare
 * a string with a number, which TypeScript accepts and JavaScript answers
 * false to. So the amount is read with Number(...).
 */

/** More than 100. */
export function highValue(order: OrderDTO | null | undefined): boolean {
    return order != null && order.totalAmount != null && Number(order.totalAmount) > 100;
}

/** At least one line: an empty array is truthy, so its length answers. */
export function hasLines(order: OrderDTO | null | undefined): boolean {
    return order != null && order.items != null && order.items.length > 0;
}

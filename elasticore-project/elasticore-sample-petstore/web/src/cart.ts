// The cart lives in the browser until checkout; the server prices it then.
import type { ItemDTO } from '../../src/main/ts/ItemDTO';

export interface CartLine {
    itemId: number;
    name: string;
    productName?: string;
    imagePath?: string;
    unitCost: number;       // what the catalog showed; the order is priced again by the server
    quantity: number;
}

const KEY = 'petstore.cart';

export function lines(): CartLine[] {
    try {
        const raw = localStorage.getItem(KEY);
        return raw ? (JSON.parse(raw) as CartLine[]) : [];
    } catch {
        return [];
    }
}

function store(all: CartLine[]): void {
    try {
        localStorage.setItem(KEY, JSON.stringify(all));
    } catch {
        // private mode: the cart lasts as long as the page
    }
    window.dispatchEvent(new Event('cart'));
}

export function add(item: ItemDTO, quantity = 1): void {
    const all = lines();
    const line = all.find((l) => l.itemId === item.itemId);
    if (line) line.quantity += quantity;
    else all.push({
        itemId: item.itemId!, name: item.name, productName: item.productName,
        imagePath: item.imagePath, unitCost: item.unitCost, quantity,
    });
    store(all);
}

export function setQuantity(itemId: number, quantity: number): void {
    store(lines().map((l) => (l.itemId === itemId ? { ...l, quantity } : l)).filter((l) => l.quantity > 0));
}

export function clear(): void {
    store([]);
}

export const count = () => lines().reduce((n, l) => n + l.quantity, 0);

export const subtotal = () => lines().reduce((sum, l) => sum + l.unitCost * l.quantity, 0);

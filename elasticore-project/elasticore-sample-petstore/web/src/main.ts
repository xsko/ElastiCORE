// YAPS Pet Store - the pages. Every call to the server goes through the
// client ElastiCORE generated from the DSL, or through shop.ts beside it.
import * as Categories from '../../src/main/ts/CategoryApi';
import * as Products from '../../src/main/ts/ProductApi';
import * as Items from '../../src/main/ts/ItemApi';
import * as Countries from '../../src/main/ts/CountryApi';
import * as Orders from '../../src/main/ts/PurchaseOrderApi';
import * as Customers from '../../src/main/ts/CustomerApi';
import { ApiError } from '../../src/main/ts/apiSupport';
import { type CustomerDTO, validateCustomer } from '../../src/main/ts/CustomerDTO';
import type { ItemDTO } from '../../src/main/ts/ItemDTO';
import type { PurchaseOrderDTO } from '../../src/main/ts/PurchaseOrderDTO';
import { type Address, validateAddress } from '../../src/main/ts/Address';
import { validateCreditCard } from '../../src/main/ts/CreditCard';
import { CreditCardType, CreditCardTypeLabel } from '../../src/main/ts/CreditCardType';
import { OrderStatusLabel } from '../../src/main/ts/OrderStatus';
import { UserRole } from '../../src/main/ts/UserRole';
import { violations } from '../../src/main/ts/PurchaseOrderInvariants';
import * as Shop from './shop';
import * as Cart from './cart';

// ---- small helpers ----------------------------------------------------------

const view = document.getElementById('view')!;
let customer: CustomerDTO | null = null;

const esc = (v: unknown) => String(v ?? '').replace(/[&<>"']/g,
    (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]!));
const money = (v: number | undefined) => '$' + (v ?? 0).toFixed(2);
const image = (path?: string) => `/images/${esc(path || 'fish1.jpg')}`;
const go = (hash: string) => { location.hash = hash; };

// Labels, checks and the server's problem details all come from the
// generated client: OrderStatusLabel, validateAddress, ApiError.detail.
const status = (s?: string) => (s ? OrderStatusLabel[s as keyof typeof OrderStatusLabel] ?? s : '');
const card = (t?: string) => (t ? CreditCardTypeLabel[t as keyof typeof CreditCardTypeLabel] ?? t : '');

/** What the server said, in words. */
function problem(e: unknown): string {
    return e instanceof ApiError ? e.detail : String(e);
}

/** A form's errors, beside its fields - from the DSL's checks or the server's 400. */
function showErrors(form: HTMLFormElement, errors: Record<string, string>, prefix = ''): boolean {
    let any = false;
    for (const [field, message] of Object.entries(errors)) {
        const input = form.querySelector<HTMLElement>(`[name="${prefix}${field}"]`);
        const slot = input?.closest('label')?.querySelector('.field-error');
        if (slot) slot.textContent = message;
        any = true;
    }
    return any;
}

function clearErrors(form: HTMLFormElement): void {
    form.querySelectorAll('.field-error').forEach((el) => { el.textContent = ''; });
}

function render(html: string): void {
    view.innerHTML = html;
    window.scrollTo(0, 0);
}

function on(selector: string, event: string, handler: (el: HTMLElement, ev: Event) => void): void {
    view.querySelectorAll<HTMLElement>(selector).forEach((el) => el.addEventListener(event, (ev) => handler(el, ev)));
}

function formData(form: HTMLFormElement): Record<string, string> {
    const out: Record<string, string> = {};
    new FormData(form).forEach((v, k) => { out[k] = String(v).trim(); });
    return out;
}

function flash(text: string, kind: 'ok' | 'error' = 'ok'): void {
    const el = document.getElementById('flash')!;
    el.textContent = text;
    el.className = `flash ${kind}`;
    setTimeout(() => { el.className = 'flash'; }, 3500);
}

// ---- header -----------------------------------------------------------------

function header(): void {
    document.getElementById('cart-count')!.textContent = String(Cart.count());
    const who = document.getElementById('who')!;
    if (customer) {
        who.innerHTML = `<a href="#/orders">${esc(customer.firstName)}'s orders</a>`
            + (customer.role === UserRole.ADMIN ? ` <a href="#/admin">Back office</a>` : '')
            + ` <a href="#/account">Account</a> <button id="signout" class="link">Sign out</button>`;
        document.getElementById('signout')!.onclick = async () => {
            await Shop.signOut();
            customer = null;
            header();
            go('#/');
        };
    } else {
        who.innerHTML = `<a href="#/signin">Sign in</a> <a href="#/signup">Sign up</a>`;
    }
}

document.getElementById('search')!.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const q = (document.getElementById('q') as HTMLInputElement).value.trim();
    if (q) go('#/search/' + encodeURIComponent(q));
});
window.addEventListener('cart', header);

// ---- catalog ----------------------------------------------------------------

async function home(): Promise<void> {
    const page = await Categories.search({ pageSize: 20 });
    render(`<section class="hero"><h1>Welcome to the YAPS Pet Store</h1>
        <p>Fish, dogs, reptiles, cats and birds - chosen with care, delivered with love.</p></section>
        <div class="grid">${page.content.map((c) => `
          <a class="card" href="#/category/${c.categoryId}">
            <h2>${esc(c.name)}</h2><p>${esc(c.description)}</p></a>`).join('')}</div>`);
}

async function category(id: number): Promise<void> {
    const [c, products] = await Promise.all([Categories.findById(id), Products.search({ categoryId: id, pageSize: 50 })]);
    if (!c) return notFound();
    render(`<p class="crumbs"><a href="#/">Home</a> / ${esc(c.name)}</p><h1>${esc(c.name)}</h1>
        <div class="grid">${products.content.map((p) => `
          <a class="card" href="#/product/${p.productId}"><h2>${esc(p.name)}</h2><p>${esc(p.description)}</p></a>`).join('')}</div>`);
}

function itemCards(items: ItemDTO[]): string {
    if (!items.length) return '<p class="empty">Nothing here yet.</p>';
    return `<div class="grid items">${items.map((i) => `
      <div class="card item">
        <a href="#/item/${i.itemId}"><img src="${image(i.imagePath)}" alt="${esc(i.name)}"></a>
        <h3><a href="#/item/${i.itemId}">${esc(i.productName)} - ${esc(i.name)}</a></h3>
        <p class="price">${money(i.unitCost)}</p>
        <button class="add" data-id="${i.itemId}">Add to cart</button>
      </div>`).join('')}</div>`;
}

function wireAdd(items: ItemDTO[]): void {
    on('button.add', 'click', (el) => {
        const item = items.find((i) => i.itemId === Number(el.dataset.id));
        if (item) {
            Cart.add(item);
            flash(`${item.productName} - ${item.name} is in your cart`);
        }
    });
}

async function product(id: number): Promise<void> {
    const [p, items] = await Promise.all([Products.findById(id), Items.search({ productId: id })]);
    if (!p) return notFound();
    render(`<p class="crumbs"><a href="#/">Home</a> / <a href="#/category/${p.categoryId}">Category</a> / ${esc(p.name)}</p>
        <h1>${esc(p.name)}</h1><p class="lead">${esc(p.description)}</p>${itemCards(items.content)}`);
    wireAdd(items.content);
}

async function item(id: number): Promise<void> {
    const i = await Items.findById(id);
    if (!i) return notFound();
    render(`<p class="crumbs"><a href="#/">Home</a> / <a href="#/product/${i.productId}">${esc(i.productName)}</a> / ${esc(i.name)}</p>
        <div class="detail"><img src="${image(i.imagePath)}" alt="${esc(i.name)}">
          <div><h1>${esc(i.productName)} - ${esc(i.name)}</h1><p>${esc(i.description)}</p>
            <p class="price big">${money(i.unitCost)}</p>
            <label>Quantity <input id="qty" type="number" min="1" value="1"></label>
            <button class="add primary" data-id="${i.itemId}">Add to cart</button></div></div>`);
    on('button.add', 'click', () => {
        const qty = Math.max(1, Number((document.getElementById('qty') as HTMLInputElement).value) || 1);
        Cart.add(i, qty);
        flash(`${qty} x ${i.productName} - ${i.name} added`);
    });
}

async function search(q: string): Promise<void> {
    // A % makes the generated search a LIKE; the name holds the match.
    const items = await Items.search({ name: `%${q}%`, pageSize: 50 });
    render(`<h1>Search: ${esc(q)}</h1>${itemCards(items.content)}`);
    wireAdd(items.content);
}

// ---- cart and checkout ------------------------------------------------------

function cart(): void {
    const lines = Cart.lines();
    if (!lines.length) {
        render(`<h1>Your cart</h1><p class="empty">Your cart is empty. <a href="#/">Go shopping</a></p>`);
        return;
    }
    render(`<h1>Your cart</h1><table class="lines"><thead><tr><th></th><th>Item</th><th>Price</th><th>Quantity</th><th>Subtotal</th></tr></thead>
      <tbody>${lines.map((l) => `<tr>
        <td><img class="thumb" src="${image(l.imagePath)}" alt=""></td>
        <td><a href="#/item/${l.itemId}">${esc(l.productName)} - ${esc(l.name)}</a></td>
        <td>${money(l.unitCost)}</td>
        <td><input class="qty" type="number" min="0" value="${l.quantity}" data-id="${l.itemId}"></td>
        <td>${money(l.unitCost * l.quantity)}</td></tr>`).join('')}</tbody></table>
      <p class="total">Subtotal ${money(Cart.subtotal())} <small>(VAT and discount at checkout)</small></p>
      <p class="actions"><button id="empty" class="link">Empty the cart</button>
        <button id="checkout" class="primary">Check out</button></p>`);
    on('input.qty', 'change', (el) => {
        Cart.setQuantity(Number(el.dataset.id), Math.max(0, Number((el as HTMLInputElement).value) || 0));
        cart();
    });
    document.getElementById('empty')!.onclick = () => { Cart.clear(); cart(); };
    document.getElementById('checkout')!.onclick = () => go(customer ? '#/checkout' : '#/signin?next=checkout');
}

function addressFields(prefix: string, a?: Address, countries: { isoCode?: string; printableName: string }[] = []): string {
    const v = (k: keyof Address) => esc(a?.[k]);
    return `<fieldset><legend>${prefix === 'd' ? 'Delivery address' : 'Home address'}</legend>
      <label>Street <input name="${prefix}.street1" required maxlength="50" value="${v('street1')}"><span class="field-error"></span></label>
      <label>Street (2) <input name="${prefix}.street2" maxlength="50" value="${v('street2')}"></label>
      <div class="row"><label>City <input name="${prefix}.city" required maxlength="50" value="${v('city')}"><span class="field-error"></span></label>
      <label>State <input name="${prefix}.state" maxlength="50" value="${v('state')}"></label>
      <label>Zip code <input name="${prefix}.zipcode" required maxlength="10" value="${v('zipcode')}"><span class="field-error"></span></label></div>
      <label>Country <select name="${prefix}.countryCode" required>${countries.map((c) =>
        `<option value="${esc(c.isoCode)}" ${c.isoCode === (a?.countryCode || 'US') ? 'selected' : ''}>${esc(c.printableName)}</option>`).join('')}
      </select></label></fieldset>`;
}

function addressFrom(data: Record<string, string>, prefix: string): Address {
    const get = (k: string) => data[`${prefix}.${k}`] || undefined;
    return {
        street1: get('street1')!, street2: get('street2'), city: get('city')!,
        state: get('state'), zipcode: get('zipcode')!, countryCode: get('countryCode')!,
    };
}

let countryCache: { isoCode?: string; printableName: string }[] | null = null;
async function countries() {
    if (!countryCache) countryCache = (await Countries.search({ pageSize: 300, sortColumn: 'printableName', sortAscending: true })).content;
    return countryCache;
}

async function checkout(): Promise<void> {
    if (!customer) return go('#/signin?next=checkout');
    const lines = Cart.lines();
    if (!lines.length) return go('#/cart');
    render(`<h1>Check out</h1><form id="order" class="form" novalidate>
        ${addressFields('d', customer.homeAddress, await countries())}
        <fieldset><legend>Credit card</legend>
          <label>Card type <select name="card.creditCardType">${Object.values(CreditCardType).map((t) =>
            `<option value="${t}">${esc(card(t))}</option>`).join('')}</select></label>
          <div class="row"><label>Number <input name="card.creditCardNumber" maxlength="30" inputmode="numeric" autocomplete="cc-number"><span class="field-error"></span></label>
          <label>Expiry (MM/YY) <input name="card.creditCardExpDate" maxlength="5" autocomplete="cc-exp"><span class="field-error"></span></label></div>
        </fieldset>
        <p class="total">${lines.length} line(s), subtotal ${money(Cart.subtotal())}</p>
        <p class="error" id="error"></p>
        <button class="primary">Place the order</button></form>`);
    on('#order', 'submit', async (form, ev) => {
        ev.preventDefault();
        const f = form as HTMLFormElement;
        const data = formData(f);
        const order: PurchaseOrderDTO = {
            deliveryAddress: addressFrom(data, 'd'),
            creditCard: {
                creditCardType: data['card.creditCardType'] as CreditCardType,
                creditCardNumber: data['card.creditCardNumber'], creditCardExpDate: data['card.creditCardExpDate'],
            },
            // Items and quantities only: the price is the shop's to say.
            lines: lines.map((l) => ({ itemId: l.itemId, quantity: l.quantity })),
        };
        // The DSL's own checks, before the round trip.
        clearErrors(f);
        const addressWrong = showErrors(f, validateAddress(order.deliveryAddress), 'd.');
        const cardWrong = showErrors(f, validateCreditCard(order.creditCard), 'card.');
        const wrong = addressWrong || cardWrong;
        const broken = violations(order);
        if (wrong || broken.length) {
            document.getElementById('error')!.textContent = broken.join(', ');
            return;
        }
        try {
            // The generated create: the server makes the signed-in customer its owner.
            const placed = await Orders.create(order);
            Cart.clear();
            flash('Thank you - your order is placed');
            go('#/orders/' + placed.orderId);
        } catch (e) {
            document.getElementById('error')!.textContent = problem(e);
        }
    });
}

// ---- orders -----------------------------------------------------------------

function orderTable(orders: PurchaseOrderDTO[], admin = false): string {
    if (!orders.length) return '<p class="empty">No orders yet.</p>';
    return `<table class="lines"><thead><tr><th>Order</th><th>Date</th><th>Status</th><th>Total</th>${admin ? '<th>Customer</th><th></th>' : ''}</tr></thead>
      <tbody>${orders.map((o) => `<tr>
        <td><a href="#/orders/${esc(o.orderId)}">${esc(o.orderId?.slice(0, 8))}</a></td><td>${esc(o.orderDate)}</td>
        <td><span class="status s-${esc(o.status)}">${esc(status(o.status))}</span></td><td>${money(o.total)}</td>
        ${admin ? `<td>#${esc(o.customerId)}</td><td>${Orders.canShip(o)
            ? `<button class="ship" data-id="${esc(o.orderId)}">Ship</button>` : ''}</td>` : ''}</tr>`).join('')}</tbody></table>`;
}

async function myOrders(): Promise<void> {
    if (!customer) return go('#/signin?next=orders');
    // The generated search: a customer's is narrowed to their own orders by the server.
    const page = await Orders.search({ sortColumn: 'orderDate', sortAscending: false });
    render(`<h1>Your orders</h1>${orderTable(page.content)}`);
}

async function order(id: string): Promise<void> {
    if (!customer) return go('#/signin');
    const o = await Orders.findById(id);
    if (!o) return notFound();
    const a = o.deliveryAddress;
    render(`<p class="crumbs"><a href="#/orders">Orders</a> / ${esc(o.orderId)}</p>
      <h1>Order of ${esc(o.orderDate)} <span class="status s-${esc(o.status)}">${esc(status(o.status))}</span></h1>
      <table class="lines"><thead><tr><th>Item</th><th>Price</th><th>Quantity</th><th>Subtotal</th></tr></thead>
      <tbody>${(o.lines ?? []).map((l) => `<tr><td><a href="#/item/${l.itemId}">${esc(l.itemName)}</a></td>
        <td>${money(l.unitCost)}</td><td>${l.quantity}</td><td>${money(l.subTotal)}</td></tr>`).join('')}</tbody></table>
      <dl class="totals"><dt>Total without VAT</dt><dd>${money(o.totalWithoutVat)}</dd>
        <dt>VAT (${o.vatRate}%)</dt><dd>${money(o.vat)}</dd>
        <dt>Discount (${o.discountRate}%)</dt><dd>- ${money(o.discount)}</dd>
        <dt><b>Total</b></dt><dd><b>${money(o.total)}</b></dd></dl>
      <p>Delivered to ${esc(a?.street1)} ${esc(a?.street2)}, ${esc(a?.city)} ${esc(a?.zipcode)} ${esc(a?.countryCode)}
        - paid by ${esc(card(o.creditCard?.creditCardType))} (expires ${esc(o.creditCard?.creditCardExpDate)})</p>
      ${Orders.canCancel(o) ? '<button id="cancel">Cancel this order</button>' : ''}<p class="error" id="error"></p>`);
    const cancel = document.getElementById('cancel');
    if (cancel) cancel.onclick = async () => {
        try {
            await Orders.cancel(id);
            flash('The order is cancelled');
            order(id);
        } catch (e) {
            document.getElementById('error')!.textContent = problem(e);
        }
    };
}

async function admin(): Promise<void> {
    if (customer?.role !== UserRole.ADMIN) return go('#/signin');
    const page = await Orders.search({ sortColumn: 'orderDate', sortAscending: false, pageSize: 50 });
    render(`<h1>Back office - orders</h1>${orderTable(page.content, true)}<p class="error" id="error"></p>`);
    on('button.ship', 'click', async (el) => {
        try {
            await Orders.ship(el.dataset.id!);
            flash('Shipped');
            admin();
        } catch (e) {
            document.getElementById('error')!.textContent = problem(e);
        }
    });
}

// ---- account ----------------------------------------------------------------

function signIn(next: string): void {
    render(`<h1>Sign in</h1><form id="signin" class="form narrow">
      <label>Login <input name="login" required autocomplete="username"></label>
      <label>Password <input name="password" type="password" required autocomplete="current-password"></label>
      <p class="error" id="error"></p><button class="primary">Sign in</button>
      <p>New here? <a href="#/signup">Create an account</a>. The demo has <b>user/user</b> and <b>admin/admin</b>.</p></form>`);
    on('#signin', 'submit', async (form, ev) => {
        ev.preventDefault();
        const d = formData(form as HTMLFormElement);
        try {
            customer = await Shop.signIn(d.login, d.password);
            header();
            go('#/' + (next || ''));
        } catch {
            document.getElementById('error')!.textContent = 'Wrong login or password';
        }
    });
}

async function profileForm(id: string, c?: CustomerDTO): Promise<string> {
    return `<form id="${id}" class="form" novalidate>
      ${c ? '' : `<div class="row"><label>Login <input name="login" maxlength="10" autocomplete="username"><span class="field-error"></span></label>
      <label>Password <input name="password" type="password" autocomplete="new-password"><span class="field-error"></span></label></div>`}
      <div class="row"><label>First name <input name="firstName" maxlength="50" value="${esc(c?.firstName)}"><span class="field-error"></span></label>
      <label>Last name <input name="lastName" maxlength="50" value="${esc(c?.lastName)}"><span class="field-error"></span></label></div>
      <div class="row"><label>Email <input name="email" maxlength="100" value="${esc(c?.email)}"><span class="field-error"></span></label>
      <label>Telephone <input name="telephone" maxlength="30" value="${esc(c?.telephone)}"></label>
      <label>Date of birth <input name="dateOfBirth" type="date" value="${esc(c?.dateOfBirth)}"></label></div>
      ${addressFields('h', c?.homeAddress, await countries())}
      <p class="error" id="error"></p><button class="primary">${c ? 'Save' : 'Create my account'}</button></form>`;
}

function customerFrom(d: Record<string, string>): CustomerDTO {
    return {
        login: d.login, password: d.password, firstName: d.firstName, lastName: d.lastName,
        email: d.email || undefined, telephone: d.telephone || undefined,
        dateOfBirth: d.dateOfBirth || undefined, homeAddress: addressFrom(d, 'h'),
    };
}

async function signUp(): Promise<void> {
    render(`<h1>Create an account</h1>${await profileForm('signup')}`);
    on('#signup', 'submit', async (form, ev) => {
        ev.preventDefault();
        const f = form as HTMLFormElement;
        const input = customerFrom(formData(f));
        clearErrors(f);
        const customerWrong = showErrors(f, validateCustomer(input));
        const homeWrong = showErrors(f, validateAddress(input.homeAddress), 'h.');
        if (customerWrong || homeWrong) return;
        try {
            customer = await Shop.signUp(input);
            header();
            flash(`Welcome, ${customer.firstName}`);
            go('#/');
        } catch (e) {
            if (e instanceof ApiError) showErrors(f, e.fieldErrors);
            document.getElementById('error')!.textContent = problem(e);
        }
    });
}

async function account(): Promise<void> {
    if (!customer) return go('#/signin?next=account');
    render(`<h1>Your account <small>${esc(customer.login)}</small></h1>${await profileForm('profile', customer)}`);
    on('#profile', 'submit', async (form, ev) => {
        ev.preventDefault();
        try {
            // The generated update: a customer may change their own record, not their role.
            const changes = customerFrom(formData(form as HTMLFormElement));
            customer = await Customers.update({ ...changes, customerId: customer!.customerId, login: customer!.login });
            header();
            flash('Saved');
        } catch (e) {
            document.getElementById('error')!.textContent = problem(e);
        }
    });
}

function notFound(): void {
    render(`<h1>Not found</h1><p><a href="#/">Back to the shop</a></p>`);
}

// ---- routing ----------------------------------------------------------------

async function route(): Promise<void> {
    const [path, query] = location.hash.replace(/^#\/?/, '').split('?');
    const [page, arg] = path.split('/');
    const next = new URLSearchParams(query).get('next') || '';
    try {
        switch (page) {
            case '': return await home();
            case 'category': return await category(Number(arg));
            case 'product': return await product(Number(arg));
            case 'item': return await item(Number(arg));
            case 'search': return await search(decodeURIComponent(arg || ''));
            case 'cart': return cart();
            case 'checkout': return await checkout();
            case 'orders': return arg ? await order(arg) : await myOrders();
            case 'admin': return await admin();
            case 'signin': return signIn(next);
            case 'signup': return await signUp();
            case 'account': return await account();
            default: return notFound();
        }
    } catch (e) {
        render(`<h1>Something went wrong</h1><p class="error">${esc(problem(e))}</p>`);
    }
}

window.addEventListener('hashchange', route);
(async () => {
    customer = await Shop.me();
    header();
    route();
})();

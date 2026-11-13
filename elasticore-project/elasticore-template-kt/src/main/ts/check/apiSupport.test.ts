import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';

import { ApiError, configureApi, request, requestOrNull } from '../apiSupport';

/**
 * The shared runtime of the generated clients, against what the server
 * answers. A void endpoint is not always a 204: the batch update answers
 * ResponseEntity.ok().build(), a 200 with no body, and a client that parsed
 * every 200 as JSON failed there on a request the server had done.
 */

const realFetch = globalThis.fetch;

afterEach(() => {
    globalThis.fetch = realFetch;
});

function answer(status: number, body: string | null): void {
    globalThis.fetch = (async () => new Response(body, { status })) as typeof fetch;
}

test('a 200 with no body is no value, not a failed parse', async () => {
    answer(200, '');
    assert.equal(await request<void>('PUT', '/api/order/batch', []), undefined);
});

test('a 204 is no value', async () => {
    answer(204, null);
    assert.equal(await request<void>('POST', '/api/customer/signout'), undefined);
});

test('a body is read as JSON', async () => {
    answer(200, '{"orderId":7}');
    assert.deepEqual(await request<{ orderId: number }>('POST', '/api/order', {}), { orderId: 7 });
    answer(200, '[true,false]');
    assert.deepEqual(await request<boolean[]>('POST', '/api/order/delete', [1, 2]), [true, false]);
});

test('a find that has nothing to return answers null', async () => {
    answer(404, '');
    assert.equal(await requestOrNull('GET', '/api/order/7'), null);
    answer(200, '');
    assert.equal(await requestOrNull('GET', '/api/order/7'), null);
    answer(200, '{"orderId":7}');
    assert.deepEqual(await requestOrNull('GET', '/api/order/7'), { orderId: 7 });
});

test('without configureApi a request sends what it always did', async () => {
    let seen: RequestInit | undefined;
    globalThis.fetch = (async (_url: string, init?: RequestInit) => {
        seen = init;
        return new Response('', { status: 200 });
    }) as typeof fetch;
    await request<void>('PUT', '/api/order/batch', [1]);
    assert.deepEqual(seen, { method: 'PUT', headers: { 'content-type': 'application/json' }, body: '[1]' });
});

test('configureApi: shared init, a per-call init over it, and its own fetch', async () => {
    const calls: { url: string; init: RequestInit }[] = [];
    let token = 'a';
    configureApi({
        baseUrl: 'https://api.example.com/',
        fetch: (async (url: string, init: RequestInit) => {
            calls.push({ url, init });
            return new Response('{"orderId":7}', { status: 200 });
        }) as typeof fetch,
        init: async () => ({ credentials: 'include', headers: { Authorization: 'Bearer ' + token, 'X-App': 'shop' } }),
    });
    try {
        await request('POST', '/api/order', {});
        token = 'b';
        await requestOrNull('GET', '/api/order/7', { headers: new Headers({ 'X-App': 'admin' }), cache: 'no-store' });
    } finally {
        configureApi({ baseUrl: '', fetch: ((url: string, init: RequestInit) => globalThis.fetch(url, init)) as typeof fetch, init: {} });
    }
    assert.equal(calls[0].url, 'https://api.example.com/api/order');
    assert.equal(calls[0].init.credentials, 'include');
    assert.equal(calls[0].init.method, 'POST');
    assert.deepEqual(calls[0].init.headers,
        { 'content-type': 'application/json', authorization: 'Bearer a', 'x-app': 'shop' });
    assert.equal(calls[1].init.method, 'GET');
    assert.equal(calls[1].init.cache, 'no-store');
    assert.deepEqual(calls[1].init.headers,
        { 'content-type': 'application/json', authorization: 'Bearer b', 'x-app': 'admin' });
    assert.equal(calls[1].init.body, undefined);
});

test('a refusal is still an ApiError', async () => {
    answer(400, '{"detail":"refused"}');
    await assert.rejects(request('PUT', '/api/order/batch', []),
        (e: unknown) => e instanceof ApiError && e.status === 400 && e.detail === 'refused');
});

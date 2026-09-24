// Step 2 of the module: "make sure you have enough data".
// Creates customers and catalog items once (setup), then one order per iteration.
//   k6 run seed-data.js                 -> 500 orders
//   k6 run -e ORDERS=2000 seed-data.js  -> more
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, authHeaders, getAccessToken } from './auth.js';

const CUSTOMERS = Number(__ENV.CUSTOMERS || 20);
const ITEMS = Number(__ENV.ITEMS || 50);
const ORDERS = Number(__ENV.ORDERS || 500);

export const options = {
    scenarios: {
        orders: { executor: 'shared-iterations', vus: 5, iterations: ORDERS, maxDuration: '10m' },
    },
    setupTimeout: '5m',
};

export function setup() {
    const token = getAccessToken();
    const run = Date.now(); // Unique names: the script can be run several times
    const customerIds = [];
    for (let i = 0; i < CUSTOMERS; i++) {
        const res = http.post(`${BASE_URL}/users`,
            JSON.stringify({ username: `customer-${run}-${i}`, password: 'CustomerPassw0rd!' }),
            authHeaders(token));
        check(res, { 'customer created': (r) => r.status === 201 });
        customerIds.push(res.json('id'));
    }
    const itemIds = [];
    for (let i = 0; i < ITEMS; i++) {
        const res = http.post(`${BASE_URL}/items`,
            JSON.stringify({ productName: `Product ${run}-${i}`, price: Math.round((5 + Math.random() * 195) * 100) / 100 }),
            authHeaders(token));
        check(res, { 'item created': (r) => r.status === 201 });
        itemIds.push(res.json('id'));
    }
    return { token, customerIds, itemIds };
}

export default function (data) {
    // 1 to 4 distinct items per order, each with a quantity between 1 and 3
    const lines = [];
    const used = new Set();
    const count = 1 + Math.floor(Math.random() * 4);
    while (lines.length < count) {
        const id = data.itemIds[Math.floor(Math.random() * data.itemIds.length)];
        if (!used.has(id)) {
            used.add(id);
            lines.push({ id, quantity: 1 + Math.floor(Math.random() * 3) });
        }
    }
    const customer = data.customerIds[Math.floor(Math.random() * data.customerIds.length)];
    const res = http.post(`${BASE_URL}/orders`,
        JSON.stringify({ customer: { id: customer }, items: lines }),
        authHeaders(data.token));
    check(res, { 'order created': (r) => r.status === 201 });
}

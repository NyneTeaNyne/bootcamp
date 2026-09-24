// Step 3 of the module: "As a user, I want to list all orders and select some of them".
//   k6 run -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_EXPORT=./report.html ./order-performance-plan.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, authHeaders, getAccessToken } from './auth.js';

export const options = {
    scenarios: {
        ramp_test: {
            executor: 'ramping-arrival-rate', // Starts iterations independently, simulate multiple users doing same action.
            startRate: 1,                     // Start with 1 request per second
            timeUnit: '1s',                   // Time unit for the rate
            preAllocatedVUs: 5,
            maxVUs: 20,
            stages: [
                { target: 50, duration: '1m' }, // Increase to 20 requests per second over 1 minute
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],   // http errors should be less than 1%
        http_req_duration: ['avg<100'],   // average http response should be less than 100ms
        // Same measure per endpoint (informative: the list and the detail do not cost the same)
        'http_req_duration{name:orders_list}': ['avg<100'],
        'http_req_duration{name:order_detail}': ['avg<100'],
    },
};

export function setup() {
    return { token: getAccessToken() };
}

export default function (data) {
    const params = authHeaders(data.token);

    const response = http.get(`${BASE_URL}/orders`, { ...params, tags: { name: 'orders_list' } });
    check(response, { 'list is 200': (r) => r.status === 200 });
    const orders = response.json();
    if (!Array.isArray(orders) || orders.length === 0) {
        return;
    }

    const randomId = orders[Math.floor(Math.random() * orders.length)].id;
    const detail = http.get(`${BASE_URL}/orders/${randomId}`, { ...params, tags: { name: 'order_detail' } });
    check(detail, { 'order is 200': (r) => r.status === 200 });
}

const assert = require('assert');
const WebSocket = require('ws');
const bridge = require('./server.js');

const PORT = process.env.PORT || 3000;

function run() {
    // --- Pure function tests ---
    const args = bridge.buildClineArgs();
    assert(args.includes('--json'), '--json present');
    assert(args.includes('--auto-approve'), '--auto-approve present');
    const ai = args.indexOf('--auto-approve');
    assert.strictEqual(args[ai + 1], 'true', 'auto-approve true');

    assert.strictEqual(bridge.extractDelta({ text: 'hi' }), 'hi');
    assert.strictEqual(bridge.extractDelta({ content: 'c' }), 'c');
    assert.strictEqual(bridge.extractDelta({ delta: 'd' }), 'd');
    assert.strictEqual(bridge.extractDelta({ value: 'v' }), 'v');
    assert.strictEqual(bridge.extractDelta({ chunk: 'k' }), 'k');
    assert.strictEqual(bridge.extractDelta({ num: 1 }), '');
    assert.strictEqual(bridge.extractDelta({}), '');
    console.log('pure functions OK');

    // --- Auth-gating integration test ---
    const ws = new WebSocket(`ws://localhost:${PORT}`);
    let step = 0;
    const received = [];
    let finished = false;

    ws.on('open', () => {
        // Without auth, chat_message must be rejected
        ws.send(JSON.stringify({ type: 'chat_message', payload: { message: { content: 'hello' } } }));
    });

    ws.on('message', (data) => {
        let msg;
        try { msg = JSON.parse(data.toString()); } catch (e) { return; }
        received.push(msg.type);

        if (step === 0 && msg.type === 'error' && /Non autenticato/.test(msg.payload.error)) {
            step = 1;
            // Invalid token must yield auth_error
            ws.send(JSON.stringify({ type: 'auth', payload: { token: 'ghp_invalidtoken' } }));
        } else if (step === 1 && msg.type === 'auth_error') {
            step = 2;
            console.log('auth gating OK ->', msg.payload.error);
            finished = true;
            try { ws.close(); } catch (e) {}
            finish(0);
        }
    });

    ws.on('error', (e) => {
        console.error('WS error:', e.message);
        if (!finished) finish(1);
    });

    setTimeout(() => {
        if (!finished) {
            console.error('TIMEOUT. received types:', received.join(', '));
            finish(1);
        }
    }, 8000);
}

let done = false;
function finish(code) {
    if (done) return;
    done = true;
    try { bridge.wss.close(); } catch (e) { /* ignore */ }
    bridge.server.close(() => process.exit(code));
    setTimeout(() => process.exit(code), 300);
}

run();

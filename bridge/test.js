const assert = require('assert');
const bridge = require('./server.js');

const args = bridge.buildClineArgs({
    provider: 'openrouter',
    model: 'qwen/qwen3-coder:free',
    apiKey: 'test-openrouter-key',
    configDir: '/tmp/cline-mobile-test'
});
assert(args.includes('--json'), '--json present');
assert(args.includes('--auto-approve') && args[args.indexOf('--auto-approve') + 1] === 'true');
assert(args.includes('--config') && args[args.indexOf('--config') + 1] === '/tmp/cline-mobile-test');
assert(args.includes('-P') && args[args.indexOf('-P') + 1] === 'openrouter');
assert(args.includes('-m') && args[args.indexOf('-m') + 1] === 'qwen/qwen3-coder:free');
assert(args.includes('-k') && args[args.indexOf('-k') + 1] === 'test-openrouter-key');
assert.notStrictEqual(
    bridge.conversationKey('client', 'chat-one'),
    bridge.conversationKey('client', 'chat-two'),
    'different chats must keep separate Cline context'
);
assert.strictEqual(
    bridge.formatClineError({ type: 'error', error: { message: 'Provider rejected request', status: 401 } }),
    'message: Provider rejected request — status: 401',
    'nested provider errors should be shown with their HTTP status'
);
assert.strictEqual(
    bridge.formatClineError({ type: 'error', text: 'Invalid key sk-or-v1-secret' }),
    'text: Invalid key [REDACTED]',
    'provider error details must not expose API keys'
);
assert.match(
    bridge.formatClineError({ type: 'error', retryable: false }),
    /fields: retryable/,
    'an error with unknown fields should provide diagnostic field names'
);

assert.strictEqual(bridge.extractDelta({ text: 'hi' }), 'hi');
assert.strictEqual(bridge.extractDelta({ content: 'c' }), 'c');
assert.strictEqual(bridge.extractDelta({ delta: 'd' }), 'd');
assert.strictEqual(bridge.extractDelta({ value: 'v' }), 'v');
assert.strictEqual(bridge.extractDelta({ chunk: 'k' }), 'k');
assert.strictEqual(bridge.extractDelta({ num: 1 }), '');
assert.strictEqual(bridge.extractDelta({}), '');

bridge.server.close(() => {
    bridge.wss.close();
    console.log('Bridge tests passed');
});

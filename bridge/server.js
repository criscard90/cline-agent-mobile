const http = require('http');
const WebSocket = require('ws');

// Configuration
const PORT = process.env.PORT || 3000;
// GitHub token: env var first, otherwise the token sent by the app via 'auth'
let activeToken = process.env.GITHUB_TOKEN || null;
const GITHUB_REPO = process.env.GITHUB_REPO || '';
const GITHUB_OWNER = process.env.GITHUB_OWNER || '';

// In-memory storage for connected clients and state
const clients = new Map();
const codespaces = new Map();
const messageQueue = [];
let serverState = {
    connected: false,
    authenticated: false,
    version: '1.0.0',
    bridgeUrl: `http://localhost:${PORT}`,
    status: 'disconnected'
};

// GitHub REST API helper
async function githubRequest(path, options = {}) {
    if (!activeToken) {
        throw new Error('No GitHub token: set GITHUB_TOKEN env var or authenticate from the app');
    }
    const url = new URL(path, 'https://api.github.com');
    const headers = {
        'Authorization': `Bearer ${activeToken}`,
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers
    };

    const response = await fetch(url.toString(), {
        method: options.method || 'GET',
        headers: headers,
        body: options.body
    });

    if (!response.ok) {
        const error = await response.text();
        throw new Error(`GitHub API error ${response.status}: ${error}`);
    }

    // Some endpoints (e.g. DELETE) answer with an empty or non-JSON body
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

// Get list of codespaces for the authenticated user
async function listMyCodespaces() {
    const data = await githubRequest('/user/codespaces');
    // GET /user/codespaces returns { total_count, codespaces } (defensively also accept a bare array)
    const items = Array.isArray(data) ? data : (data && data.codespaces) || [];
    return items.map((cs) => ({
        id: cs.id,
        name: cs.name,
        repository: cs.repository,
        container_opened_at: cs.container_opened_at,
        status: cs.status,
        state: cs.state,
        url: cs.url,
        web_url: cs.web_url,
        ssh_url: cs.ssh_url,
        model: cs.model,
        region: cs.region
    }));
}

// Get a specific codespace
async function getCodespace(name) {
    const data = await githubRequest(`/user/codespaces/${encodeURIComponent(name)}`);
    return {
        id: data.id,
        name: data.name,
        repository: data.repository,
        container_opened_at: data.container_opened_at,
        status: data.status,
        state: data.state,
        url: data.url,
        web_url: data.web_url,
        ssh_url: data.ssh_url,
        model: data.model,
        region: data.region
    };
}

// Create a new codespace in a repository (the repository comes from the path)
async function createCodespace(repo, options = {}) {
    const [owner, repoName] = String(repo).split('/');
    if (!owner || !repoName) {
        throw new Error(`Invalid repository "${repo}": expected owner/repo`);
    }
    const data = await githubRequest(
        `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repoName)}/codespaces`,
        {
            method: 'POST',
            body: JSON.stringify({
                display_name: options.displayName,
                ref: options.ref,
                machine: options.machine,
                location: options.location
            })
        }
    );
    return {
        id: data.id,
        name: data.name,
        repository: data.repository,
        container_opened_at: data.container_opened_at,
        status: data.status,
        state: data.state,
        url: data.url,
        web_url: data.web_url,
        ssh_url: data.ssh_url,
        model: data.model,
        region: data.region
    };
}

// Start a codespace
async function startCodespace(name) {
    const data = await githubRequest(`/user/codespaces/${encodeURIComponent(name)}/start`, {
        method: 'POST'
    });
    return {
        id: data.id,
        name: data.name,
        state: data.state,
        status: data.status
    };
}

// Stop a codespace
async function stopCodespace(name) {
    const data = await githubRequest(`/user/codespaces/${encodeURIComponent(name)}/stop`, {
        method: 'POST'
    });
    return {
        id: data.id,
        name: data.name,
        state: data.state,
        status: data.status
    };
}

// Restart a codespace
async function restartCodespace(name) {
    const data = await githubRequest(`/user/codespaces/${encodeURIComponent(name)}/restart`, {
        method: 'POST'
    });
    return {
        id: data.id,
        name: data.name,
        state: data.state,
        status: data.status
    };
}

// Delete a codespace (DELETE /user/codespaces/{name} → 202 Accepted, empty JSON body)
async function deleteCodespace(name) {
    await githubRequest(`/user/codespaces/${encodeURIComponent(name)}`, {
        method: 'DELETE'
    });
    return {
        id: null,
        name: name,
        state: 'deleted',
        status: 'ended'
    };
}

// Broadcast state to all connected clients
function broadcastState() {
    const state = {
        type: 'status',
        payload: serverState
    };
    const json = JSON.stringify(state);
    for (const [id, client] of clients.entries()) {
        if (client.readyState === WebSocket.OPEN) {
            client.send(json);
        }
    }
}
// Handle WebSocket connection
function handleWebSocketConnection(ws, request) {
    const clientId = Math.random().toString(36).substring(2);
    clients.set(clientId, ws);

    ws.on('message', (data) => {
        try {
            const message = JSON.parse(data.toString());
            handleMessage(clientId, message);
        } catch (e) {
            ws.send(JSON.stringify({
                type: 'error',
                payload: { error: 'Invalid message format' }
            }));
        }
    });

    ws.on('close', () => {
        clients.delete(clientId);
        console.log(`Client ${clientId} disconnected`);
        broadcastState();
    });

    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
        clients.delete(clientId);
    });

    // Send connection confirmation
    ws.send(JSON.stringify({
        type: 'status',
        payload: {
            ...serverState,
            status: 'connecting'
        }
    }));

    console.log(`Client ${clientId} connected`);
}

function handleMessage(clientId, message) {
    switch (message.type) {
        case 'connect':
            // Connection already handled via WebSocket
            break;

        case 'auth': {
            // The app can provide its own token: use it as fallback for the env var
            const clientToken = message.payload && message.payload.token;
            if (clientToken) {
                activeToken = clientToken;
            }
            if (activeToken) {
                serverState.authenticated = true;
                serverState.connected = true;
                serverState.status = 'authenticated';
                broadcastState();
                wsFor(clientId).send(JSON.stringify({
                    type: 'auth_success',
                    payload: { bridgeState: serverState }
                }));
            } else {
                wsFor(clientId).send(JSON.stringify({
                    type: 'auth_error',
                    payload: { error: 'No GITHUB_TOKEN configured' }
                }));
            }
            break;
        }

        case 'ping':
            ws.send(JSON.stringify({
                type: 'pong'
            }));
            break;

        case 'command':
            handleCommand(clientId, message.payload.command);
            break;

        case 'action_approve':
            handleActionApprove(clientId, message.payload.actionId);
            break;

        case 'action_deny':
            handleActionDeny(clientId, message.payload.actionId);
            break;

        default:
            console.log(`Unhandled message type: ${message.type}`);
    }
}

function handleCommand(clientId, command) {
    console.log(`Executing command: ${command}`);

    // Send command to output channel
    // In a real app, this would use child_process or a terminal API
    const output = `Command executed: ${command}\n`;
    wsFor(clientId).send(JSON.stringify({
        type: 'chat_stream',
        payload: { text: output }
    }));

    wsFor(clientId).send(JSON.stringify({
        type: 'chat_message',
        payload: {
            role: 'user',
            text: command,
            timestamp: Date.now()
        }
    }));

    // Add simulated response
    setTimeout(() => {
        const response = `Output for: ${command}\n[This would be the actual command output]`;
        wsFor(clientId).send(JSON.stringify({
            type: 'chat_stream',
            payload: { text: response }
        }));

        wsFor(clientId).send(JSON.stringify({
            type: 'chat_message',
            payload: {
                role: 'assistant',
                text: response,
                timestamp: Date.now()
            }
        }));
    }, 1000);
}

function handleActionApprove(clientId, actionId) {
    console.log(`Action approved: ${actionId}`);
    wsFor(clientId).send(JSON.stringify({
        type: 'action_approved',
        payload: { actionId }
    }));
}

function handleActionDeny(clientId, actionId) {
    console.log(`Action denied: ${actionId}`);
    wsFor(clientId).send(JSON.stringify({
        type: 'action_denied',
        payload: { actionId }
    }));
}

function wsFor(clientId) {
    const client = clients.get(clientId);
    if (!client) {
        throw new Error(`Client ${clientId} not found`);
    }
    return client;
}

// Create HTTP server
const server = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('Cline Agent Mobile Bridge Server');
});

// Create WebSocket server
const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, request) => {
    handleWebSocketConnection(ws, request);
});

// Start server
server.listen(PORT, () => {
    console.log(`Bridge server listening on port ${PORT}`);
    console.log(`GITHUB_TOKEN: ${activeToken ? 'Configured' : 'Not configured (set env var or authenticate from the app)'}`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
    console.log('SIGTERM received, shutting down');
    wss.close(() => {
        server.close(() => {
            process.exit(0);
        });
    });
});

process.on('SIGINT', () => {
    console.log('SIGINT received, shutting down');
    wss.close(() => {
        server.close(() => {
            process.exit(0);
        });
    });
});

module.exports = {
    server,
    wss,
    listMyCodespaces,
    getCodespace,
    createCodespace,
    startCodespace,
    stopCodespace,
    restartCodespace,
    deleteCodespace,
    broadcastState,
    serverState
};


/**
 * F1 Telemetry Replay - Main Application
 * Handles WebSocket connection, canvas rendering, and UI controls
 */

// ═══════════════════════════════════════════════════════════════════════════
// Configuration
// ═══════════════════════════════════════════════════════════════════════════

const CONFIG = {
    wsBaseUrl: 'ws://localhost:8080/ws/telemetry',
    apiBaseUrl: 'http://localhost:8080/api',
    reconnectInterval: 3000,
    canvasWidth: 800,
    canvasHeight: 600,
    dotRadius: 8,
    labelOffset: 12,
};

// ═══════════════════════════════════════════════════════════════════════════
// State
// ═══════════════════════════════════════════════════════════════════════════

const state = {
    ws: null,
    connected: false,
    sessionKey: '9140',
    status: 'IDLE',
    currentTime: null,
    startTime: null,
    endTime: null,
    speed: 1.0,
    
    // Track bounds (auto-calculated from data)
    bounds: {
        minX: Infinity,
        maxX: -Infinity,
        minY: Infinity,
        maxY: -Infinity,
        initialized: false,
    },
    
    // Current car positions and data
    cars: new Map(), // driverNumber -> { x, y, speed, ... }
};

// ═══════════════════════════════════════════════════════════════════════════
// DOM Elements
// ═══════════════════════════════════════════════════════════════════════════

const elements = {
    canvas: null,
    ctx: null,
    driverList: null,
    sessionSelect: null,
    speedSelect: null,
    seekSlider: null,
    seekTime: null,
    btnPlay: null,
    btnPause: null,
    btnStop: null,
    statusValue: null,
    sessionValue: null,
    timeValue: null,
    speedValue: null,
    carsValue: null,
    connIndicator: null,
    connStatus: null,
};

// ═══════════════════════════════════════════════════════════════════════════
// Initialization
// ═══════════════════════════════════════════════════════════════════════════

document.addEventListener('DOMContentLoaded', () => {
    initElements();
    initCanvas();
    initEventListeners();
    initDriverList();
    connect();
});

function initElements() {
    elements.canvas = document.getElementById('trackCanvas');
    elements.ctx = elements.canvas.getContext('2d');
    elements.driverList = document.getElementById('driverList');
    elements.sessionSelect = document.getElementById('sessionSelect');
    elements.speedSelect = document.getElementById('speedSelect');
    elements.seekSlider = document.getElementById('seekSlider');
    elements.seekTime = document.getElementById('seekTime');
    elements.btnPlay = document.getElementById('btnPlay');
    elements.btnPause = document.getElementById('btnPause');
    elements.btnStop = document.getElementById('btnStop');
    elements.statusValue = document.getElementById('statusValue');
    elements.sessionValue = document.getElementById('sessionValue');
    elements.timeValue = document.getElementById('timeValue');
    elements.speedValue = document.getElementById('speedValue');
    elements.carsValue = document.getElementById('carsValue');
    elements.connIndicator = document.getElementById('connIndicator');
    elements.connStatus = document.getElementById('connStatus');
}

function initCanvas() {
    const container = elements.canvas.parentElement;
    const rect = container.getBoundingClientRect();
    
    // Set canvas size to fit container while maintaining aspect ratio
    const aspectRatio = CONFIG.canvasWidth / CONFIG.canvasHeight;
    let width = rect.width - 20;
    let height = width / aspectRatio;
    
    if (height > rect.height - 20) {
        height = rect.height - 20;
        width = height * aspectRatio;
    }
    
    elements.canvas.width = width;
    elements.canvas.height = height;
    
    // Initial render
    renderTrack();
}

function initEventListeners() {
    // Transport controls
    elements.btnPlay.addEventListener('click', () => sendCommand('PLAY'));
    elements.btnPause.addEventListener('click', () => sendCommand('PAUSE'));
    elements.btnStop.addEventListener('click', () => sendCommand('STOP'));
    
    // Speed control
    elements.speedSelect.addEventListener('change', (e) => {
        const speed = parseFloat(e.target.value);
        sendCommand('SPEED', { speed });
    });
    
    // Session select
    elements.sessionSelect.addEventListener('change', (e) => {
        state.sessionKey = e.target.value;
        reconnect();
    });
    
    // Seek slider
    elements.seekSlider.addEventListener('input', (e) => {
        updateSeekTimeDisplay(e.target.value);
    });
    
    elements.seekSlider.addEventListener('change', (e) => {
        if (state.startTime && state.endTime) {
            const percent = e.target.value / 100;
            const start = new Date(state.startTime).getTime();
            const end = new Date(state.endTime).getTime();
            const targetTime = new Date(start + (end - start) * percent).toISOString();
            sendCommand('SEEK', { targetTime });
        }
    });
    
    // Window resize
    window.addEventListener('resize', () => {
        initCanvas();
        renderTrack();
    });
}

function initDriverList() {
    const driverNumbers = getAllDriverNumbers();
    elements.driverList.innerHTML = '';
    
    driverNumbers.forEach((num, index) => {
        const driver = getDriver(num);
        const row = document.createElement('div');
        row.className = 'driver-row';
        row.id = `driver-${num}`;
        row.style.borderLeftColor = driver.teamColor;
        row.innerHTML = `
            <span class="col-pos">${index + 1}</span>
            <span class="col-name" style="color: ${driver.teamColor}">${driver.lastName.toUpperCase()}</span>
            <span class="col-tire">--</span>
            <span class="col-speed">---</span>
        `;
        elements.driverList.appendChild(row);
    });
}

// ═══════════════════════════════════════════════════════════════════════════
// WebSocket Connection
// ═══════════════════════════════════════════════════════════════════════════

function connect() {
    if (state.ws && state.ws.readyState === WebSocket.OPEN) {
        return;
    }
    
    updateConnectionStatus('connecting');
    
    const wsUrl = `${CONFIG.wsBaseUrl}/${state.sessionKey}`;
    state.ws = new WebSocket(wsUrl);
    
    state.ws.onopen = () => {
        state.connected = true;
        updateConnectionStatus('connected');
        
        // Subscribe to telemetry
        sendCommand('SUBSCRIBE');
        sendCommand('GET_STATE');
    };
    
    state.ws.onmessage = (event) => {
        try {
            const message = JSON.parse(event.data);
            handleMessage(message);
        } catch (e) {
            console.error('Failed to parse message:', e);
        }
    };
    
    state.ws.onclose = () => {
        state.connected = false;
        updateConnectionStatus('disconnected');
        
        // Attempt reconnect
        setTimeout(connect, CONFIG.reconnectInterval);
    };
    
    state.ws.onerror = (error) => {
        console.error('WebSocket error:', error);
    };
}

function reconnect() {
    if (state.ws) {
        state.ws.close();
    }
    state.bounds.initialized = false;
    state.cars.clear();
    setTimeout(connect, 100);
}

function sendCommand(type, data = null) {
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN) {
        console.warn('WebSocket not connected');
        return;
    }
    
    const command = { type, data };
    state.ws.send(JSON.stringify(command));
}

// ═══════════════════════════════════════════════════════════════════════════
// Message Handling
// ═══════════════════════════════════════════════════════════════════════════

function handleMessage(message) {
    switch (message.type) {
        case 'REPLAY_STATE':
            handleReplayState(message.data);
            break;
        case 'TELEMETRY_BATCH':
            handleTelemetryBatch(message.data);
            break;
        case 'PLAYBACK_COMPLETE':
            handlePlaybackComplete();
            break;
        case 'SUBSCRIBED':
            console.log('Subscribed to telemetry');
            break;
        case 'ERROR':
            console.error('Server error:', message.data?.message);
            break;
        default:
            console.log('Unknown message type:', message.type);
    }
}

function handleReplayState(data) {
    if (!data) return;
    
    state.status = data.status || 'IDLE';
    state.currentTime = data.currentTime;
    state.startTime = data.startTime;
    state.endTime = data.endTime;
    state.speed = data.speed?.multiplier || 1.0;
    
    updateUI();
}

function handleTelemetryBatch(batch) {
    if (!batch) return;
    
    // Update bounds from location data
    if (batch.locations && batch.locations.length > 0) {
        updateBounds(batch.locations);
        
        // Update car positions
        batch.locations.forEach(loc => {
            const car = state.cars.get(loc.driverNumber) || {};
            car.x = loc.x;
            car.y = loc.y;
            state.cars.set(loc.driverNumber, car);
        });
    }
    
    // Update car data (speed, etc.)
    if (batch.carData && batch.carData.length > 0) {
        batch.carData.forEach(data => {
            const car = state.cars.get(data.driverNumber) || {};
            car.speed = data.speed;
            car.gear = data.gear;
            car.throttle = data.throttle;
            car.brake = data.brake;
            car.rpm = data.rpm;
            state.cars.set(data.driverNumber, car);
        });
    }
    
    // Update timestamp
    if (batch.batchTimestamp) {
        state.currentTime = batch.batchTimestamp;
    }
    
    // Render
    renderTrack();
    updateDriverList();
    updateUI();
}

function handlePlaybackComplete() {
    state.status = 'STOPPED';
    updateUI();
}

// ═══════════════════════════════════════════════════════════════════════════
// Canvas Rendering
// ═══════════════════════════════════════════════════════════════════════════

function updateBounds(locations) {
    let updated = false;
    
    locations.forEach(loc => {
        if (loc.x < state.bounds.minX) { state.bounds.minX = loc.x; updated = true; }
        if (loc.x > state.bounds.maxX) { state.bounds.maxX = loc.x; updated = true; }
        if (loc.y < state.bounds.minY) { state.bounds.minY = loc.y; updated = true; }
        if (loc.y > state.bounds.maxY) { state.bounds.maxY = loc.y; updated = true; }
    });
    
    if (updated) {
        state.bounds.initialized = true;
    }
}

function normalizeX(x) {
    if (!state.bounds.initialized) return elements.canvas.width / 2;
    const padding = 40;
    const range = state.bounds.maxX - state.bounds.minX;
    if (range === 0) return elements.canvas.width / 2;
    return ((x - state.bounds.minX) / range) * (elements.canvas.width - padding * 2) + padding;
}

function normalizeY(y) {
    if (!state.bounds.initialized) return elements.canvas.height / 2;
    const padding = 40;
    const range = state.bounds.maxY - state.bounds.minY;
    if (range === 0) return elements.canvas.height / 2;
    // Invert Y axis (canvas Y increases downward)
    return elements.canvas.height - (((y - state.bounds.minY) / range) * (elements.canvas.height - padding * 2) + padding);
}

function renderTrack() {
    const ctx = elements.ctx;
    const canvas = elements.canvas;
    
    // Clear canvas with dark background
    ctx.fillStyle = '#0a0a0a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Draw track outline (white line connecting car positions)
    if (state.bounds.initialized && state.cars.size > 0) {
        drawTrackOutline(ctx);
    }
    
    // Draw cars
    state.cars.forEach((car, driverNumber) => {
        if (car.x !== undefined && car.y !== undefined) {
            drawCar(ctx, driverNumber, car);
        }
    });
    
    // Draw "no data" message if no cars
    if (state.cars.size === 0) {
        ctx.fillStyle = '#00ff00';
        ctx.font = '14px "Courier New", monospace';
        ctx.textAlign = 'center';
        ctx.fillText('AWAITING TELEMETRY DATA...', canvas.width / 2, canvas.height / 2);
    }
}

function drawTrackOutline(ctx) {
    // Collect all positions and draw a faint white track
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.lineWidth = 20;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    
    // This is a simplified visualization - in a real app you'd use actual track data
    // For now, we just show the car positions
}

function drawCar(ctx, driverNumber, car) {
    const driver = getDriver(driverNumber);
    const x = normalizeX(car.x);
    const y = normalizeY(car.y);
    
    // Draw car dot with team color
    ctx.beginPath();
    ctx.arc(x, y, CONFIG.dotRadius, 0, Math.PI * 2);
    ctx.fillStyle = driver.teamColor;
    ctx.fill();
    
    // Draw white border
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 2;
    ctx.stroke();
    
    // Draw driver abbreviation
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 10px "Courier New", monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'bottom';
    ctx.fillText(driver.abbr, x, y - CONFIG.labelOffset);
}

// ═══════════════════════════════════════════════════════════════════════════
// UI Updates
// ═══════════════════════════════════════════════════════════════════════════

function updateUI() {
    // Status
    elements.statusValue.textContent = state.status;
    elements.statusValue.className = `info-value status-${state.status.toLowerCase()}`;
    
    // Session
    elements.sessionValue.textContent = state.sessionKey;
    
    // Time
    if (state.currentTime) {
        const time = new Date(state.currentTime);
        elements.timeValue.textContent = time.toISOString().substr(11, 8);
    }
    
    // Speed
    elements.speedValue.textContent = `${state.speed}x`;
    
    // Cars count
    elements.carsValue.textContent = state.cars.size;
    
    // Seek slider
    if (state.startTime && state.endTime && state.currentTime) {
        const start = new Date(state.startTime).getTime();
        const end = new Date(state.endTime).getTime();
        const current = new Date(state.currentTime).getTime();
        const percent = ((current - start) / (end - start)) * 100;
        elements.seekSlider.value = Math.min(100, Math.max(0, percent));
        updateSeekTimeDisplay(elements.seekSlider.value);
    }
    
    // Button states
    const isPlaying = state.status === 'PLAYING';
    const isPaused = state.status === 'PAUSED';
    elements.btnPlay.disabled = isPlaying;
    elements.btnPause.disabled = !isPlaying;
}

function updateSeekTimeDisplay(percent) {
    if (state.startTime && state.endTime) {
        const start = new Date(state.startTime).getTime();
        const end = new Date(state.endTime).getTime();
        const targetTime = new Date(start + (end - start) * (percent / 100));
        elements.seekTime.textContent = targetTime.toISOString().substr(11, 8);
    }
}

function updateDriverList() {
    // Sort drivers by some criteria (for now, just by driver number)
    const sortedDrivers = Array.from(state.cars.entries())
        .sort((a, b) => {
            // Sort by speed descending
            const speedA = a[1].speed || 0;
            const speedB = b[1].speed || 0;
            return speedB - speedA;
        });
    
    // Update positions
    sortedDrivers.forEach(([driverNumber, car], index) => {
        const row = document.getElementById(`driver-${driverNumber}`);
        if (row) {
            const posEl = row.querySelector('.col-pos');
            const speedEl = row.querySelector('.col-speed');
            
            if (posEl) posEl.textContent = index + 1;
            if (speedEl) speedEl.textContent = car.speed ? car.speed.toString().padStart(3) : '---';
        }
    });
}

function updateConnectionStatus(status) {
    const indicator = elements.connIndicator;
    const statusText = elements.connStatus;
    
    indicator.className = 'conn-indicator ' + status;
    
    switch (status) {
        case 'connected':
            statusText.textContent = 'CONNECTED';
            break;
        case 'connecting':
            statusText.textContent = 'CONNECTING...';
            break;
        case 'disconnected':
            statusText.textContent = 'DISCONNECTED';
            break;
    }
}

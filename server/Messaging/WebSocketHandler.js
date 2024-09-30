const WebSocket = require('ws');
const axios = require('axios');
const { parse } = require('querystring');

const wss = new WebSocket.server({port: 8020 });

function getParamsFromSession(session) {
    const { query } = session.upgradeReq;
    const params = parse(query);
    return { roomId: params.roomId, userId: params.userId };
}


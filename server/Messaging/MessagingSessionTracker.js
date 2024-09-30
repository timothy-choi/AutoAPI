const redis = require('redis');
const WebSocket = require('ws');

const client = redis.createClient();

const INACTIVITY_THRESHOLD = 10 * 60 * 1000; 

async function addSession(user, session) {
    await client.hset('activeSessions', user, JSON.stringify(session));
    await client.hset('sessionToUser', session.Id, user);
}

async function removeSession(user, sessionId) {
    await client.hdel('activeSessions', user);
    await client.hdel('sessionToUser', user);
}

async function getSessionInfo(userId) {
    const sessionInfo = await client.hget('activeSessions', userId);
    return sessionInfo ? JSON.parse(sessionInfo) : null;
}

async function getActiveSessions() {
    return await client.hgetall('activeSessions');
}




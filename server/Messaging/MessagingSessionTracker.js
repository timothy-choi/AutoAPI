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
    await client.hdel('sessionToUser', sessionId);
}

async function getSessionInfo(userId) {
    const sessionInfo = await client.hget('activeSessions', userId);
    return sessionInfo ? JSON.parse(sessionInfo) : null;
}

async function getActiveSessions() {
    return await client.hgetall('activeSessions');
}

async function getUserForSession(sessionId) {
    return await client.hget('sessionToUser', sessionId);
}

async function updateLastActiveTime(userId, lastActiveTime) {
    const sessionInfo = await getSessionInfo(userId);
    if (sessionInfo) {
        sessionInfo.LastActiveAt = lastActiveTime;
        await client.hset('activeSessions', userId, JSON.stringify(sessionInfo));
    }
}

async function getActiveUsers() {
    const activeSessions = await getActiveSessions();

    return Object.entries(activeSessions).filter(([_, sessionInfo]) => Date.now() - JSON.parse(sessionInfo).lastActivity < INACTIVITY_THRESHOLD).map(([userId, _]) => userId);
}

async function getInactiveUsers() {
    const activeSessions = await getActiveSessions();

    return Object.entries(activeSessions).filter(([_, sessionInfo]) => Date.now() - JSON.parse(sessionInfo).lastActivity >= INACTIVITY_THRESHOLD).map(([userId, _]) => userId);
}




const redis = require('redis');

const client = redis.createClient();

const INACTIVITY_THRESHOLD = 10 * 60 * 1000; 

client.on('error', (err) => {
    console.error('Redis error:', err);
});

async function addSession(user, session) {
    const sessionInfo = {
        id: session.Id,
        session: session.wsSession,
        lastActivity: session.lastActiveTime,
        roomId: session.roomId,
        username: user
    };

    await client.hset('activeSessions:' + session.roomId, user, JSON.stringify(sessionInfo));
    await client.hset('sessionToUser', session.Id, user);
}

async function removeSession(user, roomId, sessionId) {
    await client.hdel('activeSessions:' + roomId, user);
    await client.hdel('sessionToUser', sessionId);
}

async function getSessionInfo(roomId, userId) {
    const sessionInfo = await client.hget('activeSessions:' + roomId, userId);
    return sessionInfo ? JSON.parse(sessionInfo) : null;
}

async function getActiveSessions(roomId) {
    return await client.hgetall('activeSessions:' + roomId);
}

async function getUserForSession(sessionId) {
    return await client.hget('sessionToUser', sessionId);
}

async function updateLastActiveTime(roomId, userId, lastActiveTime) {
    const sessionInfo = await getSessionInfo(roomId, userId);
    if (sessionInfo) {
        sessionInfo.lastActivity = lastActiveTime;
        await client.hset('activeSessions:' + roomId, userId, JSON.stringify(sessionInfo));
    }
}

async function getActiveUsers(roomId) {
    const activeSessions = await getActiveSessions(roomId);

    return Object.entries(activeSessions).filter(([_, sessionInfo]) => Date.now() - JSON.parse(sessionInfo).lastActivity < INACTIVITY_THRESHOLD).map(([userId, _]) => userId);
}

async function getInactiveUsers(roomId) {
    const activeSessions = await getActiveSessions(roomId);

    return Object.entries(activeSessions).filter(([_, sessionInfo]) => Date.now() - JSON.parse(sessionInfo).lastActivity >= INACTIVITY_THRESHOLD).map(([userId, _]) => userId);
}

process.on('SIGINT', () => {
    console.log('Shutting down Redis client...');
    client.quit();
    process.exit();
});

module.exports = {
    addSession,
    removeSession,
    getSessionInfo,
    getActiveSessions,
    getUserForSession,
    updateLastActiveTime,
    getActiveUsers,
    getInactiveUsers
};



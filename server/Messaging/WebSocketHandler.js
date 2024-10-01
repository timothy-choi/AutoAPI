const WebSocket = require('ws');
const axios = require('axios');
const { parse } = require('querystring');

const { addSession, removeSession, getSessionInfo, getActiveSessions, getUserForSession, updateLastActiveTime, getActiveUsers, getInactiveUsers } = require('./MessagingSessionTracker');

const wss = new WebSocket.server({port: 8020 });

function getParamsFromSession(session) {
    const { query } = session.upgradeReq;
    const params = parse(query);
    return { roomId: params.roomId, userId: params.userId };
}

async function broadcastToRoom(roomId, message, sender) {
    const activeSessions = await getActiveSessions(roomId);

    for (const [userId, sessionInfo] of Object.entries(activeSessions)) {
        const sessionData = JSON.parse(sessionInfo);
        const wsSession = sessionData.session;

        if (wsSession && wsSession.readyState === WebSocket.OPEN) {
            wsSession.send(message);
        }
    }
}

wss.on('connection', async (ws, req) => {
    const { chatroomId, userId } = getParamsFromSession(req);

    if (chatroomId && userId) {
        var user = await axios.get('/User/' + userId);
        var chatroom = await axios.get('/Messaging/chatroomId/' + chatroomId);

        const messagingSession = await axios.get('/MessagingSession/userId/' + userId);

        var sessionInfo = {
            sessionId: messagingSession.Id,
            session: ws,
            lastActivity: Date.now(),
            roomId: chatroomId,
            user: messagingSession.Username
        };

        await addSession(user.Username, sessionInfo);

        if (!messagingSession.HasJoined) {
            await axios.put('/MessagingSession/hasJoined/' + messagingSession.Id);

            const joinedText = {
                type: 'notification',
                message: sessionInfo.user + ' has entered the chat',
                messageDate: Date.now()
            };

            await broadcastToRoom(chatroomId, joinedText, ws);
        } 

        await axios.put('/MessagingSession/joinedAt/' + messagingSession.Id);

        await axios.put('/MessagingSession/sessionStatus/' + messagingSession.Id + "/ACTIVE");

        ws.on('message', async (msg) => {
            const messageInfo = {
                type: 'message',
                message: msg,
                user: messagingSession.Username,
                messageDate: Date.now()
            };

            await broadcastToRoom(chatroomId, messageInfo, ws);

            var lastTime = messageInfo.messageDate;

            await updateLastActiveTime(chatroomId, userId, lastTime);
            
            await axios.put('/MessagingSession/lastActiveAt/' + messagingSession.Id);
        });


    } else {
        ws.close(1003, 'Bad Data');
    }
})
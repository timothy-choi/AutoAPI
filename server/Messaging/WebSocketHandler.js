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
                message: sessionInfo.user + ' has entered the chat'
            };
        } 

        await axios.put('/MessagingSession/joinedAt/' + messagingSession.Id);

        await axios.put('/MessagingSession/sessionStatus/' + messagingSession.Id + "/ACTIVE");


    } else {
        ws.close(1003, 'Bad Data');
    }
})
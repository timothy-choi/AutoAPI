const WebSocket = require('ws');
const axios = require('axios');
const { parse } = require('querystring');
const kafka = require('kafka-node');
const { KafkaClient, Producer } = kafka;

const { addSession, removeSession, getSessionInfo, getActiveSessions, getUserForSession, updateLastActiveTime, getActiveUsers, getInactiveUsers } = require('./MessagingSessionTracker');

const wss = new WebSocket.server({port: 8020 });

const kafkaClient = new KafkaClient({ kafkaHost: 'localhost:9092' });
const producer = new Producer(kafkaClient);

producer.on('ready', () => {});

producer.on('error', (err) => {
    console.error('Kafka Producer error:', err);
});

function getParamsFromSession(session) {
    const { query } = session.upgradeReq;
    const params = parse(query);
    return { roomId: params.roomId, userId: params.userId };
}

async function broadcastToRoom(roomId, message) {
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

            await broadcastToRoom(chatroomId, joinedText);
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

            await broadcastToRoom(chatroomId, messageInfo);

            var lastTime = messageInfo.messageDate;

            await updateLastActiveTime(chatroomId, userId, lastTime);
            
            await axios.put('/MessagingSession/lastActiveAt/' + messagingSession.Id);

            await axios.put('/MessagingSession/sessionStatus/' + messagingSession.Id + "/ACTIVE");

            var messageId = null;
            
            await axios.post('/Message/', {
                SenderId: userId,
                SenderUsername: messagingSession.Username,
                ChatroomId: chatroomId,
                MessageText: msg
            }).then((response) => {
                messageId = response.data.Id;
            }).catch((err) => {});

            await axios.put('/Messaging/message/add/' + chatroom.Id + "/" + messageId);
        });

        ws.on('closed', async () => {
            await removeSession(messagingSession.Username, chatroomId, sessionInfo.sessionId);

            await axios.put('/MessagingSession/closedChatAt/' + messagingSession.Id);

            await axios.put('/MessagingSession/sessionStatus/' + messagingSession.Id + "/CLOSED");
        });
    } else {
        ws.close(1003, 'Bad Data');
    }
});


const sentUserStatusUpdates = (roomId) => {
    setInterval(async () => {
        const activeUsers = await getActiveUsers(roomId);
        const inactiveUsers = await getInactiveUsers(roomId);
        const allCurrentUsers = activeUsers.concat(inactiveUsers);
    
        const userStatus = {
            activeUsers,
            inactiveUsers
        };
    
        const message = {
            topic: 'userStatus_' + roomId,
            messages: JSON.stringify(userStatus),
            partition: 0
        };
    
        producer.send(message, (err, data) => {
            if (err) console.error('Error publishing to Kafka:', err);
        });

        for (let i = 0; i < allCurrentUsers.length; ++i) {
            if (inactiveUsers.includes(allCurrentUsers[i])) {
                const messagingSession = await axios.get('/MessagingSession/userId/' + allCurrentUsers[i]);

                await axios.put('/MessagingSession/sessionStatus/' + messagingSession.Id + "/INACTIVE");
            }
        }
    }, 60000);
}
const MessagingSession = require('./MessagingSession');

exports.GetMessagingSessionById = async (messagingSessionId) => {
    var messagingSession = await MessagingSession.findByPk(messagingSessionId);

    return messagingSession;
}

exports.GetMessagingSessionByUserId = async (userId) => {
    var messagingSession = await MessagingSession.findOne({where: { UserId: userId }});

    return messagingSession;
}

exports.CreateMessagingSession = async (messagingSessionBody) => {
    try {
        var messagingSession = await this.GetMessagingSessionByUserId(messagingSessionBody.userId);

        if (messagingSession) {
            throw new Error('session already exists');
        }

        messagingSession = await MessagingSession.create({ChatroomId: messagingSessionBody.chatroomId, UserId: messagingSessionBody.userId, Username: messagingSessionBody.username, JoinedAt: Date.now(), SessionStatus: "Active"});

        return messagingSession;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteMessagingSession = async (messagingSessionId) => {
    try {
        var messagingSession = await GetMessagingSessionById(messagingSessionId);

        if (!messagingSession) {
            throw new Error('session does not exist');
        }

        await messagingSession.destory();
    } catch (error) {
        throw new Error(error.message);
    }
}


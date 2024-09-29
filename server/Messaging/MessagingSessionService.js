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

exports.EditUsername = async (messagingSessionId, user) => {
    try {
        var messagingSession = await this.GetMessagingSessionById(messagingSessionId);

        if (!messagingSession) {
            throw new Error('session does not exist');
        }

        messagingSession.Username = user;

        await messagingSession.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.SetLastMessageReadId = async (messagingSessionId, messageId) => {
    try {
        var messagingSession = await this.GetMessagingSessionById(messagingSessionId);

        if (!messagingSession) {
            throw new Error('session does not exist');
        }

        messagingSession.LastReadMessageId = messageId;

        await messagingSession.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.SetLastActiveAt = async (messagingSessionId) => {
    try {
        var messagingSession = await this.GetMessagingSessionById(messagingSessionId);

        if (!messagingSession) {
            throw new Error('session does not exist');
        }

        messagingSession.LastActiveAt = Date.now();

        await messagingSession.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.SetJoinedAt = async (messagingSessionId) => {
    try {
        var messagingSession = await this.GetMessagingSessionById(messagingSessionId);

        if (!messagingSession) {
            throw new Error('session does not exist');
        }

        messagingSession.JoinedAt = Date.now();

        await messagingSession.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.SetClosedChatAt = async (messagingSessionId) => {
    try {
        var messagingSession = await this.GetMessagingSessionById(messagingSessionId);

        if (!messagingSession) {
            throw new Error('session does not exist');
        }

        messagingSession.ClosedChatAt = Date.now();

        await messagingSession.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.SetSessionStatus = async (messagingSessionId, sessionStatus) => {
    try {
        var messagingSession = await this.GetMessagingSessionById(messagingSessionId);

        if (!messagingSession) {
            throw new Error('session does not exist');
        }

        messagingSession.SessionStatus = sessionStatus;

        await messagingSession.save();
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


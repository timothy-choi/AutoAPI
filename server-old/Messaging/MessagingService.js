const Messaging = require('./Messaging');

exports.GetMessagingById = async (messagingId) => {
    var message = await Messaging.findByPk(messagingId);

    return message;
}

exports.GetMessagingByChatroomId = async (chatroomId) => {
    var message = await Messaging.findOne({ where: { ChatroomId: chatroomId } });

    return message;
}

exports.CreateMessaging = async (messagingBody) => {
    try {
        var message = await this.GetMessagingByChatroomId(messagingBody.chatroomId);

        if (message) {
            throw new Error('chatroom already exists');
        }

        message = await Messaging.create({ChatroomId: messagingBody.chatroomId, CurrentUsers: messagingBody.currentUsers, MessageThreadCreated: Date.now()});

        return message;
    } catch (error) {
        throw new Error(error.message);
    }
};

exports.AddUser = async (messagingId, user) => {
    try {
        var messaging = await this.GetMessagingById(messagingId);

        if (!messaging) {
            throw new Error('chatroom does not exist');
        }

        messaging.CurrentUsers.push(user);

        await messaging.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteUser = async (messagingId, user) => {
    try {
        var messaging = await this.GetMessagingById(messagingId);

        if (!messaging) {
            throw new Error('chatroom does not exist');
        }

        messaging.CurrentUsers.remove(user);

        await messaging.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.AddSession = async (messagingId, sessionId) => {
    try {
        var messaging = await this.GetMessagingById(messagingId);

        if (!messaging) {
            throw new Error('chatroom does not exist');
        }

        messaging.AllSessions.push(sessionId);

        await messaging.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteSession = async (messagingId, sessionId) => {
    try {
        var messaging = await this.GetMessagingById(messagingId);

        if (!messaging) {
            throw new Error('chatroom does not exist');
        }

        messaging.AllSessions.remove(sessionId);

        await messaging.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.AddMessage = async (messagingId, messageId) => {
    try {
        var messaging = await this.GetMessagingById(messagingId);

        if (!messaging) {
            throw new Error('chatroom does not exist');
        }

        messaging.Messages.push(messageId);

        await messaging.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteMessage = async (messagingId, messageId) => {
    try {
        var messaging = await this.GetMessagingById(messagingId);

        if (!messaging) {
            throw new Error('chatroom does not exist');
        }

        messaging.Messages.remove(messageId);

        await messaging.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteMessaging = async (messagingId) => {
    try {
        var messaging = await this.GetMessagingById(messagingId);

        if (!messaging) {
            throw new Error('chatroom already exists');
        }

        await messaging.destroy();
    } catch (error) {
        throw new Error(error.message);
    }
}
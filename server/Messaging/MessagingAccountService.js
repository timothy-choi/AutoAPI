const MessagingAccount = require('./MessagingAccount');

exports.getMessagingAccountById = async (messagingAccountId) => {
    var messagingAccount = await MessagingAccount.findByPk(messagingAccountId);

    return messagingAccount;
}

exports.getMessagingAccountByUserId = async (userId) => {
    var messagingAccount = await MessagingAccount.findOne({ where: {UserId: userId}});

    return messagingAccount;
}

exports.CreateMessagingAccount = async (userId, username) => {
    try {
        var messagingAcct = await getMessagingAccountByUserId(userId);

        if (messagingAcct) {
            throw new Error('account already exists');
        }

        var messagingAccount = await MessagingAccount.create({UserId: userId, Username: username});

        return messagingAccount;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.AddChatroom = async (messagingAccountId, chatroomInfo) => {
    try {
        var messagingAcct = await getMessagingAccountById(messagingAccountId);

        if (messagingAcct) {
            throw new Error('account already exists');
        }

        messagingAcct.AllChatroomsJoined.push(chatroomInfo);

        await messagingAcct.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.RemoveChatroom = async (messagingAccountId, chatroomId) => {
    try {
        var messagingAcct = await getMessagingAccountById(messagingAccountId);

        if (messagingAcct) {
            throw new Error('account already exists');
        }

        messagingAcct.AllChatroomsJoined.filter(obj => obj.RoomId !== chatroomId);

        await messagingAcct.save();
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteMessagingAccount = async (messagingAccountId) => {
    try {
        var messagingAcct = await getMessagingAccountById(messagingAccountId);

        if (messagingAcct) {
            throw new Error('account already exists');
        }

        await messagingAcct.destroy();
    } catch (error) {
        throw new Error(error.message);
    }
}




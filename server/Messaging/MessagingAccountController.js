const MessagingAccountService = require('./MessagingAccountService');

exports.GetMessagingAccountById = async (req, res) => {
    try {
        var messagingAccount = await MessagingAccountService.getMessagingAccountById(req.messagingAccountId);

        return res.status(200).json(messagingAccount);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.GetMessagingAccountByUserId = async (req, res) => {
    try {
        var messagingAccount = await MessagingAccountService.getMessagingAccountByUserId(req.userId);

        return res.status(200).json(messagingAccount);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.CreateMessagingAccount = async (req, res) => {
    try {
        var messagingAccount = await MessagingAccountService.CreateMessagingAccount(req.body.userId, req.body.username);

        return res.status(201).json(messagingAccount);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.AddChatroom = async (req, res) => {
    try {
        await MessagingAccountService.AddChatroom(req.messagingAccountId, req.body);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.RemoveChatroom = async (req, res) => {
    try {
        await MessagingAccountService.RemoveChatroom(req.messagingAccountId, req.chatroomId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

exports.DeleteMessagingAccount = async (req, res) => {
    try {
        await MessagingAccountService.DeleteMessagingAccount(req.messagingAccountId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({error: error.message});
    }
}

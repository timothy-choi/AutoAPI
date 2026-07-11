const MessagingService = require('./MessagingService');

exports.GetMessagingById = async (req, res) => {
    try {
        var messaging = await MessagingService.GetMessagingById(req.messagingId);

        return res.status(200).json(messaging);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetMessagingByChatroomId = async (req, res) => {
    try {
        var messaging = await MessagingService.GetMessagingByChatroomId(req.chatroomId);

        return res.status(200).json(messaging);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.CreateMessaging = async (req, res) => {
    try {
        var messaging = await MessagingService.CreateMessaging(req.body);

        return res.status(201).json(messaging);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddUser = async (req, res) => {
    try {
        await MessagingService.AddUser(req.messagingId, req.user);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveUser = async (req, res) => {
    try {
        await MessagingService.DeleteUser(req.messagingId, req.user);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddSession = async (req, res) => {
    try {
        await MessagingService.AddSession(req.messagingId, req.sessionId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveSession = async (req, res) => {
    try {
        await MessagingService.DeleteSession(req.messagingId, req.sessionId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.AddMessage = async (req, res) => {
    try {
        await MessagingService.AddMessage(req.messagingId, req.messageId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.RemoveMessage = async (req, res) => {
    try {
        await MessagingService.DeleteMessage(req.messagingId, req.messageId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteMessaging = async (req, res) => {
    try {
        await MessagingService.DeleteMessaging(req.messagingId);

        return res.status(201).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}
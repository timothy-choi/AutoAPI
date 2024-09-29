const MessagingSessionService = require('./MessagingSessionService');

exports.GetMessagingSessionById = async (req, res) => {
    try {
        var messagingSession = await MessagingSessionService.GetMessagingSessionById(req.messagingSessionId);

        return res.status(200).json(messagingSession);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.GetMessagingSessionByUserId = async (req, res) => {
    try {
        var messagingSession = await MessagingSessionService.GetMessagingSessionByUserId(req.userId);

        return res.status(200).json(messagingSession);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.CreateMessagingSession = async (req, res) => {
    try {
        var messagingSession = await MessagingSessionService.CreateMessagingSession(req.body);

        return res.status(201).json(messagingSession);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetUsername = async (req, res) => {
    try {
        await MessagingSessionService.EditUsername(req.messagingSessionId, req.user);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetLastMessageReadId = async (req, res) => {
    try {
        await MessagingSessionService.SetLastMessageReadId(req.messagingSessionId, req.messageId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetLastActiveAt = async (req, res) => {
    try {
        await MessagingSessionService.SetLastActiveAt(req.messagingSessionId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetJoinedAt = async (req, res) => {
    try {
        await MessagingSessionService.SetJoinedAt(req.messagingSessionId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetClosedChatAt = async (req, res) => {
    try {
        await MessagingSessionService.SetClosedChatAt(req.messagingSessionId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.SetSessionStatus = async (req, res) => {
    try {
        await MessagingSessionService.SetSessionStatus(req.messagingSessionId, req.sessionStatus);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteMessagingSession = async (req, res) => {
    try {
        await MessagingSessionService.DeleteMessagingSession(req.messagingSessionId);

        return res.status(200).json(null);

    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}


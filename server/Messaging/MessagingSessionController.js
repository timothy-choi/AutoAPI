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

exports.DeleteMessagingSession = async (req, res) => {
    try {
        await MessagingSessionService.DeleteMessagingSession(req.messagingSessionId);

        return res.status(200).json(null);

    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}


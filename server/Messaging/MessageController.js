const MessageService = require('./MessageService');

exports.GetMessageById = async (req, res) => {
    try {
        var message = await MessageService.GetMessageById(req.messageId);

        return res.status(200).body(message);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.CreateMessage = async (req, res) => {
    try {
        var message = await MessageService.CreateMessage(req.body);

        return res.status(201).body(message);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.EditMessage = async (req, res) => {
    try {
        await MessageService.EditMessage(req.messageId, req.body.messageText);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}

exports.DeleteMessage = async (req, res) => {
    try {
        await MessageService.DeleteMessage(req.messageId);

        return res.status(200).body(null);
    } catch (error) {
        return res.status(500).json({ error: error.message });
    }
}


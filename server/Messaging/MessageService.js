const Message = require('./Message');

exports.GetMessageById = async (messageId) => {
    var message = await Message.findByPk(messageId);

    return message;
}

exports.CreateMessage = async (messageBody) => {
    try {
        var message = await Message.create({SenderId: messageBody.userId, SenderUsername: messageBody.username, ChatroomId: messageBody.chatroomId, MessageText: messageBody.messageText, MessageCreated: Date.now()});

        return message;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteMessage = async (messageId) => {
    try {
        var message = await GetMessageById(messageId);

        if (!message) {
            throw new Error('message does not exist');
        }
        
        await message.destroy();
    } catch (error) {
        throw new Error(error.message);
    }
}

const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const MessageSession = sequelize.define('MessageSessions', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ChatroomId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    UserId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    Username: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    LastReadMessageId: {
        type: DataTypes.UUID,
        allowNull: true,
    },
    LastActiveAt: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: Date.now,
    },
    JoinedAt: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: Date.now,
    },
    ClosedChatAt: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: Date.now,
    },
    SessionStatus: {
        type: DataTypes.STRING,
        allowNull: false,
    }
});

module.exports = MessageSession;
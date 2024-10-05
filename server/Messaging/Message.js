const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Message = sequelize.define('Message', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    SenderId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    SenderUsername: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    ChatroomId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    MessageText: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    MessageCreated: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: Date.now,
    },
    MessageUpdated: {
        type: DataTypes.DATE,
        allowNull: true,
        defaultValue: Date.now,
    },
    MessageType: {
        type: DataTypes.STRING,
        allowNull: false,
    }
});

modules.export = Message;
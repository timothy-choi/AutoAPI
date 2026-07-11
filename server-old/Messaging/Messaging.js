const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Messaging = sequelize.define('Messaging', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    ChatroomId: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    CurrentUsers: {
        type: DataTypes.ARRAY(DataTypes.STRING),
        allowNull: false,
        defaultValue: [],
    },
    MessageThreadCreated: {
        type: DataTypes.DATE,
        allowNull: false,
    },
    AllSessions: {
        type: DataTypes.ARRAY(DataTypes.UUID),
        allowNull: true,
        defaultValue: []
    },
    Messages: {
        type: DataTypes.ARRAY(DataTypes.UUID),
        allowNull: true,
        defaultValue: []
    }
});

module.exports = Messaging;
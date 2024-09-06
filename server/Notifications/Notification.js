const { DataTypes } = require('sequelize');
const sequelize = require('../config/database');

const Notification = sequelize.define('Notification', {
    Id: {
        type: DataTypes.UUID,
        autoIncrement: true,
        primaryKey: true,
    },
    UserRecipient: {
        type: DataTypes.UUID,
        allowNull: false,
    },
    NotificationType: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    NotificationSent: {
        type: DataTypes.DATE,
        allowNull: false,
        defaultValue: Date.now,
    },
    NotificationEmailId: {
        type: DataTypes.UUID,
        allowNull: true,
    },
    NotificationTopic: {
        type: DataTypes.STRING,
        allowNull: false,
    },
    NotificationMessage: {
        type: DataTypes.STRING,
        allowNull: false,
    }
}, );

module.exports = Notification;
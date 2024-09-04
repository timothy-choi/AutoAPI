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
});

module.exports = Notification;